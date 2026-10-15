/*
 * Copyright 2012 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.tomcat;

import java.util.HashMap;
import java.util.Map;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A singleton Tomcat instance is managed for every port. 
 */
public class TomcatManager {

	static final Logger LOG = LoggerFactory.getLogger(TomcatManager.class);
	
	static final Map<Integer, Tomcat> MANAGER = new HashMap<>();
	
	/**
	 * The switch logback-classic reads before it registers its
	 * ServletContainerInitializer listener in a web application
	 * (ch.qos.logback.core.CoreConstants.DISABLE_SERVLET_CONTAINER_INITIALIZER_KEY).
	 */
	static final String LOGBACK_DISABLE_SCI_KEY = "logbackDisableServletContainerInitializer";
	
	/**
	 * The instance corresponding to a port is returned. 
	 * @param port
	 * @return Tomcat instance
	 */
	public static synchronized Tomcat getInstance(int port) {
		Tomcat instance = MANAGER.get(port);
		if (instance == null) {
			disableLogbackServletContainerInitializer();
			instance = new Tomcat();
			instance.setPort(port);
			MANAGER.put(port, instance);
		}
		return instance;
	}
	
	/**
	 * Keeps logback from stopping the server's logging when a web application stops.
	 * <p>logback-classic ships a ServletContainerInitializer that adds a listener to
	 * every web application; when that application stops, the listener stops the
	 * JVM-wide LoggerContext. The embedded Tomcat shares the server's LoggerContext,
	 * so stopping any webapp would silently end all logging of the whole server.
	 * logback skips the initializer when this key is "true". It is set here, before
	 * any Tomcat exists, so it applies to every webapp. An explicit value given by
	 * the user (system property or environment variable, including "false") is
	 * respected; blank values count as unset, the same as logback reads them.
	 * A webapp can still opt back in with a context-param of the same name.
	 */
	static void disableLogbackServletContainerInitializer() {
		if (isBlank(System.getProperty(LOGBACK_DISABLE_SCI_KEY))
				&& isBlank(System.getenv(LOGBACK_DISABLE_SCI_KEY))) {
			System.setProperty(LOGBACK_DISABLE_SCI_KEY, "true");
		}
	}
	
	static boolean isBlank(String value) {
		return value == null || value.trim().isEmpty();
	}
	
	/**
	 * Start the all Tomcat instances.
	 */
	public static void start() {
		for (Tomcat instance : MANAGER.values()) {
			TomcatThread t = new TomcatThread(instance);
			t.start();
		}
	}
	
	/**
	 * Stop the all Tomcat instances.
	 */
	public static void stop() {
		for (Tomcat instance : MANAGER.values()) {
			try {
				instance.stop();
			} catch (LifecycleException e) {
				LOG.error(e.getMessage(), e);
			}
		}
	}
	
	private  TomcatManager() {}
	
	/**
	 * Thread of Tomcat inscanse.
	 */
    static class TomcatThread extends Thread {
    	final Tomcat tomcat;
    	TomcatThread(Tomcat tomcat) {
    		this.tomcat = tomcat;
    	}
    	
    	public void run() {
    		try {
    			tomcat.start();
    			tomcat.getServer().await();
    		} catch (Exception e) {
				LOG.error(e.getMessage(), e);
    		}
    	}
    }
}
