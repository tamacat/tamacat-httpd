/*
 * Copyright (c) 2009 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.filter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpEntityContainer;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.tamacat.httpd.config.ReverseUrl;
import org.tamacat.httpd.core.HttpContextKeys;
import org.tamacat.httpd.util.HeaderUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tamacat.httpd.core.util.StringUtils;

/**
 * <p>
 * HTML link convert for reverse proxy.
 */
public class HtmlLinkConvertInterceptor implements HttpResponseInterceptor {

	private static final Logger LOG = LoggerFactory.getLogger(HtmlLinkConvertInterceptor.class);

	protected Set<String> contentTypes = new HashSet<String>();
	protected List<Pattern> linkPatterns = new ArrayList<Pattern>();

	public HtmlLinkConvertInterceptor() {
		contentTypes.add("html");
	}

	/**
	 * Add link convert pattern.
	 * 
	 * @param regex The expression to be compiled.(case insensitive)
	 */
	public void setLinkPattern(String regex) {
		this.linkPatterns.add(Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
	}

	/**
	 * <p>core5 passes the entity metadata as a separate {@link EntityDetails} argument and
	 * {@code HttpResponse} itself has no entity accessor, so the entity is reached by
	 * downcasting the response to {@link HttpEntityContainer} (R-5.1, Q6). In the classic
	 * blocking pipeline the instance handed to a response interceptor is always a
	 * {@code ClassicHttpResponse}, which extends {@code HttpEntityContainer}.
	 *
	 * <p>When the downcast is not possible (a non-classic {@code HttpProcessor}) the
	 * response is passed through unconverted and the condition is logged at WARN
	 * (SEC-4.1). The {@code ClassCastException} is not swallowed silently. Note that the
	 * links in the passed-through body then still point at the backend path.
	 */
	@Override
	public void process(HttpResponse response, EntityDetails entityDetails, HttpContext context)
			throws HttpException, IOException {
		if (context == null) {
			throw new IllegalArgumentException("HTTP context may not be null");
		}
		ReverseUrl reverseUrl = (ReverseUrl) context.getAttribute(HttpContextKeys.REVERSE_URL);
		if (reverseUrl != null) {
			Header header = response.getFirstHeader(HttpHeaders.CONTENT_TYPE);
			if (header != null && HeaderUtils.inContentType(contentTypes, header)) {
				if (!(response instanceof HttpEntityContainer)) {
					//SEC-4.1: do not swallow. Pass through without converting, and log it.
					LOG.warn("HTML link conversion skipped: the response is not an HttpEntityContainer"
						+ " (" + response.getClass().getName() + ")."
						+ " HtmlLinkConvertInterceptor requires the classic (blocking) HttpProcessor."
						+ " Links in the response body are left pointing at the backend path.");
					return;
				}
				HttpEntityContainer container = (HttpEntityContainer) response;
				String before = reverseUrl.getReverse().getPath();
				String after = reverseUrl.getServiceUrl().getPath();
				HttpEntity entity = container.getEntity();
				if (before.equals(after)) {
					container.setEntity(entity);
				} else if (entity != null) {
					response.setHeader(HttpHeaders.TRANSFER_ENCODING, HeaderElements.CHUNKED_ENCODING); //Transfer-Encoding:chunked
					response.removeHeaders(HttpHeaders.CONTENT_LENGTH);
					container.setEntity(new LinkConvertingEntity(entity, before, after, linkPatterns));
				}
			}
		}
	}

	/**
	 * <p>
	 * Set the content type of the link convertion.<br>
	 * default are "text/html" content types to convert.
	 * </p>
	 * <p>
	 * The {@code contentType} value is case insensitive,<br>
	 * and the white space of before and after is trimmed.
	 * </p>
	 * 
	 * <p>
	 * Examples: {@code contentType="html, css, javascript, xml" }
	 * <ul>
	 * <li>text/html</li>
	 * <li>text/css</li>
	 * <li>text/javascript</li>
	 * <li>application/xml</li>
	 * <li>text/xml</li>
	 * </ul>
	 * 
	 * @param contentType Comma Separated Value of content-type or sub types.
	 */
	public void setContentType(String contentType) {
		if (StringUtils.isNotEmpty(contentType)) {
			String[] csv = contentType.split(",");
			for (String t : csv) {
				contentTypes.add(t.trim().toLowerCase());
				String[] types = t.split(";")[0].split("/");
				if (types.length >= 2) {
					contentTypes.add(types[1].trim().toLowerCase());
				}
			}
		}
	}
}
