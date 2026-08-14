/*
 * Copyright (c) 2019 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.middleware;

import org.tamacat.httpd.tomcat.TomcatManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The TomcatEmbeded middleware for UnifiedHttpEngine.
 * Startup tamacat-httpd with Tomcat.
 */
public class TomcatEmbeded implements Middleware {

	private static final Logger LOG = LoggerFactory.getLogger(TomcatEmbeded.class);

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
