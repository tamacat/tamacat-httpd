/*
 * Copyright (c) 2009 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core;

import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import javax.net.ssl.SSLException;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.HttpRequestFactory;
//core5 also has an impl.nio.DefaultHttpRequestFactory; the classic (blocking) counterpart
//of 4.4's impl.DefaultHttpRequestFactory is impl.io.DefaultClassicHttpRequestFactory (R-5.3).
import org.apache.hc.core5.http.impl.io.DefaultClassicHttpRequestFactory;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.impl.io.HttpService;
import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.io.RuntimeIOException;
import org.tamacat.log.DiagnosticContext;
import org.tamacat.log.Log;
import org.tamacat.log.LogFactory;
import org.tamacat.util.ExceptionUtils;

/**
 * <p>This class is a worker thread for multi thread server.
 */
public class DefaultWorker implements Worker {
	static final Log LOG = LogFactory.getLog(DefaultWorker.class);
	static final DiagnosticContext DC = LogFactory.getDiagnosticContext(LOG);

	static final String HTTP_IN_CONN = HttpContextKeys.HTTP_IN_CONN;

	protected ServerConfig serverConfig;
	protected HttpService httpService;
	protected Socket socket;
	protected ServerHttpConnection conn;
	protected HttpRequestFactory<ClassicHttpRequest> httpRequestFactory;
	

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
				HttpContext context = new BasicHttpContext();
				if (!conn.isOpen()) {
					break;
				} else {
					//Bind server connection objects to the execution context
					context.setAttribute(HTTP_IN_CONN, conn);
				}
				if (LOG.isDebugEnabled()){
					//core5 dropped HttpConnection#getMetrics(); the request count now
					//lives on EndpointDetails, which is only available once bound.
					EndpointDetails endpoint = conn.getEndpointDetails();
					LOG.debug("count:" + (endpoint != null ? endpoint.getRequestCount() : -1)
						+  " - " + conn);
				}
				this.httpService.handleRequest(conn, context);
				DC.remove(); //delete Logging context.
			}
		} catch (Exception e) {
			handleException(e);
		} finally {
			shutdown(conn);
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
				//core5 replaced HttpConnection#shutdown() with close(CloseMode).
				//IMMEDIATE is the force-close that shutdown() performed in 4.4.
				conn.close(CloseMode.IMMEDIATE);
				LOG.trace("server conn shutdown.");
			}
		} finally {
			DC.remove();
		}
	}
}
