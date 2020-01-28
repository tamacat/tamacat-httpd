/*
 * Copyright (c) 2019 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.middleware;

import org.tamacat.httpd.middleware.Middleware;
import org.tamacat.httpd.tomcat.TomcatManager;
import org.tamacat.log.Log;
import org.tamacat.log.LogFactory;

/**
 * The TomcatEmbeded middleware for UnifiedHttpEngine.
 * Startup tamacat-httpd with Tomcat.
 */
public class TomcatEmbeded implements Middleware {

	static final Log LOG = LogFactory.getLog(TomcatEmbeded.class);

	@Override
	public void startup() {
		try {
			TomcatManager.start();
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		}
	}

	@Override
	public void shutdown() {
		TomcatManager.stop();
	}

}
