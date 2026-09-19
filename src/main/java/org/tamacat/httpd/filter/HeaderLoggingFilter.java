/*
 * Copyright (c) 2013, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.filter;

import org.tamacat.httpcore4.Header;
import org.tamacat.httpcore4.HttpRequest;
import org.tamacat.httpcore4.HttpResponse;
import org.tamacat.httpcore4.protocol.HttpContext;
import org.tamacat.httpd.config.ServiceUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HeaderLoggingFilter implements RequestFilter, ResponseFilter {

	static final Logger LOG = LoggerFactory.getLogger("org.tamacat.httpd.debug.Header");

	@Override
	public void init(ServiceUrl serviceUrl) {}

	@Override
	public void doFilter(HttpRequest request, HttpResponse response,
			HttpContext context) {
		LOG.info("[request] " + request.getRequestLine());
		if (LOG.isDebugEnabled()) {
			for (Header h : request.getAllHeaders()) {
				LOG.debug("[request] " + h);
			}
		}
	}

	@Override
	public void afterResponse(HttpRequest request, HttpResponse response,
			HttpContext context) {
		LOG.info("[response] " + response.getStatusLine());
		if (LOG.isDebugEnabled()) {
			for (Header h : response.getAllHeaders()) {
				LOG.debug("[response] " + h);
			}
		}
	}
}
