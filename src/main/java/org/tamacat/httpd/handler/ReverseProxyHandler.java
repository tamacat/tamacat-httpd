/*
 * Copyright (c) 2009 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.handler;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;

import javax.net.SocketFactory;

import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.MalformedChunkCodingException;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.FileEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.http.protocol.RequestConnControl;
import org.apache.hc.core5.http.protocol.RequestContent;
import org.apache.hc.core5.http.protocol.RequestExpectContinue;
import org.apache.hc.core5.http.protocol.RequestTargetHost;
import org.apache.hc.core5.http.protocol.RequestUserAgent;
import org.tamacat.httpd.config.ReverseUrl;
import org.tamacat.httpd.config.ServiceUrl;
import org.tamacat.httpd.core.BackEndKeepAliveConnReuseStrategy;
import org.tamacat.httpd.core.BasicHttpStatus;
import org.tamacat.httpd.core.ClientHttpConnection;
import org.tamacat.httpd.core.HttpContextKeys;
import org.tamacat.httpd.core.HttpProcessorBuilder;
import org.tamacat.httpd.exception.BadRequestException;
import org.tamacat.httpd.exception.HttpException;
import org.tamacat.httpd.exception.ServiceUnavailableException;
import org.tamacat.httpd.util.RequestUtils;
import org.tamacat.httpd.util.ReverseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tamacat.httpd.core.util.StringUtils;

/**
 * The {@link HttpHandler} for reverse proxy.
 */
public class ReverseProxyHandler extends AbstractHttpHandler {

	static final Logger LOG = LoggerFactory.getLogger(ReverseProxyHandler.class);

	protected static final String DEFAULT_CONTENT_TYPE = "text/html; charset=UTF-8";

	protected HttpRequestExecutor httpexecutor;
	protected SocketFactory socketFactory;
	protected HttpProcessorBuilder procBuilder = new HttpProcessorBuilder();
	protected String proxyAuthorizationHeader = "X-ReverseProxy-Authorization";
	protected String proxyOrignPathHeader = "X-ReverseProxy-Origin-Path"; // v1.1
	protected int connectionTimeout = 30000;
	protected int socketBufferSize = 8192;
	protected ConnectionReuseStrategy connStrategy;
	protected HttpProcessor httpproc;
	protected boolean useForwardHeader;
	protected String forwardHeader = "X-Forwarded-For";
	protected boolean supportExpectContinue;
	protected boolean forceUpdateHttpVersion = true;
	protected boolean strictHttps;
	protected boolean overrideHostHeaderWithReverseUrl;
	protected String overrideHostHeader;

	public ReverseProxyHandler() {
		this.httpexecutor = new HttpRequestExecutor();
		setParseRequestParameters(false);
	}

	@Override
	public void setServiceUrl(ServiceUrl serviceUrl) {
		super.setServiceUrl(serviceUrl);
		setDefaultHttpRequestInterceptor();
		connStrategy = new BackEndKeepAliveConnReuseStrategy(serviceUrl.getServerConfig());
		httpproc = procBuilder.build();
	}

	@Override
	public void doRequest(ClassicHttpRequest request, ClassicHttpResponse response,
			HttpContext context) throws HttpException, IOException {
		//Set the X-Forwarded Headers.
		ReverseUtils.setXForwardedFor(request, context, useForwardHeader, forwardHeader);
		ReverseUtils.setXForwardedHost(request);
		ReverseUtils.setXForwardedProto(request, serviceUrl.getServerConfig());
		ReverseUtils.setXForwardedPort(request, serviceUrl.getServerConfig());
		
		ReverseUrl reverseUrl = getReverseUrl(context);

		//Access Backend server.
		ClassicHttpResponse targetResponse = forwardRequest(request, response, context, reverseUrl);

		ReverseUtils.copyHttpResponse(targetResponse, response);
		ReverseUtils.rewriteStatusLine(request, response);
		ReverseUtils.rewriteContentLocationHeader(request, response, reverseUrl);

		ReverseUtils.rewriteServerHeader(response, reverseUrl);

		//Location Header convert.
		ReverseUtils.rewriteLocationHeader(request, response, reverseUrl);

		//Set-Cookie Header convert.
		ReverseUtils.rewriteSetCookieHeader(request, response, reverseUrl);

		//Set the entity and response headers from targetResponse.
		response.setEntity(targetResponse.getEntity());
	}

	
	protected ClientHttpConnection getClientHttpConnection(HttpContext context, ReverseUrl reverseUrl) throws IOException {
		ClientHttpConnection conn = new ClientHttpConnection(serviceUrl.getServerConfig());
		Socket outsocket = createSocket(reverseUrl);
		if (outsocket == null) throw new SocketException("Can not create socket.");
		conn.bind(outsocket);
		if (LOG.isTraceEnabled()) {
			LOG.trace("Outgoing connection to "	+ outsocket.getInetAddress());
		}
		return conn;
	}
	
	/**
	 * Request forwarding to backend server.
	 */
	protected ClassicHttpResponse forwardRequest(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context, ReverseUrl reverseUrl) {
		if (LOG.isDebugEnabled()) {
			LOG.debug(">> " + RequestUtils.getRequestLine(request));
		}
		
		if (reverseUrl == null) {
			throw new ServiceUnavailableException("reverseUrl is null.");
		}
		try {
			context.setAttribute(HttpContextKeys.REVERSE_URL, reverseUrl);
			HttpContext reverseContext = new BasicHttpContext(context);
			reverseContext.setAttribute(HttpContextKeys.REVERSE_URL, reverseUrl);

			ReverseHttpRequest targetRequest = ReverseHttpRequestFactory
				.getInstance(request, response, reverseContext, reverseUrl, 
						forceUpdateHttpVersion? HttpVersion.HTTP_1_1 : RequestUtils.getVersion(request));
			
			targetRequest.setHeader(proxyOrignPathHeader, serviceUrl.getPath()); // v1.1
			
			//Override host request header. (v1.5.2)
			if (overrideHostHeaderWithReverseUrl) {
				targetRequest.setHeader(HttpHeaders.HOST, reverseUrl.getTargetHost().getHostName());
			} else if (StringUtils.isNotEmpty(overrideHostHeader)) {
				targetRequest.setHeader(HttpHeaders.HOST, overrideHostHeader);
			}
			
			//forward remote user.
			ReverseUtils.setReverseProxyAuthorization(targetRequest, context, proxyAuthorizationHeader);
			try {
				httpexecutor.preProcess(targetRequest, httpproc, reverseContext);
				ClientHttpConnection conn = getClientHttpConnection(context, reverseUrl);
				ClassicHttpResponse targetResponse = httpexecutor.execute(targetRequest, conn, reverseContext);
				httpexecutor.postProcess(targetResponse, httpproc, reverseContext);
				return targetResponse;
			} catch (MalformedChunkCodingException e) {
				//ex. Bad chunk header: Q
				throw new BadRequestException(e);
			}
		} catch (SocketException e) {
			throw new ServiceUnavailableException(
				BasicHttpStatus.SC_GATEWAY_TIMEOUT.getReasonPhrase() + " URL=" + reverseUrl.getReverse());
		} catch (RuntimeException e) {
			handleException(request, response, e);
			return response;
		} catch (Exception e) {
			handleException(request, response, e);
			return response;
		}
	}

	/**
	 * Preset the HttpRequestInterceptor.
	 */
	protected void setDefaultHttpRequestInterceptor() {
		procBuilder.addInterceptor(new RequestContent())
				.addInterceptor(new RequestTargetHost())
				.addInterceptor(new RequestConnControl())
				.addInterceptor(new RequestUserAgent());
		//Bug#14
		if (supportExpectContinue) {
			//core5's RequestExpectContinue has no "activeByDefault" constructor argument;
			//the interceptor is simply registered (or not), which is what the
			//supportExpectContinue flag already decides here.
			procBuilder.addInterceptor(new RequestExpectContinue());
		}
	}

	public void addHttpRequestInterceptor(HttpRequestInterceptor interceptor) {
		procBuilder.addInterceptor(interceptor);
	}

	public void addHttpResponseInterceptor(HttpResponseInterceptor interceptor) {
		procBuilder.addInterceptor(interceptor);
	}

	/**
	 * Set the header name of Reverse Proxy Authorization. default: "X-ReverseProxy-Authorization"
	 * @param proxyAuthorizationHeader
	 */
	public void setProxyAuthorizationHeader(String proxyAuthorizationHeader) {
		this.proxyAuthorizationHeader = proxyAuthorizationHeader;
	}

	/**
	 * Set the header name of Reverse Proxy Origin Path. default: "X-ReverseProxy-Origin-Path"
	 * @param proxyOrignPathHeader
	 * @since 1.1
	 */
	public void setProxyOrignPathHeader(String proxyOrignPathHeader) {
		this.proxyOrignPathHeader = proxyOrignPathHeader;
	}

	@Override
	protected HttpEntity getEntity(String html) {
		try {
			//core5 entities are immutable: content type is set at construction time.
			return new StringEntity(html,
				ContentType.create(ContentType.parse(DEFAULT_CONTENT_TYPE).getMimeType(), encoding));
		} catch (Exception e) {
			// UnsupportedEncodingException or UnsupportedCharsetException
			return null;
		}
	}

	@Override
	protected HttpEntity getFileEntity(File file) {
		ContentType type = ContentType.DEFAULT_TEXT;
		try {
			type = ContentType.create(getContentType(file));
		} catch (Exception e) {
		}
		return new FileEntity(file, type);
	}

	/**
	 * Create a socket in order to connect with reverse URL.
	 */
	protected Socket createSocket(ReverseUrl reverseUrl) throws IOException {
		if (this.socketFactory == null) {
			if ("https".equalsIgnoreCase(reverseUrl.getReverse().getProtocol())) {
				return ReverseUtils.createSSLSocket(reverseUrl, strictHttps);
			} else {
				this.socketFactory = SocketFactory.getDefault();
			}
		}
		return socketFactory.createSocket(reverseUrl.getTargetAddress().getHostName(),
				reverseUrl.getTargetAddress().getPort());
	}

	/**
	 * Get the ReverseUrl
	 * @since 1.2
	 */
	protected ReverseUrl getReverseUrl(HttpContext context) {
		return serviceUrl.getReverseUrl();
	}

	/**
	 * Get a remote IP address using X-Forwarded-For request header.
	 * @param forwardHeader
	 */
	public void setUseForwardHeader(boolean forwardHeader) {
		this.useForwardHeader = forwardHeader;
	}

	/**
	 * Set a request header name for Forwarded IP address.
	 * @param forwardHeader
	 */
	public void setForwardHeader(String forwardHeader) {
		this.forwardHeader = forwardHeader;
	}
	
	/**
	 * true: ReverseProxy add Expect: 100-continue request header to back-end.
	 * default: false, For back-end server that can not understand Expect header.
	 * @since 1.3.2/1.4-20181214
	 * @param supportExpectContinue
	 */
	public void setSupportExpectContinue(boolean supportExpectContinue) {
		this.supportExpectContinue = supportExpectContinue;
	
	}
	
	/**
	 * force update HTTP/1.0 to HTTP/1.1 (default ture)
	 * @since 1.5-20211107
	 * @param forceUpdateHttpVersion
	 */
	public void setForceUpdateHttpVersion(boolean forceUpdateHttpVersion) {
		this.forceUpdateHttpVersion = forceUpdateHttpVersion;
	}
	
	/**
	 * Use Strict HostnameVerifier and Strict TrustManager.
	 * isStrictHttps true (default false)
	 * 
	 * javax.net.ssl.SSLHandshakeException: PKIX path building failed:
	 * sun.security.provider.certpath.SunCertPathBuilderException:
	 * unable to find valid certification path to requested target
	 * @param strictHttps
	 * @since 1.5-20211228
	 */
	public void setStrictHttps(boolean strictHttps) {
		this.strictHttps = strictHttps;
	}
	
	/**
	 * Overrides the Host request header with the hostname from the reverse URL.
	 * @param overrideHostHeaderWithReverseUrl
	 * @since 1.5.2-20250310
	 */
	public void setOverrideHostHeaderWithReverseUrl(boolean overrideHostHeaderWithReverseUrl) {
		this.overrideHostHeaderWithReverseUrl = overrideHostHeaderWithReverseUrl;
	}

	/**
	 * Overrides the Host request header. (When overrideHostHeaderWithReverseUrl=false)
	 * @param overrideHostHeader
	 * @since 1.5.2-20250310
	 */
	public void setOverrideHostHeader(String overrideHostHeader) {
		this.overrideHostHeader = overrideHostHeader;
	}
}
