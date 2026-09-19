/*
 * Copyright (c) 2009 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

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
	static final BasicCounter COUNTER = new BasicCounter();
	
	static {
		COUNTER.register();
	}
	
	protected ServerConfig serverConfig;
	protected HttpService httpService;
	protected Socket socket;
	protected ServerHttpConnection conn;
	protected HttpRequestFactory httpRequestFactory;
	

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
				if (LOG.isDebugEnabled()){
					LOG.debug("count:" + metrics.getRequestCount() +  " - " + conn);
				}
				this.httpService.handleRequest(conn, context);
				MDC.clear(); //delete Logging context.
			}
		} catch (Exception e) {
			handleException(e);
		} finally {
			shutdown(conn);
			countDown();
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
