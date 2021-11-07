/*
 * Copyright (c) 2009, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.handler;

import java.net.URL;

import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolVersion;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.tamacat.httpd.config.ReverseUrl;
import org.tamacat.httpd.exception.NotFoundException;
import org.tamacat.httpd.util.HeaderUtils;
import org.tamacat.httpd.util.RequestUtils;
import org.tamacat.httpd.util.ReverseUtils;
import org.tamacat.log.Log;
import org.tamacat.log.LogFactory;
import org.tamacat.util.StringUtils;

/**
 * <p>The client side request for reverse proxy.<br>
 * The case including the entity uses {@link ReverseHttpEntityEnclosingRequest}
 * for a request.
 */
public class ReverseHttpRequest extends BasicHttpRequest {
	static final Log LOG = LogFactory.getLog(ReverseHttpRequest.class);

	protected ReverseUrl reverseUrl;
	protected URL url;
	protected ProtocolVersion forceHttpVersion;

	/**
	 * <p>Constructs with the original request of {@link HttpRequest}.
	 * force HTTP/1.1
	 * @param request
	 * @param reverseUrl
	 */
	public ReverseHttpRequest(HttpRequest request, HttpContext context, ReverseUrl reverseUrl) {
		this(request, context, reverseUrl, HttpVersion.HTTP_1_1);
	}
	
	/**
	 * <p>Constructs with the original request of {@link HttpRequest}.
	 * @param request
	 * @param reverseUrl
	 * @param forceHttpVersion
	 */
	public ReverseHttpRequest(HttpRequest request, HttpContext context, ReverseUrl reverseUrl, ProtocolVersion forceHttpVersion) {
		super(request.getRequestLine().getMethod(), reverseUrl.getReverseUrl(request.getRequestLine().getUri()).getFile());
		url = reverseUrl.getReverseUrl(request.getRequestLine().getUri());
		if (url == null) {
			throw new NotFoundException("url is null.");
		}
		this.reverseUrl = reverseUrl;
		this.forceHttpVersion = forceHttpVersion;
		
		setRequest(request, context);
	}
	
	public URL getURL() {
		return url;
	}
    
	/**
	 * <p>Set the original request.
	 * @param request
	 */
	public void setRequest(HttpRequest request, HttpContext context) {
		appendHostHeader(request);

		rewriteHostHeader(request, context);

		setHeaders(request.getAllHeaders());
		//setParams(request.getParams());
		ReverseUtils.removeRequestHeaders(this);
	}

	protected void appendHostHeader(HttpRequest request) {
		if (forceHttpVersion.greaterEquals(HttpVersion.HTTP_1_1)
		 && request.getProtocolVersion().lessEquals(HttpVersion.HTTP_1_0)
		 && StringUtils.isEmpty(HeaderUtils.getHeader(request, HTTP.TARGET_HOST))) {
			request.setHeader(HTTP.TARGET_HOST, reverseUrl.getTargetHost().getHostName());
			LOG.debug("Host(add): "+HeaderUtils.getHeader(request, HTTP.TARGET_HOST));
		}
	}
	
	//rewrite Host Header
	protected void rewriteHostHeader(HttpRequest request, HttpContext context) {
		Header[] hostHeaders = request.getHeaders(HTTP.TARGET_HOST);
		for (Header hostHeader : hostHeaders) {
			String value = hostHeader.getValue();
			URL host = RequestUtils.getRequestURL(request, context, reverseUrl.getServiceUrl());
			reverseUrl.setHost(host);
			String before = host.getAuthority();
			int beforePort = host.getPort();
			if (beforePort != 80 && beforePort > 0) {
				before = before + ":" + beforePort;
			}
			String after = reverseUrl.getReverse().getHost();
			int afterPort = reverseUrl.getReverse().getPort();
			if (afterPort != 80 && afterPort > 0) {
				after = after + ":" + afterPort;
			}
			String newValue = value.replace(before, after);

			LOG.trace("Host: " + value + " >> " + newValue);
			Header newHeader = new BasicHeader(hostHeader.getName(), newValue);
			request.removeHeader(hostHeader);
			request.addHeader(newHeader);
		}
	}
}
