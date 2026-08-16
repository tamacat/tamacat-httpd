/*
 * Copyright (c) 2014, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.handler;

import java.io.IOException;

import org.apache.hc.core5.http.io.HttpClientConnection;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.util.Timeout;
import org.tamacat.httpd.mock.HttpObjectFactory;

public class DummyHttpRequestExecutor extends HttpRequestExecutor {
	ClassicHttpRequest request;
	HttpContext context;

	public DummyHttpRequestExecutor() {
	}

	@Deprecated
	public DummyHttpRequestExecutor(int waitForContinue) {
		super(Timeout.ofMilliseconds(waitForContinue), null, null);
	}

	@Override
	public ClassicHttpResponse execute(
			final ClassicHttpRequest request,
			final HttpClientConnection conn,
			final HttpContext context) throws IOException, HttpException {
		this.request = request;
		this.context = context;
		return 	HttpObjectFactory.createHttpResponse(200, "OK");
	}

	public ClassicHttpRequest getHttpRequest() {
		return request;
	}

	public HttpContext getHttpContext() {
		return context;
	}
}
