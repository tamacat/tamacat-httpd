/*
 * Copyright (c) 2009, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.handler;

import org.tamacat.httpcore4.Header;


import org.tamacat.httpcore4.HttpEntity;
import org.tamacat.httpcore4.HttpEntityEnclosingRequest;
import org.tamacat.httpcore4.HttpRequest;
import org.tamacat.httpcore4.HttpVersion;
import org.tamacat.httpcore4.ProtocolVersion;
import org.tamacat.httpcore4.protocol.HTTP;
import org.tamacat.httpcore4.protocol.HttpContext;
import org.tamacat.httpd.config.ReverseUrl;
import org.tamacat.httpd.util.RequestUtils;

/**
 * <p>The client side request for reverse proxy, including the entity.
 * (Implements {@link HttpEntityEnclosingRequest})
 */
public class ReverseHttpEntityEnclosingRequest
		extends ReverseHttpRequest implements HttpEntityEnclosingRequest {

	private HttpEntity entity;
	
	/**
	 * <p>Constructs with the original request of {@link HttpRequest}.
	 * @param request
	 * @param reverseUrl
	 */
	public ReverseHttpEntityEnclosingRequest(HttpRequest request, HttpContext context, ReverseUrl reverseUrl) {
		this(request, context, reverseUrl, HttpVersion.HTTP_1_1);
	}
	
	/**
	 * <p>Constructs with the original request of {@link HttpRequest}.
	 * @param request
	 * @param reverseUrl
	 * @param version
	 */
	public ReverseHttpEntityEnclosingRequest(HttpRequest request, HttpContext context, ReverseUrl reverseUrl, ProtocolVersion version) {
		super(request, context, reverseUrl, version);
		entity = RequestUtils.getEntity(request);
	}

	@Override
    public HttpEntity getEntity() {
        return this.entity;
    }
	
	@Override
    public void setEntity(final HttpEntity entity) {
        this.entity = entity;
    }
    
    @Override
    public boolean expectContinue() {
        Header expect = getFirstHeader(HTTP.EXPECT_DIRECTIVE);
        return expect != null && HTTP.EXPECT_CONTINUE.equalsIgnoreCase(expect.getValue());
    }
}
