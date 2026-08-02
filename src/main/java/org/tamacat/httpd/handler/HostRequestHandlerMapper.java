/*
 * Copyright (c) 2010, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.handler;

import java.util.HashMap;

import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestMapper;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.tamacat.httpd.config.HostServiceConfig;
import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.httpd.config.ServiceConfig;
import org.tamacat.httpd.config.ServiceConfigParser;
import org.tamacat.httpd.config.ServiceUrl;
import org.tamacat.httpd.util.RequestUtils;
import org.tamacat.log.Log;
import org.tamacat.log.LogFactory;

/**
 * <p>The {@link HttpRequestMapper} for a virtual host.<br>
 * With this HttpRequestMapper, I acquire virtual host setting based on
 * a Host request header and return a supporting {@link HttpRequestHandler}.
 */
public class HostRequestHandlerMapper implements HttpRequestMapper<HttpRequestHandler> {
	static final Log LOG = LogFactory.getLog(HostRequestHandlerMapper.class);

	/** default key for empty host.*/
	static final String DEFAULT_HOST = "default";

	private HashMap<String, HttpRequestMapper<HttpRequestHandler>> hostHandler = new HashMap<>();

	private boolean useVirtualHost = false;

	public HostRequestHandlerMapper create(
			ServerConfig serverConfig, String componentsXML) {
		HttpHandlerFactory factory = new DefaultHttpHandlerFactory(
				componentsXML, getClass().getClassLoader());

		HostServiceConfig hostConfig = new ServiceConfigParser(serverConfig).getConfig();
		for (String host : hostConfig.getHosts()) {
			UriHttpRequestHandlerMapper mapper = new UriHttpRequestHandlerMapper();
			ServiceConfig serviceConfig = hostConfig.getServiceConfig(host);
			for (ServiceUrl serviceUrl : serviceConfig.getServiceUrlList()) {
				HttpHandler handler = factory.getHttpHandler(serviceUrl);
				if (handler != null) {
					LOG.info(serviceUrl.getServerConfig().getPort() + ":" + serviceUrl.getPath() + " - " + serviceUrl.getHandlerName()
						+ " (class="+handler.getClass().getName() + ")");
					mapper.register(serviceUrl.getPath() + "*", handler);
				} else {
					LOG.warn(serviceUrl.getPath() + " HttpHandler is not found.");
				}
			}
			this.setHostRequestHandlerMapper(host, mapper);
		}
		return this;
	}

	/**
	 * <p>Set the Host and {@link HttpRequestMapper}.
	 * @param host parameter is null then set the default {@link HttpRequestMapper}.
	 * @param mapper
	 */
	public void setHostRequestHandlerMapper(String host, HttpRequestMapper<HttpRequestHandler> mapper) {
		if (host == null) {
			host = DEFAULT_HOST;
		}
		if (useVirtualHost == false && hostHandler.size() >= 1) {
			useVirtualHost = true;
		}
		if (host.equals(DEFAULT_HOST) == false) {
			LOG.info("add virtual host: " + host + "=" + mapper.getClass().getName());
		}
		hostHandler.put(host.replaceAll("http://", "").replaceAll("https://", ""), mapper);
	}

	/**
	 * <p>Resolve the HttpRequestHandler for Host request header.
	 * @param request
	 * @param context
	 * @return HttpRequestHandler or {@code null} if no match is found.
	 */
	@Override
	public HttpRequestHandler resolve(HttpRequest request, HttpContext context) throws HttpException {
		return lookup(request, context);
	}

	/**
	 * <p>Lookup the HttpRequestHandler for Host request header.
	 * @param request
	 * @param context
	 * @return HttpRequestHandler
	 */
	public HttpRequestHandler lookup(HttpRequest request, HttpContext context) throws HttpException {
		HttpRequestMapper<HttpRequestHandler> mapper = null;
		if (useVirtualHost) {
			String host = RequestUtils.getRequestHost(request, context);
			if (host == null) {
				host = DEFAULT_HOST;
			}
			mapper = hostHandler.get(host);
		}
		if (mapper == null) {
			mapper = hostHandler.get(DEFAULT_HOST);
		}
		if (LOG.isTraceEnabled() && mapper != null) {
			LOG.trace("handler: " + mapper.getClass().getName());
		}
		HttpRequestHandler handler = null;
		if (mapper != null) {
			handler = mapper.resolve(request, context);
		}
		return handler;
	}
}
