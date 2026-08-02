/*
 * Copyright (c) 2010, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.filter;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * <p>{@code ResponseFilter} performed at the end of a {@link HttpRequestHandler#handle} method.
 */
public interface ResponseFilter extends HttpFilter {
	
	String SKIP_RESPONSE_FILTER_KEY = "org.tamacat.httpd.filter.ResponseFilter.SkipFilter";
	
	/**
	 * This method is performed after a response. 
	 * @param request
	 * @param response
	 * @param context
	 */
	void afterResponse(ClassicHttpRequest request, ClassicHttpResponse response, 
		HttpContext context);
}
