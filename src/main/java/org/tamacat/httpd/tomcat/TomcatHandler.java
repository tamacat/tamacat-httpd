/*
 * Copyright 2012 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.tomcat;

import java.io.File;
import java.io.FileFilter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.valves.RemoteAddrValve;
import org.tamacat.httpd.config.DefaultReverseUrl;
import org.tamacat.httpd.config.ReverseUrl;
import org.tamacat.httpd.config.ServiceUrl;
import org.tamacat.httpd.handler.ReverseProxyHandler;
import org.tamacat.httpd.tomcat.util.ServerUtils;
import org.tamacat.log.Log;
import org.tamacat.log.LogFactory;
import org.tamacat.util.StringUtils;

/**
 * The reverse proxy handler using the Tomcat Embedded.
 */
public class TomcatHandler extends ReverseProxyHandler {

	static final Log LOG = LogFactory.getLog(TomcatHandler.class);

	protected String serverHome;
	protected String hostname = "127.0.0.1";
	protected String bindAddress = "127.0.0.1";
	protected int port = 8080;
	protected String allowRemoteAddrValve;
	protected String webapps = "./webapps";
	protected String contextPath;
	protected String work = "${server.home}";
	protected Tomcat tomcat;
	protected boolean useWarDeploy = true;
	protected String uriEncoding;
	protected Boolean useBodyEncodingForURI;
	
	@Override
	public void setServiceUrl(ServiceUrl serviceUrl) {
		super.setServiceUrl(serviceUrl);
		
		//deployment
		deploy(serviceUrl);
	}
	
	/**
	 * Deployment Web Applications for Tomcat Embedded
	 * @param serviceUrl
	 */
	protected void deploy(ServiceUrl serviceUrl) {
		ReverseUrl reverseUrl = serviceUrl.getReverseUrl();
		if (reverseUrl == null) {
			reverseUrl = new DefaultReverseUrl(serviceUrl);
			try {
				reverseUrl.setReverse(new URL("http://"+hostname+":"+port+serviceUrl.getPath()));
				serviceUrl.setReverseUrl(reverseUrl);
			} catch (MalformedURLException e) {
			}
		}
		tomcat = TomcatManager.getInstance(port);
		tomcat.setBaseDir(getWork());

		//Tomcat bind address default: 127.0.0.1
		if (StringUtils.isNotEmpty(bindAddress)) {
			tomcat.getConnector().setProperty("address",  bindAddress);
		}
		if (StringUtils.isNotEmpty(uriEncoding)) {
			tomcat.getConnector().setURIEncoding(uriEncoding);
		}
		if (useBodyEncodingForURI != null) {
			tomcat.getConnector().setUseBodyEncodingForURI(useBodyEncodingForURI.booleanValue());
		}

		if (useWarDeploy) {
			deployWarFiles(serviceUrl);
		}

		deployWebapps(serviceUrl);		
	}
	
	/**
	 * Deployment for webapps/serviceUrl
	 * @param serviceUrl
	 */
	protected void deployWebapps(ServiceUrl serviceUrl) {
		try {	    	
			String contextRoot = serviceUrl.getPath().replaceAll("/$", "");
			if (StringUtils.isNotEmpty(contextPath)) {
				contextRoot = contextPath;
			}
	    	//check already add webapp.
	    	if (tomcat.getHost().findChild(contextRoot) != null) {
	    		return; //skip
	    	}
			File baseDir = new File(getWebapps() + contextRoot);
			Context ctx = tomcat.addWebapp(contextRoot, baseDir.getAbsolutePath());
			ctx.setParentClassLoader(getClassLoader());
			LOG.info("Tomcat port="+port+", path="+contextRoot+", dir="+baseDir.getAbsolutePath());
			
			allowRemoteAddrValue(ctx);
		} catch (Exception e) {
			LOG.warn(e.getMessage(), e);
		}
	}
	
	/**
	 * Auto deployment for war files in webapps.
	 * @param serviceUrl
	 */
	protected void deployWarFiles(ServiceUrl serviceUrl) {		
		try {
			File webappsRoot = new File(getWebapps());
		    File[] warfiles = webappsRoot.listFiles(new WarFileFilter());
		    for (File war : warfiles) {
		    	String contextRoot = "/"+war.getName().replace(".war", "");
		    	//Skip already added webapp.
		    	if (tomcat.getHost().findChild(contextRoot) != null) {
		    		continue;
		    	}
				//Skip already exists extract directory.
		    	if (Files.isDirectory(Paths.get(webappsRoot.getAbsolutePath(), contextRoot))) {
		    		LOG.info("[skip] war deploy: "+war.getAbsolutePath());
		    		continue;
		    	}
		    	
		    	Context ctx = tomcat.addWebapp(contextRoot, war.getAbsolutePath());
		    	ctx.setParentClassLoader(getClassLoader());
		    	LOG.info("Tomcat port="+port+", path="+contextRoot+", war="+war.getAbsolutePath());
		    	
				allowRemoteAddrValue(ctx);
		    }

		} catch (Exception e) {
			LOG.warn(e.getMessage(), e);
		}
	}
	
	/**
	 * Denied Tomcat direct access -> HTTP Status 403 – Forbidden
	 * @param ctx
	 */
	protected void allowRemoteAddrValue(Context ctx) {
		if (StringUtils.isNotEmpty(allowRemoteAddrValve)) {
			RemoteAddrValve valve = new RemoteAddrValve();
			valve.setAllow(allowRemoteAddrValve);
			ctx.getPipeline().addValve(valve);
		}
	}
	
	public void setHostname(String hostname) {
		this.hostname = hostname;
	}

	public void setPort(int port) {
		this.port = port;
	}
	
    public void setServerHome(String serverHome) {
    	this.serverHome = serverHome;
    }
    
    protected String getServerHome() {
    	if (StringUtils.isEmpty(serverHome)) {
    		serverHome = ServerUtils.getServerHome();
    	}
    	return serverHome;
    }

	public void setWebapps(String webapps) {
		if (work.indexOf("${server.home}") >= 0) {
			this.webapps = webapps.replace("${server.home}", getServerHome()).replace("\\", "/");
		} else {
			this.webapps = webapps;
		}
	}

	public void setContextPath(String contextPath) {
		this.contextPath = contextPath;
	}
	
	protected String getWebapps() {
		return webapps;
	}

	public void setWork(String work) {
		this.work = work;
	}

	protected String getWork() {
		if (work.indexOf("${server.home}") >= 0) {
			this.work = work.replace("${server.home}", getServerHome()).replace("\\", "/");//.replaceAll("/work$", "");
		}
		return work;
	}
	
	public void setAllowRemoteAddrValve(String allowRemoteAddrValve) {
		this.allowRemoteAddrValve = allowRemoteAddrValve;
	}
	
	/**
	 * Auto Deployment for war files. (default: true)
	 * @param useWarDeploy
	 */
	public void setUseWarDeploy(String useWarDeploy) {
		this.useWarDeploy = Boolean.valueOf(useWarDeploy);
	}
	
	/**
	 * Tomcat Connector#setURIEncoding(String)
	 * default: UTF-8
	 * @see org.apache.catalina.connector.Connector#setURIEncoding(String)
	 */
	public void setUriEncoding(String uriEncoding) {
		this.uriEncoding = uriEncoding;
	}
	
	/**
	 * Tomcat Connector#seUseBodyEncodingForURI(boolean)
	 * default: false (unset/null) 
	 * @see org.apache.catalina.connector.Connector#setUseBodyEncodingForURI(boolean)
	 */
	public void seUseBodyEncodingForURI(String useBodyEncodingForURI) {
		this.useBodyEncodingForURI = Boolean.valueOf(useBodyEncodingForURI);
	}
	
	/**
	 * Tomcat Connector#setProperty("address", bindAddress)
	 * @param bindAddress default: 127.0.0.1
	 * @since 1.5-20220113
	 */
	public void setBindAddress(String bindAddress) {
		this.bindAddress = bindAddress;
	}
	
	/**
	 * FileFilter for .war file
	 */
	static class WarFileFilter implements FileFilter {
		
		@Override
		public boolean accept(File file) {
			return file.isFile() && file.getName().endsWith(".war");
		}
	}
}
