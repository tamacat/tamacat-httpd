/*
 * Copyright (c) 2009 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.handler;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.Date;
import java.util.Map;

import javax.net.SocketFactory;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.HttpVersion;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.protocol.RequestConnControl;
import org.apache.http.protocol.RequestContent;
import org.apache.http.protocol.RequestExpectContinue;
import org.apache.http.protocol.RequestTargetHost;
import org.apache.http.protocol.RequestUserAgent;
import org.tamacat.httpd.config.HttpProxyConfig;
import org.tamacat.httpd.config.ReverseUrl;
import org.tamacat.httpd.config.ServiceUrl;
import org.tamacat.httpd.core.BackEndKeepAliveConnReuseStrategy;
import org.tamacat.httpd.core.BasicHttpStatus;
import org.tamacat.httpd.core.ClientHttpConnection;
import org.tamacat.httpd.core.HttpProcessorBuilder;
import org.tamacat.httpd.core.jmx.PerformanceCounter;
import org.tamacat.httpd.exception.HttpException;
import org.tamacat.httpd.exception.ServiceUnavailableException;
import org.tamacat.httpd.util.RequestUtils;
import org.tamacat.httpd.util.ReverseUtils;
import org.tamacat.log.Log;
import org.tamacat.log.LogFactory;
import org.tamacat.util.CollectionUtils;
import org.tamacat.util.DateUtils;

/**
 * The {@link HttpHandler} for reverse proxy.
 */
public class BackendKeepAliveReverseProxyHandler extends AbstractHttpHandler {

	static final Log LOG = LogFactory.getLog(ReverseProxyHandler.class);

	protected static final String HTTP_OUT_CONN = "http.out-conn";
	protected static final String HTTP_CONN_KEEPALIVE = "http.proxy.conn-keepalive";

	protected static final String DEFAULT_CONTENT_TYPE = "text/html; charset=UTF-8";

	protected HttpRequestExecutor httpexecutor;
	protected SocketFactory socketFactory;
	protected HttpProcessorBuilder procBuilder = new HttpProcessorBuilder();
	protected String proxyAuthorizationHeader = "X-ReverseProxy-Authorization";
	protected String proxyOrignPathHeader = "X-ReverseProxy-Origin-Path"; // v1.1
	protected int connectionTimeout = 30000;
	protected int socketBufferSize = 8192;
	protected ConnectionReuseStrategy connStrategy;
	protected HttpProxyConfig proxyConfig = new HttpProxyConfig();
	protected HttpProcessor httpproc;
	protected boolean useForwardHeader;
	protected String forwardHeader = "X-Forwarded-For";

	public BackendKeepAliveReverseProxyHandler() {
		this.httpexecutor = new HttpRequestExecutor();
		setParseRequestParameters(false);
		setDefaultHttpRequestInterceptor();
	}

	@Override
	public void setServiceUrl(ServiceUrl serviceUrl) {
		super.setServiceUrl(serviceUrl);
		connStrategy = new BackEndKeepAliveConnReuseStrategy(serviceUrl.getServerConfig());
		httpproc = procBuilder.build();
	}

	@Override
	public void doRequest(HttpRequest request, HttpResponse response,
			HttpContext context) throws HttpException, IOException {
		//Set the X-Forwarded Headers.
		ReverseUtils.setXForwardedFor(request, context, useForwardHeader, forwardHeader);
		ReverseUtils.setXForwardedHost(request);

		ReverseUrl reverseUrl = getReverseUrl(context);

		//Access Backend server.
		HttpResponse targetResponse = forwardRequest(request, response, context, reverseUrl);

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
		ClientHttpConnection conn = null;
		String key = reverseUrl.getReverse().toString();
		//Reuse client connention (KeepAlive)
		@SuppressWarnings("unchecked")
		Map<String, ClientHttpConnection> conns = (Map<String,ClientHttpConnection>) context.getAttribute(HTTP_OUT_CONN);
		if (conns != null) {
			conn = conns.get(key);
		}
		if (conn == null || !conn.isOpen()) {
			conn = new ClientHttpConnection(serviceUrl.getServerConfig());
			Socket outsocket = createSocket(reverseUrl);
			if (outsocket == null) throw new SocketException("Can not create socket.");
			conn.bind(outsocket);
			if (LOG.isTraceEnabled()) {
				LOG.trace("Outgoing connection to "	+ outsocket.getInetAddress());
			}
		} else {
			long time = conn.getLastAccessTime();
			LOG.debug("get reuse client conn. url="+key+", conn="+conn +", access="+DateUtils.getTime(new Date(time), "yyyyMMddHHmmss"));
		}
		return conn;
	}
	
	protected void setReuseClientHttpConnection(HttpContext context, ClientHttpConnection conn) {
		@SuppressWarnings("unchecked")
		Map<String, ClientHttpConnection> conns = (Map<String, ClientHttpConnection>) context.getAttribute(HTTP_OUT_CONN);
		if (conns == null) {
			conns = CollectionUtils.newLinkedHashMap();
		}
		String key = getReverseUrl(context).getReverse().toString();
		LOG.debug("set reuse client conn. url="+key+", conn="+conn);
		conns.put(key, conn);
		context.setAttribute(HTTP_OUT_CONN, conns);
	}
	
	/**
	 * Request forwarding to backend server.
	 */
	protected HttpResponse forwardRequest(HttpRequest request, HttpResponse response, HttpContext context, ReverseUrl reverseUrl) {
		if (LOG.isDebugEnabled()) {
			LOG.debug(">> " + RequestUtils.getRequestLine(request));
		}
		
		if (reverseUrl == null) {
			throw new ServiceUnavailableException("reverseUrl is null.");
		}
		try {
			context.setAttribute("reverseUrl", reverseUrl);
			HttpContext reverseContext = new BasicHttpContext();
			reverseContext.setAttribute("reverseUrl", reverseUrl);
			ReverseHttpRequest targetRequest = ReverseHttpRequestFactory
					.getInstance(request, response, reverseContext, reverseUrl, HttpVersion.HTTP_1_1);
			
			targetRequest.setHeader(proxyOrignPathHeader, serviceUrl.getPath()); // v1.1
			
			//forward remote user.
			ReverseUtils.setReverseProxyAuthorization(targetRequest, context, proxyAuthorizationHeader);
			try {
				countUp(reverseUrl, context);
				
				httpexecutor.preProcess(targetRequest, httpproc, reverseContext);
				ClientHttpConnection conn = getClientHttpConnection(context, reverseUrl);
				HttpResponse targetResponse = httpexecutor.execute(targetRequest, conn, reverseContext);
				httpexecutor.postProcess(targetResponse, httpproc, reverseContext);
				//Keep-Alive client connection.
				boolean keepAlive = connStrategy.keepAlive(targetResponse, reverseContext);
				if (keepAlive) {
					setReuseClientHttpConnection(context, conn);
				}
				if (LOG.isDebugEnabled()) {
					LOG.debug("client conn keep-alive count:" + conn.getMetrics().getResponseCount() + " - " + conn);
				}
				return targetResponse;
			} finally {
				countDown(reverseUrl, context);
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
				.addInterceptor(new RequestUserAgent())
				.addInterceptor(new RequestExpectContinue(true));
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
			StringEntity entity = new StringEntity(html, encoding);
			entity.setContentType(DEFAULT_CONTENT_TYPE);
			return entity;
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
				return ReverseUtils.createSSLSocket(reverseUrl, proxyConfig, false);
			} else {
				this.socketFactory = SocketFactory.getDefault();
			}
		}
		if (proxyConfig.isDirect()) {
			return socketFactory.createSocket(reverseUrl.getTargetAddress().getHostName(),
					reverseUrl.getTargetAddress().getPort());
		} else {
			return proxyConfig.tunnel(reverseUrl.getTargetHost());
		}
	}

	/**
	 * Get the ReverseUrl
	 * @since 1.2
	 */
	protected ReverseUrl getReverseUrl(HttpContext context) {
		return serviceUrl.getReverseUrl();
	}

	public void setHttpProxyConfig(HttpProxyConfig proxyConfig) {
		this.proxyConfig = proxyConfig;
	}
	
	/**
	 * The number of threads under processing is counted up.
	 * @since 1.2
	 */
	protected void countUp(ReverseUrl reverseUrl, HttpContext context) {
		if (reverseUrl instanceof PerformanceCounter) {
			((PerformanceCounter) reverseUrl).countUp();
		}
	}

	/**
	 * The number of threads under processing is counted down.
	 * @since 1.2
	 */
	protected void countDown(ReverseUrl reverseUrl, HttpContext context) {
		if (reverseUrl instanceof PerformanceCounter) {
			((PerformanceCounter) reverseUrl).countDown();
		}
	}
	
	public void setUseForwardHeader(boolean forwardHeader) {
		this.useForwardHeader = forwardHeader;
	}

	public void setForwardHeader(String forwardHeader) {
		this.forwardHeader = forwardHeader;
	}
}
