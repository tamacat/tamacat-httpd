/*
 * Copyright (c) 2009, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.handler;

import java.net.URL;

import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.tamacat.httpd.config.ReverseUrl;
import org.tamacat.httpd.exception.NotFoundException;
import org.tamacat.httpd.util.HeaderUtils;
import org.tamacat.httpd.util.RequestUtils;
import org.tamacat.httpd.util.ReverseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tamacat.httpd.core.util.StringUtils;

/**
 * <p>The client side request for reverse proxy.
 *
 * <p>Up to 1.5 a request that carried an entity used the separate
 * {@code ReverseHttpEntityEnclosingRequest} subclass, because httpcore 4.x split the
 * request contract into {@code HttpRequest} and {@code HttpEntityEnclosingRequest}.
 * HttpComponents Core 5.x folds the two together
 * ({@code ClassicHttpRequest extends HttpRequest, HttpEntityContainer}), so the entity
 * is carried by this class for every request and the subclass is gone (ADR-007).
 */
public class ReverseHttpRequest extends BasicClassicHttpRequest {
	private static final long serialVersionUID = 1L;

	private static final Logger LOG = LoggerFactory.getLogger(ReverseHttpRequest.class);

	protected ReverseUrl reverseUrl;
	protected URL url;
	protected ProtocolVersion forceHttpVersion;

	/**
	 * <p>Constructs with the original request of {@link ClassicHttpRequest}.
	 * force HTTP/1.1
	 * @param request
	 * @param reverseUrl
	 */
	public ReverseHttpRequest(ClassicHttpRequest request, HttpContext context, ReverseUrl reverseUrl) {
		this(request, context, reverseUrl, HttpVersion.HTTP_1_1);
	}
	
	/**
	 * <p>Constructs with the original request of {@link ClassicHttpRequest}.
	 * @param request
	 * @param reverseUrl
	 * @param forceHttpVersion
	 */
	public ReverseHttpRequest(ClassicHttpRequest request, HttpContext context, ReverseUrl reverseUrl, ProtocolVersion forceHttpVersion) {
		super(request.getMethod(), reverseUrl.getReverseUrl(request.getRequestUri()).getFile());
		url = reverseUrl.getReverseUrl(request.getRequestUri());
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
	public void setRequest(ClassicHttpRequest request, HttpContext context) {
		appendHostHeader(request);

		rewriteHostHeader(request, context);

		setHeaders(request.getHeaders());
		//The entity used to be carried by the ReverseHttpEntityEnclosingRequest subclass.
		//core5 puts it on ClassicHttpRequest itself, so it is copied here (ADR-007).
		setEntity(request.getEntity());
		ReverseUtils.removeRequestHeaders(this);
	}

	protected void appendHostHeader(ClassicHttpRequest request) {
		if (forceHttpVersion.greaterEquals(HttpVersion.HTTP_1_1)
		 && RequestUtils.getVersion(request).lessEquals(HttpVersion.HTTP_1_0)
		 && StringUtils.isEmpty(HeaderUtils.getHeader(request, HttpHeaders.HOST))) {
			request.setHeader(HttpHeaders.HOST, reverseUrl.getTargetHost().getHostName());
			LOG.debug("Host(add): "+HeaderUtils.getHeader(request, HttpHeaders.HOST));
		}
	}
	
	//rewrite Host Header
	protected void rewriteHostHeader(ClassicHttpRequest request, HttpContext context) {
		Header[] hostHeaders = request.getHeaders(HttpHeaders.HOST);
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
