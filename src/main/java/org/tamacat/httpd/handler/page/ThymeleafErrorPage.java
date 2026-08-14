/*
 * Copyright (c) 2019, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.handler.page;

import java.util.Locale;
import java.util.Properties;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.tamacat.httpd.exception.HttpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.context.Context;

/**
 * <p>It is the HTTP error page that used Velocity template.
 */
public class ThymeleafErrorPage extends ThymeleafPage {

	private static final Logger LOG = LoggerFactory.getLogger(ThymeleafErrorPage.class);

	static final String DEFAULT_CONTENT_TYPE = "text/html; charset=UTF-8";

	static final String DEFAULT_ERROR_HTML
		= "<html><body><p>Error.</p></body></html>";

	public ThymeleafErrorPage() {
    }
	
	public ThymeleafErrorPage(Properties props) {
	    init(props, null);
	}

	public String getErrorPage(ClassicHttpRequest request, ClassicHttpResponse response, HttpException exception) {
		return getErrorPage(request, response, new Context(), exception);
	}

	public String getErrorPage(ClassicHttpRequest request, ClassicHttpResponse response, Context context, HttpException exception) {
		try {
			response.setCode(exception.getHttpStatus().getStatusCode());
			response.setReasonPhrase(exception.getHttpStatus().getReasonPhrase());
	
			if (LOG.isTraceEnabled() && exception.getHttpStatus().isServerError()) {
				LOG.trace(exception.toString()); //exception.printStackTrace();
			}
			
	        context.setVariable("url", request.getRequestUri());
	        context.setVariable("method", request.getMethod().toUpperCase(Locale.ENGLISH));
	        context.setVariable("exception", exception);
		    return getTemplatePage(request, response, context, "/error"+exception.getHttpStatus().getStatusCode());
		} catch (Exception e) {
		    return getDefaultErrorPage(request, response, context, exception);
		}
	}

	protected String getDefaultErrorPage(ClassicHttpRequest request, ClassicHttpResponse response, Context context, HttpException exception) {
	    try {
	        return getTemplatePage(request, response, context, "/error");
	    } catch (Exception e) {
	        return getDefaultErrorHtml(exception);
	    }
	}
	
	protected String getDefaultErrorHtml(HttpException exception) {
		String errorMessage = exception.getHttpStatus().getStatusCode()
				+ " " + exception.getHttpStatus().getReasonPhrase();
		StringBuilder html = new StringBuilder();
		html.append("<!DOCTYPE html>");
		html.append("<html><head><meta charset=\"UTF-8\" />");
		html.append("<title>" + errorMessage + "</title>");
		html.append("</head><body>");
		html.append("<h1>" + errorMessage + "</h1>");
		html.append("</body></html>");
		return html.toString();
	}
}
