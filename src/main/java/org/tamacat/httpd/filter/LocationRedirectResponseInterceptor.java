/*
 * Copyright 2019 tamacat.org
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package org.tamacat.httpd.filter;

import java.io.IOException;

import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.tamacat.httpd.util.HeaderUtils;
import org.tamacat.httpd.core.util.StringUtils;

public class LocationRedirectResponseInterceptor implements HttpResponseInterceptor {
	
	protected static final String LAST_REDIRECT_URL ="ReverseProxyHandler.LAST_REDIRECT_URL";

	@Override
	public void process(HttpResponse response, EntityDetails entity, HttpContext context) throws HttpException, IOException {
		if (response.containsHeader("Location")) {
			String location = HeaderUtils.getHeader(response, "Location");
			if (StringUtils.isNotEmpty(location)) {
				context.setAttribute(LAST_REDIRECT_URL, location);
			}
		}
	}
	
	public static void checkRedirect(HttpResponse response, HttpContext context) {
		Object location = context.getAttribute(LAST_REDIRECT_URL);
		if (location != null && location instanceof String) {
			response.setCode(302);
			response.addHeader("Location", (String)location);
		}
	}
}
