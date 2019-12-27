/*
 * Copyright (c) 2019, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.handler.page;

import java.util.Locale;
import java.util.Properties;

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.tamacat.httpd.exception.HttpException;
import org.tamacat.log.Log;
import org.tamacat.log.LogFactory;
import org.thymeleaf.context.Context;

/**
 * <p>It is the HTTP error page that used Velocity template.
 */
public class ThymeleafErrorPage extends ThymeleafPage {

	static final Log LOG = LogFactory.getLog(ThymeleafErrorPage.class);

	static final String DEFAULT_CONTENT_TYPE = "text/html; charset=UTF-8";

	static final String DEFAULT_ERROR_HTML
		= "<html><body><p>Error.</p></body></html>";

	public ThymeleafErrorPage() {
    }
	
	public ThymeleafErrorPage(Properties props) {
	    init(props, null);
	}

	public String getErrorPage(HttpRequest request, HttpResponse response, HttpException exception) {
		return getErrorPage(request, response, new Context(), exception);
	}

	public String getErrorPage(HttpRequest request, HttpResponse response, Context context, HttpException exception) {
		response.setStatusCode(exception.getHttpStatus().getStatusCode());
		response.setReasonPhrase(exception.getHttpStatus().getReasonPhrase());

		if (LOG.isTraceEnabled() && exception.getHttpStatus().isServerError()) {
			LOG.trace(exception); //exception.printStackTrace();
		}
		
        context.setVariable("url", request.getRequestLine().getUri());
        context.setVariable("method", request.getRequestLine().getMethod().toUpperCase(Locale.ENGLISH));
        context.setVariable("exception", exception);
        
		try {
		    return getTemplatePage(request, response, context, "/error"+exception.getHttpStatus().getStatusCode());
		} catch (Exception e) {
		    return getDefaultErrorPage(request, response, context, exception);
		}
	}

	protected String getDefaultErrorPage(HttpRequest request, HttpResponse response, Context context, HttpException exception) {
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
