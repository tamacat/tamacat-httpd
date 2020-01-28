/*
 * Copyright (c) 2012, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.tomcat;

import org.tamacat.httpd.core.HttpEngine;

/**
 * <p>It is implements of the multi-thread server.
 * The embedded Tomcat is supported. 
 */
public class TomcatHttpEngine extends HttpEngine {

	@Override
	public void init() {
		super.init();
		TomcatManager.start();
	}
	
	@Override
	public void stopHttpd() {
		TomcatManager.stop();
		super.stopHttpd();
	}
}
