/*
 * Copyright (c) 2010, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.filter;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.velocity.VelocityContext;
import org.tamacat.httpd.util.RequestUtils;
import org.tamacat.httpd.core.util.StringUtils;

/**
 * <p>The Context of Request for VelocityActionFilter.
 * (ClassicHttpRequest, ClassicHttpResponse and HttpContext)
 */
public class RequestContext {

	private final ClassicHttpRequest request;
	private final ClassicHttpResponse response;
	private final HttpContext context;
	
	public RequestContext(
			ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context) {
		this.request = request;
		this.response = response;
		this.context = context;
	}
	
	public ClassicHttpRequest getRequest() {
		return request;
	}

	public ClassicHttpResponse getResponse() {
		return response;
	}

	public HttpContext getContext() {
		return context;
	}
	
	public String getParameter(String name) {
		return RequestUtils.getParameter(context, name);
	}
	
	public <T>T getParameter(String name, T defaultValue) {
		String value = RequestUtils.getParameter(context, name);
		return StringUtils.parse(value, defaultValue);
	}
	
	public String[] getParameters(String name) {
		return RequestUtils.getParameters(context, name);
	}
	
	public void setAttribute(String name, Object value) {
		context.setAttribute(name, value);
		getVelocityContext().put(name, value);
	}
	
	public Object getAttribute(String name) {
		return context.getAttribute(name);
	}
	
	public void setVelocityContext(VelocityContext ctx) {
		context.setAttribute(VelocityContext.class.getName(), ctx);
	}
	
	public VelocityContext getVelocityContext() {
		VelocityContext ctx = (VelocityContext) context.getAttribute(
				VelocityContext.class.getName());
		return ctx != null? ctx : new VelocityContext();
	}
}
