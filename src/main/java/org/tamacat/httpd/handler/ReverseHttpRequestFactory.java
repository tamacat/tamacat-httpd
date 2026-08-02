/*
 * Copyright (c) 2013, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.handler;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.tamacat.httpd.config.ReverseUrl;

/**
 * <p>Factory of {@link ReverseHttpRequest}.
 *
 * <p>Up to 1.5 this factory chose between {@code ReverseHttpRequest} and
 * {@code ReverseHttpEntityEnclosingRequest} depending on whether the incoming request
 * implemented httpcore 4.x's {@code HttpEntityEnclosingRequest}. HttpComponents Core
 * 5.x has no such split - {@code ClassicHttpRequest} extends {@code HttpEntityContainer}
 * - so both branches collapse into a single {@code ReverseHttpRequest} (ADR-007).
 */
public class ReverseHttpRequestFactory {

	public static ReverseHttpRequest getInstance(ClassicHttpRequest request, ClassicHttpResponse response,
			HttpContext context, ReverseUrl reverseUrl) {
		return new ReverseHttpRequest(request, context, reverseUrl);
	}

	/**
	 * Create ReverseHttpRequest
	 * @since 1.5-20211107
	 * @param request
	 * @param response
	 * @param context
	 * @param reverseUrl
	 * @param version
	 * @return ReverseHttpRequest
	 */
	public static ReverseHttpRequest getInstance(ClassicHttpRequest request, ClassicHttpResponse response,
			HttpContext context, ReverseUrl reverseUrl, ProtocolVersion version) {
		return new ReverseHttpRequest(request, context, reverseUrl, version);
	}
}
