/*
 * Copyright (c) 2010, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.filter;

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.protocol.HttpContext;
import org.tamacat.httpd.config.ServiceUrl;
import org.tamacat.httpd.util.AccessLogUtils;

/**
 * This filter is logging access and server side response time.
 */
public class AccessLogFilter implements RequestFilter, ResponseFilter {

	protected static final String START_TIME = AccessLogFilter.class.getName() + "_Response.startTime";
	protected static final String RESPONSE_TIME = AccessLogFilter.class.getName() + "_Response.responseTime";
	protected ServiceUrl serviceUrl;
	
	protected boolean faviconLogging;
	
	protected boolean useForwardHeader;
	protected String forwardHeader = "X-Forwarded-For";
	
	/**
	 * Logging "/favicon.ico" to access log.
	 * default false (not logging)
	 * @param faviconLogging
	 */
	public void setFaviconLogging(boolean faviconLogging) {
		this.faviconLogging = faviconLogging;
	}

	@Override
	public void doFilter(HttpRequest request, HttpResponse response,
			HttpContext context) {
		if (faviconLogging == false && "/favicon.ico".equals(request.getRequestLine().getUri()) == false) {
			long start = System.currentTimeMillis();
			context.setAttribute(START_TIME, start);
		}
	}

	@Override
	public void init(ServiceUrl serviceUrl) {
		this.serviceUrl = serviceUrl;
	}

	@Override
	public void afterResponse(HttpRequest request, HttpResponse response,
			HttpContext context) {
		Long start = (Long)context.getAttribute(START_TIME);
		if (start != null) {
			long time = System.currentTimeMillis() - start;
			context.setAttribute(RESPONSE_TIME, time);
			AccessLogUtils.writeAccessLog(request, response, context, time, useForwardHeader ? forwardHeader: null);
		} else if (response.getStatusLine().getStatusCode() > 200) {
			AccessLogUtils.writeAccessLog(request, response, context, -1, useForwardHeader ? forwardHeader: null);
		}
	}
	
	/**
	 * Get a remote IP address using X-Forwarded-For request header.
	 * @since 1.4
	 * @param forwardHeader
	 */
	public void setUseForwardHeader(boolean forwardHeader) {
		this.useForwardHeader = forwardHeader;
	}

	/**
	 * Set a request header name for Forwarded IP address.
	 * @since 1.4
	 * @param forwardHeader
	 */
	public void setForwardHeader(String forwardHeader) {
		this.forwardHeader = forwardHeader;
	}
}
