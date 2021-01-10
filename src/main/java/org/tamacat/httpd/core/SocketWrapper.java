/*
 * Copyright (c) 2010, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core;

import java.net.Socket;

public class SocketWrapper {

	private Socket socket;
	
	@Deprecated
	private boolean isWebSocketSupport;
	@Deprecated
	private boolean isWebDAVSupport;
	
	@Deprecated
	public boolean isWebDAVSupport() {
		return isWebDAVSupport;
	}

	@Deprecated
	public void setWebDAVSupport(boolean isWebDAVSupport) {
		this.isWebDAVSupport = isWebDAVSupport;
	}

	public SocketWrapper(Socket socket) {
		this.socket = socket;
	}
	
	@Deprecated
	public void setWebSocketSupport(boolean isWebSocketSupport) {
		this.isWebSocketSupport = isWebSocketSupport;
	}
	
	@Deprecated
	public boolean isWebSocketSupport() {
		return isWebSocketSupport;
	}
	
	public Socket getSocket() {
		return socket;
	}
}
