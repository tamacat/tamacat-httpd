/*
 * Copyright (c) 2013, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.filter;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
//core5 keeps RequestLine / StatusLine as standalone value objects built from the
//message; they are no longer accessors on the message itself (R-5.4, 15.8).
import org.apache.hc.core5.http.message.RequestLine;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.tamacat.httpd.config.ServiceUrl;
import org.tamacat.log.Log;
import org.tamacat.log.LogFactory;

public class HeaderLoggingFilter implements RequestFilter, ResponseFilter {

	static final Log LOG = LogFactory.getLog("org.tamacat.httpd.debug.Header");

	@Override
	public void init(ServiceUrl serviceUrl) {}

	@Override
	public void doFilter(ClassicHttpRequest request, ClassicHttpResponse response,
			HttpContext context) {
		LOG.info("[request] " + new RequestLine(request));
		if (LOG.isDebugEnabled()) {
			for (Header h : request.getHeaders()) {
				LOG.debug("[request] " + h);
			}
		}
	}

	@Override
	public void afterResponse(ClassicHttpRequest request, ClassicHttpResponse response,
			HttpContext context) {
		LOG.info("[response] " + new StatusLine(response));
		if (LOG.isDebugEnabled()) {
			for (Header h : response.getHeaders()) {
				LOG.debug("[response] " + h);
			}
		}
	}
}
