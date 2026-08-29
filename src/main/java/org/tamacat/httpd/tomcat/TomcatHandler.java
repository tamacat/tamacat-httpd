/*
 * Copyright 2012 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.tomcat;

import java.io.File;
import java.io.FileFilter;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.valves.RemoteCIDRValve;
import org.apache.tomcat.JarScanner;
import org.apache.tomcat.util.scan.StandardJarScanner;
import org.tamacat.httpd.config.DefaultReverseUrl;
import org.tamacat.httpd.config.ReverseUrl;
import org.tamacat.httpd.config.ServiceUrl;
import org.tamacat.httpd.handler.ReverseProxyHandler;
import org.tamacat.httpd.tomcat.util.ServerUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tamacat.httpd.core.util.StringUtils;

/**
 * The reverse proxy handler using the Tomcat Embedded.
 */
public class TomcatHandler extends ReverseProxyHandler {

	private static final Logger LOG = LoggerFactory.getLogger(TomcatHandler.class);

	protected String serverHome;
	protected String hostname = "127.0.0.1";
	protected String bindAddress = "127.0.0.1";
	protected int port = 8080;
	protected String allowRemoteAddrValve;
	protected String webapps = "./webapps";
	protected String contextPath;
	protected String work = "${server.home}";
	protected Tomcat tomcat;
	protected boolean useWarDeploy = false; //@since v1.5.1 changes default false
	protected String uriEncoding;
	protected Boolean useBodyEncodingForURI;
	protected String maxHttpRequestHeaderSize;
	
	//JarScanner
	protected boolean scanBootstrapClassPath = false;
	protected boolean scanClassPath = true;
	protected boolean scanManifest = false;
	protected boolean scanAllDirectories = true;
	protected boolean scanAllFiles = false;

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
				reverseUrl.setReverse(new URI("http://"+hostname+":"+port+serviceUrl.getPath()).toURL());
				serviceUrl.setReverseUrl(reverseUrl);
			} catch (URISyntaxException | MalformedURLException e) {
			}
		}
		tomcat = TomcatManager.getInstance(port);
		tomcat.setBaseDir(getWork());
		//tomcat.getServer().getCatalina().setParentClassLoader(getClassLoader());

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
		if (maxHttpRequestHeaderSize != null) {
			tomcat.getConnector().setProperty("maxHttpRequestHeaderSize", maxHttpRequestHeaderSize);
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
		Context ctx;
		try {	    	
			String path = serviceUrl.getPath().replaceAll("/$", "");
			String contextPath = this.contextPath;
			if (StringUtils.isEmpty(contextPath)) {
				contextPath = path;
			}
	    	//check already add webapp.
	    	if (tomcat.getHost().findChild(path) != null) {
	    		return; //skip
	    	}
			File baseDir = new File(getWebapps() + contextPath);
			ctx = tomcat.addWebapp(path, baseDir.getAbsolutePath());
			ctx.setParentClassLoader(getClassLoader());
			ctx.setJarScanner(createJarScanner());
			LOG.info("Tomcat port="+port+", path="+path+", dir="+baseDir.getAbsolutePath());
		} catch (Exception e) {
			//Deployment failures stay non-fatal: warn and let the server start.
			//No Context was produced, so there is nothing to filter.
			LOG.warn(e.getMessage(), e);
			return;
		}
		//Deliberately outside the catch: failing to apply the access filter must
		//not be swallowed, or the webapp would serve traffic unprotected.
		applyRemoteAddrFilter(ctx);
	}
	
	/**
	 * Auto deployment for war files in webapps.
	 * @param serviceUrl
	 */
	protected void deployWarFiles(ServiceUrl serviceUrl) {		
		List<Context> deployed = new ArrayList<Context>();
		//Pass 1: deploy every war found. Failures here stay non-fatal and abort
		//the loop with a warning, exactly as before.
		try {
			File webappsRoot = new File(getWebapps());
		    File[] warfiles = webappsRoot.listFiles(new WarFileFilter());
		    if (warfiles == null) {
		    	return;
		    }
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
				ctx.setJarScanner(createJarScanner());
		    	LOG.info("Tomcat port="+port+", path="+contextRoot+", war="+war.getAbsolutePath());
		    	
				deployed.add(ctx);
		    }
		} catch (Exception e) {
			LOG.warn(e.getMessage(), e);
		}
		//Pass 2: deliberately outside the catch. The first failure propagates and
		//stops the server from starting, so no war is left serving unprotected.
		for (Context ctx : deployed) {
			applyRemoteAddrFilter(ctx);
		}
	}
	
	/**
	 * Create new JarScanner instance.
	 * @return StandardJarScanner
	 */
	protected JarScanner createJarScanner() {
		StandardJarScanner scanner = new StandardJarScanner();
		scanner.setScanBootstrapClassPath(scanBootstrapClassPath);
		scanner.setScanClassPath(scanClassPath);
		scanner.setScanManifest(scanManifest);
		scanner.setScanAllDirectories(scanAllDirectories);
		scanner.setScanAllFiles(scanAllFiles);
		LOG.debug("create new StandardJarScanner() [scanBootstrapClassPath="+scanBootstrapClassPath
				+", scanClassPath="+scanClassPath+", scanManifest="+scanManifest
				+", scanAllDiredtories="+scanAllDirectories+", scanAllFiles="+scanAllFiles
				+"]");
		return scanner;
	}
	
	/**
	 * Denied Tomcat direct access -> HTTP Status 403 – Forbidden
	 * <p>The value of {@code allowRemoteAddrValve} is a comma separated list of
	 * netmasks (for example {@code 127.0.0.1} or {@code 192.168.0.0/16}), not a
	 * regular expression. An unusable value raises IllegalArgumentException,
	 * which is intentionally left to propagate: a filter that cannot be applied
	 * must stop the server rather than silently leave the webapp open.
	 * @param ctx
	 */
	protected void applyRemoteAddrFilter(Context ctx) {
		if (StringUtils.isNotEmpty(allowRemoteAddrValve)) {
			RemoteCIDRValve valve = new RemoteCIDRValve();
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
	public void setUseWarDeploy(boolean useWarDeploy) {
		this.useWarDeploy = useWarDeploy;
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
	 * Tomcat Connector#seUseBodyEncodingForURI(boolean)
	 * default: false (unset/null) 
	 * @see org.apache.catalina.connector.Connector#setUseBodyEncodingForURI(boolean)
	 */
	public void seUseBodyEncodingForURI(boolean useBodyEncodingForURI) {
		this.useBodyEncodingForURI = useBodyEncodingForURI;
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
	 * Tomcat Context StandardJarScanner#setScanBootstrapClassPath(boolean)
	 * Controls the testing of the bootstrap classpath which consists of the
     * runtime classes provided by the JVM and any installed system extensions.
	 * @param scanBootstrapClassPath default: false
	 * @since 1.5-20220128
	 */
	public void setScanBootstrapClassPath(boolean scanBootstrapClassPath) {
		this.scanBootstrapClassPath = scanBootstrapClassPath;
	}
	
	/**
	 * Tomcat Context StandardJarScanner#setScanClassPath(boolean)
	 * Controls the classpath scanning extension.
	 * @param scanClassPath default: true
	 * @since 1.5-20220128
	 */
	public void setScanClassPath(boolean scanClassPath) {
		this.scanClassPath = scanClassPath;
	}
	
	/**
	 * Tomcat Context StandardJarScanner#setScanManifest(boolean)
	 * Controls the JAR file Manifest scanning extension.
	 * @param scanManifest default: false
	 * @since 1.5-20220128
	 */
    public void setScanManifest(boolean scanManifest) {
		this.scanManifest = scanManifest;
    }
	
    /**
     * Tomcat Context StandardJarScanner#setScanAllDirectories(boolean)
     * Controls the testing all directories to see of they are exploded JAR
     * files extension.
     * @param scanAllDirectories default: true
     */
    public void setScanAllDirectories(boolean scanAllDirectories) {
        this.scanAllDirectories = scanAllDirectories;
    }
    
    /**
     * Tomcat Context JarScanner#setScanAllFiles(boolean)
     * Controls the testing all files to see of they are JAR files extension.
     * @param scanAllFiles default: false
     */
    public void setScanAllFiles(boolean scanAllFiles) {
        this.scanAllFiles = scanAllFiles;
    }
    
    /**
     * Tomcat Connector attributes: maxHttpRequestHeaderSize
     * The maximum permitted size of the request line and headers associated with an HTTP request, specified in bytes. This is compared to the number of bytes received so includes line terminators and whitespace as well as the request line, header names and header values. If not specified, this attribute is set to the value of the maxHttpHeaderSize attribute.
     * If you see "Request header is too large" errors you can increase this, but be aware that Tomcat will allocate the full amount you specify for every request. For example, if you specify a maxHttpRequestHeaderSize of 1 MB and your application handles 100 concurrent requests, you will see 100 MB of heap consumed by request headers.
     * @see https://tomcat.apache.org/tomcat-9.0-doc/config/http.html
     * @param maxHttpRequestHeaderSize default 8192 (bytes)
     * @since 1.5.1-b20250227
     */
    public void setMaxHttpRequestHeaderSize(String maxHttpRequestHeaderSize) {
    	this.maxHttpRequestHeaderSize = maxHttpRequestHeaderSize;
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
