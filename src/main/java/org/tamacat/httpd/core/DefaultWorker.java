/*
 * Copyright (c) 2009 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core;

import java.io.UncheckedIOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLException;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequestFactory;
//core5 also has an impl.nio.DefaultHttpRequestFactory; the classic (blocking) counterpart
//of 4.4's impl.DefaultHttpRequestFactory is impl.io.DefaultClassicHttpRequestFactory (R-5.3).
import org.apache.hc.core5.http.impl.io.DefaultClassicHttpRequestFactory;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.impl.io.HttpService;
import org.tamacat.httpd.config.ServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.tamacat.httpd.core.util.ExceptionUtils;

/**
 * <p>This class is a worker thread for multi thread server.
 */
public class DefaultWorker implements Worker {
	private static final Logger LOG = LoggerFactory.getLogger(DefaultWorker.class);

	static final String HTTP_IN_CONN = HttpContextKeys.HTTP_IN_CONN;

	protected ServerConfig serverConfig;
	protected HttpService httpService;
	protected Socket socket;
	protected ServerHttpConnection conn;
	protected HttpRequestFactory<ClassicHttpRequest> httpRequestFactory;

	/**
	 * The backend (reverse-proxy) connections reused across the requests of this
	 * worker's inbound connection, keyed by backend target host. Empty until the
	 * first reverse-proxy request creates an entry.
	 * <p>The context itself is re-created every loop iteration (see {@link #run()}),
	 * so it cannot carry this reference across requests by itself; this field is
	 * the actual owner of the backend connections' lifetime, matching the client
	 * side's {@link #conn} field. The same map instance (never a copy) is shared
	 * into every request's context under {@link HttpContextKeys#HTTP_OUT_CONN}, so
	 * a {@code put} made by {@code ReverseProxyHandler.getClientHttpConnection}
	 * while handling a request is immediately visible here too - no explicit
	 * write-back step is needed.
	 * <p>Keyed by target host (rather than a single field) because one inbound
	 * connection can legitimately forward to more than one distinct backend host in
	 * the same keep-alive session: a single listen port can serve several
	 * {@code type="reverse"} {@code <url>} entries with different {@code reverse}
	 * targets (see {@code src/test/resources/url-config.xml}). A single-field
	 * design would let a connection opened for one target be handed back for a
	 * different target's request - the client would then receive a response from
	 * the wrong backend. A plain {@code HashMap} is safe here because a single
	 * worker thread is always the only accessor.
	 * FR-1/BR-1/BR-2, revised in the §12a code-generation review, iteration 1
	 * (the original single-field design had the cross-target reuse defect above);
	 * BR-2a (explicit per-request write-back) is retired by this revision, since
	 * the map being shared mutable state makes an explicit write-back unnecessary.
	 * @since 2.0
	 */
	protected final Map<HttpHost, ClientHttpConnection> backendConns = new HashMap<>();


	public DefaultWorker() {
		httpRequestFactory = DefaultClassicHttpRequestFactory.INSTANCE;
	}

	public DefaultWorker(ServerConfig serverConfig, HttpService httpService,
			HttpRequestFactory<ClassicHttpRequest> httpRequestFactory, Socket socket) {
		this.httpRequestFactory = httpRequestFactory;
		setHttpService(httpService);
		setServerConfig(serverConfig);
		setSocket(socket);
	}
	
	@Override
	public void setServerConfig(ServerConfig serverConfig) {
		this.serverConfig = serverConfig;
		this.conn = new ServerHttpConnection(serverConfig.getSocketBufferSize(), httpRequestFactory);
	}

	@Override
	public void setHttpService(HttpService httpService) {
		this.httpService = httpService;
	}

	@Override
	public void setSocket(Socket socket) {
		this.socket = socket;
	}

	@Override
	public void run() {
		try {
			this.conn.bind(socket);
			LOG.debug("bind - " + conn);
			while (Thread.interrupted()==false) {
				HttpContext context = new HttpCoreContext();
				if (!conn.isOpen()) {
					break;
				} else {
					//Bind server connection objects to the execution context
					context.setAttribute(HTTP_IN_CONN, conn);
				}
				//FR-1/BR-1 (revised): share this worker's backend-connection map -
				//the same instance every request, never a copy - so
				//ReverseProxyHandler.getClientHttpConnection() can find and reuse the
				//entry for the request's target host across requests of this inbound
				//connection, and any entry it creates or replaces is immediately
				//visible here too (no explicit write-back; BR-2a is retired).
				context.setAttribute(HttpContextKeys.HTTP_OUT_CONN, backendConns);
				if (LOG.isDebugEnabled()){
					//core5 dropped HttpConnection#getMetrics(); the request count now
					//lives on EndpointDetails, which is only available once bound.
					EndpointDetails endpoint = conn.getEndpointDetails();
					LOG.debug("count:" + (endpoint != null ? endpoint.getRequestCount() : -1)
						+  " - " + conn);
				}
				this.httpService.handleRequest(conn, context);
				MDC.clear(); //delete Logging context.
			}
		} catch (Exception e) {
			handleException(e);
		} finally {
			shutdown(conn);
			//FR-1/BR-2 (revised): shutdown every backend connection this worker is
			//holding - one per distinct target host - once, at worker exit, the same
			//timing as the client-side connection above. Connections are
			//intentionally left open across requests for reuse (BR-1).
			for (ClientHttpConnection backendConn : backendConns.values()) {
				shutdown(backendConn);
			}
			backendConns.clear();
		}
	}
	
	protected void handleException(Exception e) {
		//Connection reset by peer: socket write error
		if (e instanceof SocketException) {
			LOG.debug(e.getMessage() + " - " + conn);
		} else if (e instanceof SSLException) {
			LOG.debug(e.getClass() + ": " + e.getMessage() + " - " + conn); 
		} else if (e instanceof ConnectionClosedException) {
			LOG.debug("client closed connection. - " + conn);
		} else if (e instanceof SocketTimeoutException) {
			LOG.debug("timeout >> close connection. - " + conn);
		} else if (e instanceof UncheckedIOException) {
			//SocketException: Broken pipe
			LOG.warn(e.getClass() + ": " + e.getMessage() + " - " + conn);
			LOG.trace(ExceptionUtils.getStackTrace(e));
		} else {
			LOG.error(e.getClass() + ": " + e.getMessage() + " - " + conn);
			LOG.debug(ExceptionUtils.getStackTrace(e));
		}
	}

	protected boolean isClosed() {
		return socket.isClosed();
	}

	protected void shutdown(HttpConnection conn) {
		try {
			if (conn != null) {
				//core5 replaced HttpConnection#shutdown() with close(CloseMode).
				//IMMEDIATE is the force-close that shutdown() performed in 4.4.
				conn.close(CloseMode.IMMEDIATE);
				LOG.trace("server conn shutdown.");
			}
		} finally {
			MDC.clear();
		}
	}
}
