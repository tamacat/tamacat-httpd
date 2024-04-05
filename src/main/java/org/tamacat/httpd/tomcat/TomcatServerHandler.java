/*
 * Copyright 2024 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.tomcat;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.valves.RemoteAddrValve;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.protocol.HttpContext;
import org.apache.tomcat.JarScanner;
import org.apache.tomcat.util.scan.StandardJarScanner;
import org.tamacat.httpd.config.ServiceUrl;
import org.tamacat.httpd.filter.HttpFilter;
import org.tamacat.httpd.handler.HttpHandler;
import org.tamacat.httpd.tomcat.util.ServerUtils;
import org.tamacat.log.Log;
import org.tamacat.log.LogFactory;
import org.tamacat.util.StringUtils;

/**
 * TomcatServerHandler for Tomcat Embedded without Reverse Proxy.
 */
public class TomcatServerHandler implements HttpHandler {

	static final Log LOG = LogFactory.getLog(TomcatServerHandler.class);

	protected ClassLoader loader;
	
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
	
	//JarScanner
	protected boolean scanBootstrapClassPath = false;
	protected boolean scanClassPath = true;
	protected boolean scanManifest = false;
	protected boolean scanAllDirectories = true;
	protected boolean scanAllFiles = false;

	@Override
	public void setServiceUrl(ServiceUrl serviceUrl) {		
		//deployment
		deploy(serviceUrl);
	}
	
	/**
	 * Deployment Web Applications for Tomcat Embedded
	 * @param serviceUrl
	 */
	protected void deploy(ServiceUrl serviceUrl) {
//		ReverseUrl reverseUrl = serviceUrl.getReverseUrl();
//		if (reverseUrl == null) {
//			reverseUrl = new DefaultReverseUrl(serviceUrl);
//			try {
//				reverseUrl.setReverse(new URL("http://"+hostname+":"+port+serviceUrl.getPath()));
//				serviceUrl.setReverseUrl(reverseUrl);
//			} catch (MalformedURLException e) {
//			}
//		}
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
			if (StringUtils.isEmpty(contextPath)) {
				contextPath = contextRoot;
			}
	    	//check already add webapp.
	    	if (tomcat.getHost().findChild(contextRoot) != null) {
	    		return; //skip
	    	}
			File baseDir = new File(getWebapps() + contextPath);
			Context ctx = tomcat.addWebapp(contextRoot, baseDir.getAbsolutePath());
			ctx.setParentClassLoader(getClassLoader());
			ctx.setJarScanner(createJarScanner());
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
		    	
				allowRemoteAddrValue(ctx);
		    }
		} catch (Exception e) {
			LOG.warn(e.getMessage(), e);
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
	 * FileFilter for .war file
	 */
	static class WarFileFilter implements FileFilter {
		
		@Override
		public boolean accept(File file) {
			return file.isFile() && file.getName().endsWith(".war");
		}
	}

	@Override
	public void handle(HttpRequest request, HttpResponse response, HttpContext context)
			throws HttpException, IOException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setHttpFilter(HttpFilter filter) {		
	}

	/**
	 * <p.Set the ClassLoader
	 * @param loader
	 */
	public void setClassLoader(ClassLoader loader) {
		this.loader = loader;
	}

	/**
	 * <p>Get the ClassLoader, default is getClass().getClassLoader().
	 * @return
	 */
	public ClassLoader getClassLoader() {
		return loader != null ? loader : getClass().getClassLoader();
	}
}
