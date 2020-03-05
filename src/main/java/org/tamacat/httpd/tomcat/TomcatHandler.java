/*
 * Copyright (c) 2012 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.tomcat;

import java.io.File;
import java.net.URL;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.valves.RemoteAddrValve;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.protocol.HttpContext;
import org.tamacat.httpd.config.DefaultReverseUrl;
import org.tamacat.httpd.config.ReverseUrl;
import org.tamacat.httpd.config.ServiceUrl;
import org.tamacat.httpd.handler.ReverseProxyHandler;
import org.tamacat.httpd.tomcat.util.ServerUtils;
import org.tamacat.log.Log;
import org.tamacat.log.LogFactory;
import org.tamacat.util.StringUtils;

/**
 * The reverse proxy handler using the embedded Tomcat.
 */
public class TomcatHandler extends ReverseProxyHandler {

	static final Log LOG = LogFactory.getLog(TomcatHandler.class);

	protected String serverHome;
	protected String hostname = "127.0.0.1";
	protected int port = 8080;
	protected String allowRemoteAddrValve;
	protected String webapps = "./webapps";
	protected String contextPath;
	protected String work = "${server.home}";
	protected Tomcat tomcat;
	
	@Override
	public void handle(HttpRequest request, HttpResponse response, HttpContext context) {
		super.handle(request, response, context);
	}

	@Override
	public void setServiceUrl(ServiceUrl serviceUrl) {
		super.setServiceUrl(serviceUrl);
		ReverseUrl reverseUrl = serviceUrl.getReverseUrl();
		try {
			if (reverseUrl == null) {
				reverseUrl = new DefaultReverseUrl(serviceUrl);
				reverseUrl.setReverse(new URL("http://"+hostname+":"+port+serviceUrl.getPath()));
				serviceUrl.setReverseUrl(reverseUrl);
			}

			tomcat = TomcatManager.getInstance(port);
			tomcat.setBaseDir(getWork());
			
			String contextRoot = getWebapps() + serviceUrl.getPath();
			if (StringUtils.isNotEmpty(contextPath)) {
				contextRoot = getWebapps() + contextPath;
			}
			LOG.info("tomcat-embeded port="+port+", path="+serviceUrl.getPath()+", contextRoot="+contextRoot);
			// ProtectionDomain domain = TomcatHandler.class.getProtectionDomain();
			// URL location = domain.getCodeSource().getLocation();
			String baseDir = new File(contextRoot).getAbsolutePath();
			Context ctx = tomcat.addWebapp(serviceUrl.getPath().replaceAll("/$", ""), baseDir);
			ctx.setParentClassLoader(getClassLoader());
			
			//Denied Tomcat direct access -> HTTP Status 403 – Forbidden
			if (StringUtils.isNotEmpty(allowRemoteAddrValve)) {
				RemoteAddrValve valve = new RemoteAddrValve();
				valve.setAllow(allowRemoteAddrValve);
				ctx.getPipeline().addValve(valve);
			}
		} catch (Exception e) {
			LOG.warn(e.getMessage(), e);
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
}
