/*
 * Copyright (c) 2010 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core;

import java.io.IOException;
import java.net.Socket;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpRequestFactory;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.io.DefaultBHttpServerConnection;
import org.apache.hc.core5.http.impl.io.DefaultHttpRequestParserFactory;
import org.apache.hc.core5.http.impl.io.SocketHolder;

public class ServerHttpConnection extends DefaultBHttpServerConnection {

	long connStartTime = System.currentTimeMillis();
	long lastAccessTime = System.currentTimeMillis();

	/**
	 * <p>HttpComponents Core 5.x has no buffer-size constructor argument: the buffer
	 * size moved into {@link Http1Config}. The socket buffer size configured through
	 * {@code SocketBufferSize} is therefore carried in an {@code Http1Config} and every
	 * other protocol limit is left at the core5 default (R-4 / Q5: only settings that
	 * were explicit before are carried over).
	 */
	static Http1Config http1Config(int buffersize) {
		return Http1Config.custom().setBufferSize(buffersize).build();
	}

	public ServerHttpConnection(int buffersize, HttpRequestFactory<ClassicHttpRequest> factory) {
		super(null, http1Config(buffersize), null, null, null, null,
			new DefaultHttpRequestParserFactory(null, null, factory), null);
	}

	public ServerHttpConnection(int buffersize) {
		super(null, http1Config(buffersize));
	}

	private SocketWrapper socketWrapper;

	public long getConnectionStartTime() {
		return connStartTime;
	}
	
	public long getLastAccessTime() {
		long last = lastAccessTime;
		lastAccessTime = System.currentTimeMillis();
		return last;
	}

	@Override
	public void bind(final Socket socket) throws IOException {
		socketWrapper = new SocketWrapper(socket);
		connStartTime = System.currentTimeMillis();
		lastAccessTime = connStartTime;
		super.bind(socket);
	}

	/**
	 * <p>Returns the bound {@link Socket}, or {@code null} when the connection is not
	 * bound.
	 * <p>core5 removed the {@code getSocket()} accessor from the connection classes;
	 * the socket is reached through the protected {@code getSocketHolder()}. This method
	 * keeps the tamacat accessor that {@code RequestUtils} relies on.
	 */
	public Socket getSocket() {
		SocketHolder holder = getSocketHolder();
		return holder != null ? holder.getSocket() : null;
	}

	@Deprecated
	public void setWebSocketSupport(boolean isWebSocket) {
		socketWrapper.setWebSocketSupport(isWebSocket);
	}

	@Deprecated
	public void setWebDAVSupport(boolean isWebSocketSupport) {
		socketWrapper.setWebDAVSupport(isWebSocketSupport);
	}

	public SocketWrapper getSocketWrapper() {
		return socketWrapper;
	}
}
