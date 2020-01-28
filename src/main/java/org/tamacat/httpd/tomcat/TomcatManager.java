/*
 * Copyright (c) 2012, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.tomcat;

import java.util.HashMap;
import java.util.Map;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.tamacat.log.Log;
import org.tamacat.log.LogFactory;

/**
 * A singleton Tomcat instance is managed for every port. 
 */
public class TomcatManager {

	static final Log LOG = LogFactory.getLog(TomcatManager.class);
	
	static final Map<Integer, Tomcat> MANAGER = new HashMap<>();
	
	/**
	 * The instance corresponding to a port is returned. 
	 * @param port
	 * @return Tomcat instance
	 */
	public static synchronized Tomcat getInstance(int port) {
		Tomcat instance = MANAGER.get(port);
		if (instance == null) {
			instance = new Tomcat();
			instance.setPort(port);
			MANAGER.put(port, instance);
		}
		return instance;
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
