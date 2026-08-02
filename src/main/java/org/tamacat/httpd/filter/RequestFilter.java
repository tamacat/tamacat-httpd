/*
 * Copyright (c) 2009, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.filter;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * <p>{@code RequestFilter} is execute from
 * {@link HttpRequestHandler#handle} method.
 */
public interface RequestFilter extends HttpFilter {
	
	String SKIP_REQUEST_FILTER_KEY = "org.tamacat.httpd.filter.RequestFilter.SkipFilter";
	
	/**
	 * This method is performed before a request. 
	 * @param request
	 * @param response
	 * @param context
	 */
	void doFilter(ClassicHttpRequest request, ClassicHttpResponse response, 
		HttpContext context);
}
