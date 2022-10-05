/*
 * Copyright (c) 2022 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.handler;

import java.io.IOException;
import java.net.URL;

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.protocol.HttpContext;
import org.tamacat.httpd.config.ReverseUrl;
import org.tamacat.httpd.core.BasicHttpStatus;
import org.tamacat.httpd.core.HttpStatus;
import org.tamacat.httpd.exception.HttpException;
import org.tamacat.httpd.exception.ServiceUnavailableException;
import org.tamacat.httpd.util.RequestUtils;

/**
 * Redirect http handler. (entends ReverseProxyHandler)
 * 
 * Location: reverse URL (http://localhost:8080/examples/path?param=value)
 * HTTP/1.1 302 [Found]
 * 
 * @since 1.5-20221005
 */
public class RedirectHttpHandler extends ReverseProxyHandler {

	protected HttpStatus httpStatus = BasicHttpStatus.SC_FOUND;

	/**
	 * Set a status code. default 302 (Found)
	 * @param statusCode
	 */
	public void setStatusCode(int statusCode) {
		this.httpStatus = BasicHttpStatus.getHttpStatus(statusCode);
	}
	
	@Override
	public void doRequest(HttpRequest request, HttpResponse response,
			HttpContext context) throws HttpException, IOException {
		if (LOG.isDebugEnabled()) {
			LOG.debug(">> " + RequestUtils.getRequestLine(request));
		}
		
		ReverseUrl reverseUrl = getReverseUrl(context);
		if (reverseUrl == null) {
			throw new ServiceUnavailableException("reverseUrl is null.");
		}
		try {
			URL url = reverseUrl.getReverseUrl(request.getRequestLine().getUri());
			LOG.debug("redirect: "+url.toString());
			response.setHeader("Location", url.toString());
			response.setStatusCode(httpStatus.getStatusCode());
			response.setReasonPhrase(httpStatus.getReasonPhrase());
		} catch (Exception e) {
			handleException(request, response, e);
		}
	}
}
