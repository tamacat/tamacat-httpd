/*
 * Copyright (c) 2009 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core;

import java.io.UncheckedIOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLException;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.HttpConnection;
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
	 * The backend (reverse-proxy) connections opened while handling the current
	 * request. Backend keep-alive is off: every request gets connections of its
	 * own, and they are all closed once that request has been handled.
	 * <p>The context is re-created every loop iteration (see {@link #run()}); this
	 * field owns the backend connections' lifetime, and the same list instance
	 * (never a copy) is shared into every request's context under
	 * {@link HttpContextKeys#HTTP_OUT_CONN}, so a connection that
	 * {@code ReverseProxyHandler.getClientHttpConnection} adds while handling the
	 * request is visible here without a write-back step. A plain {@code ArrayList}
	 * is safe because the worker thread is its only accessor.
	 * <p>2.0 used to keep these connections across the requests of one inbound
	 * connection (a {@code Map} keyed by target host). That reuse raced with
	 * backends closing idle connections - the next request then failed with 503 -
	 * and could hand a response body left unread by an exception to the next
	 * request. 1.6 never reused backend connections, but never closed them either,
	 * leaving them open until garbage collection.
	 * @since 2.0
	 */
	protected final List<ClientHttpConnection> backendConns = new ArrayList<>();


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
				//Share this worker's backend-connection list - the same instance every
				//request, never a copy - so the connections ReverseProxyHandler opens
				//for this request are closed below.
				context.setAttribute(HttpContextKeys.HTTP_OUT_CONN, backendConns);
				if (LOG.isDebugEnabled()){
					//core5 dropped HttpConnection#getMetrics(); the request count now
					//lives on EndpointDetails, which is only available once bound.
					EndpointDetails endpoint = conn.getEndpointDetails();
					LOG.debug("count:" + (endpoint != null ? endpoint.getRequestCount() : -1)
						+  " - " + conn);
				}
				this.httpService.handleRequest(conn, context);
				//handleRequest() returns once the response - including a body streamed
				//from a backend connection - has been sent, so this request's backend
				//connections are done with. Closing them any earlier, e.g. in
				//ReverseProxyHandler, would cut off the streamed body.
				closeBackendConnections(CloseMode.GRACEFUL);
				MDC.clear(); //delete Logging context.
			}
		} catch (Exception e) {
			handleException(e);
		} finally {
			//Also runs for a request that ended in an exception (or an Error) before
			//its backend connections were closed above.
			closeBackendConnections(CloseMode.IMMEDIATE);
			shutdown(conn);
		}
	}

	/**
	 * Close the backend connections opened for the current request, then forget them.
	 * @param closeMode {@code GRACEFUL} once the response has been sent;
	 *   {@code IMMEDIATE} when the request ended in an exception and the connection
	 *   may be in any state.
	 * @since 2.0
	 */
	protected void closeBackendConnections(CloseMode closeMode) {
		for (ClientHttpConnection backendConn : backendConns) {
			backendConn.close(closeMode);
		}
		backendConns.clear();
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
