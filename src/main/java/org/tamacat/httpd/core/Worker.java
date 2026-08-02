/*
 * Copyright (c) 2013, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core;

import java.net.Socket;

import org.apache.hc.core5.http.impl.io.HttpService;
import org.tamacat.httpd.config.ServerConfig;

public interface Worker extends Runnable {

	void setServerConfig(ServerConfig serverConfig);

	void setHttpService(HttpService httpService);

	void setSocket(Socket socket);
}
