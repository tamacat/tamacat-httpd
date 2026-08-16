/*
 * Copyright (c) 2010, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core;

import java.net.Socket;

public class SocketWrapper {

	private Socket socket;

	public SocketWrapper(Socket socket) {
		this.socket = socket;
	}

	public Socket getSocket() {
		return socket;
	}
}
