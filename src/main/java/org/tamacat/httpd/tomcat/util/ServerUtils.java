/*
 * Copyright (c) 2015 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.tomcat.util;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * Replace the "${server.home}" variable to server home directory.
 * <pre>
 * -Dserver.home=/usr/local/tamacat-httpd
 * or
 * -Duser.dir=~/tamacat-httpd
 * </pre>
 */
public class ServerUtils {
	
	private static final Logger LOG = LoggerFactory.getLogger(ServerUtils.class);
	
	protected static String serverHome;

	static {
		try {
			serverHome = System.getProperty("server.home");
			if (serverHome == null) serverHome = System.getProperty("user.dir");
			File home = new File(serverHome);
			serverHome = home.getCanonicalPath();
		} catch (Exception e) {
			LOG.error(e.toString());
		}
	}

	/**
	 * Get ${server.home}
	 * @param docsRoot
	 * @since 1.4-20180522
	 */
	public static String getServerHome() {
		return serverHome;
	}
}
