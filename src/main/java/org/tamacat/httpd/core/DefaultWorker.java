/*
 * Copyright (c) 2009 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLException;

import org.tamacat.httpcore4.ConnectionClosedException;
import org.tamacat.httpcore4.HttpConnection;
import org.tamacat.httpcore4.HttpConnectionMetrics;
import org.tamacat.httpcore4.HttpRequestFactory;
import org.tamacat.httpcore4.protocol.BasicHttpContext;
import org.tamacat.httpcore4.protocol.HttpContext;
import org.tamacat.httpcore4.protocol.HttpService;
import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.httpd.core.jmx.BasicCounter;
import org.tamacat.httpd.core.util.RuntimeIOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.tamacat.httpd.core.util.ExceptionUtils;

/**
 * <p>This class is a worker thread for multi thread server.
 */
public class DefaultWorker implements Worker {
	static final Logger LOG = LoggerFactory.getLogger(DefaultWorker.class);

	static final String HTTP_IN_CONN = "http.in-conn";

	/**
	 * The context attribute that carries the backend (reverse-proxy) connections
	 * of the current request: the worker's {@code List<ClientHttpConnection>}.
	 * {@code ReverseProxyHandler} adds each connection it opens; the worker closes
	 * them all once the request has been handled.
	 * @since 1.6
	 */
	public static final String HTTP_OUT_CONN = "http.out-conn";

	static final BasicCounter COUNTER = new BasicCounter();

	static {
		COUNTER.register();
	}

	protected ServerConfig serverConfig;
	protected HttpService httpService;
	protected Socket socket;
	protected ServerHttpConnection conn;
	protected HttpRequestFactory httpRequestFactory;

	/**
	 * The backend (reverse-proxy) connections opened while handling the current
	 * request, shared (the same instance, never a copy) into every request's
	 * context under {@link #HTTP_OUT_CONN}. A backend connection is never reused:
	 * the ones a request opened are closed once that request has been handled.
	 * Before this, nothing closed them and they stayed open until garbage
	 * collection. A plain {@code ArrayList} is safe because the worker thread is
	 * its only accessor.
	 * @since 1.6
	 */
	protected final List<ClientHttpConnection> backendConns = new ArrayList<>();


	public DefaultWorker() {
		httpRequestFactory = new StandardHttpRequestFactory();
	}

	public DefaultWorker(ServerConfig serverConfig, HttpService httpService, HttpRequestFactory httpRequestFactory,Socket socket) {
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
			countUp();
			LOG.debug("bind - " + conn);
			HttpConnectionMetrics metrics = this.conn.getMetrics();
			while (Thread.interrupted()==false) {
				HttpContext context = new BasicHttpContext();
				if (!conn.isOpen()) {
					break;
				} else {
					//Bind server connection objects to the execution context
					context.setAttribute(HTTP_IN_CONN, conn);
				}
				context.setAttribute(HTTP_OUT_CONN, backendConns);
				if (LOG.isDebugEnabled()){
					LOG.debug("count:" + metrics.getRequestCount() +  " - " + conn);
				}
				this.httpService.handleRequest(conn, context);
				//handleRequest() returns once the response - including a body streamed
				//from a backend connection - has been sent, so this request's backend
				//connections are done with. Closing them any earlier, e.g. in
				//ReverseProxyHandler, would cut off the streamed body.
				closeBackendConnections(false);
				MDC.clear(); //delete Logging context.
			}
		} catch (Exception e) {
			handleException(e);
		} finally {
			//Also runs for a request that ended in an exception (or an Error) before
			//its backend connections were closed above.
			closeBackendConnections(true);
			shutdown(conn);
			countDown();
		}
	}

	/**
	 * Close the backend connections opened for the current request, then forget them.
	 * @param immediately false: close normally, once the response has been sent.
	 *   true: abort (RST), when the request ended in an exception and the
	 *   connection may be in any state.
	 * @since 1.6
	 */
	protected void closeBackendConnections(boolean immediately) {
		for (ClientHttpConnection backendConn : backendConns) {
			try {
				if (immediately) {
					backendConn.shutdown();
				} else {
					backendConn.close();
				}
			} catch (IOException e) {
				//close() and shutdown() close the socket even when they throw.
				LOG.trace("backend conn close: " + e.getMessage());
			}
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
		} else if (e instanceof RuntimeIOException) {
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
				conn.shutdown();
				LOG.trace("server conn shutdown.");
			}
		} catch (IOException ignore) {
		} finally {
			MDC.clear();
		}
	}
	
	protected void countUp() {
		int active = COUNTER.countUp();
		LOG.trace("active: "+active);
	}

	protected void countDown() {
		int active = COUNTER.countDown();
		LOG.trace("active: "+active);
	}
}
