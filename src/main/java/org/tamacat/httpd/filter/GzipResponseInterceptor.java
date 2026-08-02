/*
 * Copyright (c) 2009, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.filter;

import java.io.IOException;
import java.util.Iterator;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElement;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpEntityContainer;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.io.entity.HttpEntityWrapper;
import org.apache.hc.core5.http.message.MessageSupport;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.tamacat.httpd.util.HeaderUtils;
import org.tamacat.httpd.util.RequestUtils;
import org.tamacat.log.Log;
import org.tamacat.log.LogFactory;
import org.tamacat.util.ExceptionUtils;
import org.tamacat.util.StringUtils;

/**
 * <p>Server-side interceptor to handle Gzip-encoded responses.<br>
 * The cord of the basis is Apache HttpComponents {@code ResponseGzipCompress.java}.</p>
 *
 * <pre>Example:{@code components.xml}
 * {@code <bean id="gzip" class="org.tamacat.httpd.filter.GzipResponseInterceptor">
 *  <property name="contentType">
 *    <value>html,xml,css,javascript</value>
 *  </property>
 * </bean>
 * }</pre>
 *
 * {@link http://svn.apache.org/repos/asf/httpcomponents/httpcore/trunk/contrib/src/main/java/org/apache/http/contrib/compress/ResponseGzipCompress.java}
 */
public class GzipResponseInterceptor implements HttpResponseInterceptor {

	static final Log LOG = LogFactory.getLog(GzipResponseInterceptor.class);
	protected static final String ACCEPT_ENCODING = "Accept-Encoding";
	protected static final String GZIP_CODEC = "gzip";

	protected Set<String> contentTypes = new HashSet<String>();
	protected boolean useAll = true;

	/**
	 * <p>core5 passes the entity metadata as a separate {@link EntityDetails} argument and
	 * {@code HttpResponse} itself has no entity accessor, so the entity is reached by
	 * downcasting the response to {@link HttpEntityContainer} (R-5.1, Q6). In the classic
	 * blocking pipeline the instance handed to a response interceptor is always a
	 * {@code ClassicHttpResponse}, which extends {@code HttpEntityContainer}.
	 *
	 * <p>When the downcast is not possible (a non-classic {@code HttpProcessor}) the
	 * response is passed through uncompressed and the condition is logged at WARN
	 * (SEC-4.1). The {@code ClassCastException} is not swallowed silently, and the
	 * response is still complete and correct - only unoptimized.
	 */
	@Override
	public void process(HttpResponse response, EntityDetails entityDetails, HttpContext context)
			throws HttpException, IOException {
		if (context == null) {
			throw new IllegalArgumentException("HTTP context may not be null");
		}
		HttpRequest request = RequestUtils.getHttpRequest(context);
		Header aeheader = request != null ? request.getFirstHeader(ACCEPT_ENCODING) : null;
		if (request != null && RequestUtils.getVersion(request).greaterEquals(HttpVersion.HTTP_1_1)
				&& aeheader != null && useCompress(response.getFirstHeader(HttpHeaders.CONTENT_TYPE))) {
			String ua = HeaderUtils.getHeader(request, "User-Agent");
			if (ua != null && ua.indexOf("MSIE 6.0") >= 0) {
				return; //Skipped for IE6 bug(KB823386)
			}
			//core5 dropped Header#getElements(); MessageSupport#iterate parses the
			//comma separated element list of a header instead.
			Iterator<HeaderElement> codecs = MessageSupport.iterate(request, ACCEPT_ENCODING);
			while (codecs.hasNext()) {
				if (codecs.next().getName().equalsIgnoreCase(GZIP_CODEC)) {
					if (!(response instanceof HttpEntityContainer)) {
						//SEC-4.1: do not swallow. Pass through without wrapping, and log it.
						LOG.warn("Gzip compression skipped: the response is not an HttpEntityContainer"
							+ " (" + response.getClass().getName() + ")."
							+ " GzipResponseInterceptor requires the classic (blocking) HttpProcessor.");
						return;
					}
					HttpEntityContainer container = (HttpEntityContainer) response;
					HttpEntity original = container.getEntity();
					if (original == null) {
						return; //nothing to compress
					}
					GzipCompressingEntity entity = new GzipCompressingEntity(original);
					container.setEntity(entity);
					//core5's EntityDetails#getContentEncoding() returns a String, not a Header.
					response.setHeader(HttpHeaders.CONTENT_ENCODING, entity.getContentEncoding()); //Content-Encoding:gzip
					response.setHeader(HttpHeaders.TRANSFER_ENCODING, HeaderElements.CHUNKED_ENCODING); //Transfer-Encoding:chunked
					response.removeHeaders(HttpHeaders.CONTENT_LENGTH);
					return;
				}
			}
		}
	}

	/**
	 * <p>Set the content type of the gzip compression.<br>
	 * default are all content types to compressed.</p>
	 * <p>The {@code contentType} value is case insensitive,<br>
	 * and the white space of before and after is trimmed.</p>
	 *
	 * <p>Examples: {@code contentType="html, css, javascript, xml" }
	 * <ul>
	 *   <li>text/html</li>
	 *   <li>text/css</li>
	 *   <li>text/javascript</li>
	 *   <li>application/xml</li>
	 *   <li>text/xml</li>
	 * </ul>
	 * @param contentType Comma Separated Value of content-type or sub types.
	 */
	public void setContentType(String contentType) {
		if (StringUtils.isNotEmpty(contentType)) {
			String[] csv = StringUtils.split(contentType, ",");
			for (String t : csv) {
				contentTypes.add(t.toLowerCase());
				useAll = false;
				String[] types = t.split(";")[0].split("/");
				if (types.length >= 2) {
					contentTypes.add(types[1].toLowerCase());
				}
			}
		}
	}

	/**
	 * <p>Check for use compress contents.
	 * @param contentType
	 * @return true use compress.
	 */
	boolean useCompress(Header contentType) {
		if (contentType == null) return false;
		String type = contentType.getValue();
		if (useAll || contentTypes.contains(type)) {
			return true;
		} else {
			//Get the content sub type. (text/html; charset=UTF-8 -> html)
			String[] types = type != null ? type.split(";")[0].split("/") : new String[0];
			if (types.length >= 2 && contentTypes.contains(types[1])) {
				return true;
			} else {
				return false;
			}
		}
	}

	/**
	 * <p>Wrapping entity that compresses content when {@link #writeTo writing}.
	 * {@link http://svn.apache.org/repos/asf/httpcomponents/httpcore/trunk/contrib/src/main/java/org/apache/http/contrib/compress/GzipCompressingEntity.java}
	 */
	static class GzipCompressingEntity extends HttpEntityWrapper {

		//core5 made HttpEntityWrapper#wrappedEntity private (it was protected in 4.4),
		//so the wrapped entity is kept here as well for writeTo().
		private final HttpEntity wrapped;

		public GzipCompressingEntity(HttpEntity entity) {
			super(entity);
			this.wrapped = entity;
		}

		/**
		 * <p>Returns {@code "gzip"}.
		 * <p>core5's {@code EntityDetails#getContentEncoding()} returns a {@code String};
		 * httpcore 4.4's {@code HttpEntity#getContentEncoding()} returned a {@code Header}
		 * (R-5.2). Callers that need the header must build it themselves.
		 */
		@Override
		public String getContentEncoding() {
			return GZIP_CODEC;
		}

		@Override
		public long getContentLength() {
			return -1;
		}

		@Override
		public boolean isChunked() {
			// force content chunking
			return true;
		}

		@Override
		public void writeTo(OutputStream outstream) throws IOException {
			if (outstream == null) {
				throw new IllegalArgumentException("Output stream may not be null");
			}
			GZIPOutputStream gzip = new GZIPOutputStream(outstream);
			try {
				wrapped.writeTo(gzip);
			} finally {
				try {
					gzip.close();
				} catch (IOException e) {
					LOG.debug(ExceptionUtils.getStackTrace(e, 100));
				}
			}
		}
	}
}
