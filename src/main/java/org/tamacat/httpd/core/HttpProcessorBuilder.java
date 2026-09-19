/*
 * Copyright (c) 2009, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core;

import java.util.ArrayList;
import java.util.List;

import org.tamacat.httpcore4.HttpRequestInterceptor;
import org.tamacat.httpcore4.HttpResponseInterceptor;
import org.tamacat.httpcore4.protocol.HttpProcessor;
import org.tamacat.httpcore4.protocol.ImmutableHttpProcessor;

/**
 * <p>The builder class for {@link HttpProcessor}.<br>
 * The {@link #build} method execute a build {@code HttpProcessor} and returns.
 */
public class HttpProcessorBuilder {

	private List<HttpRequestInterceptor> req = new ArrayList<>();
	private List<HttpResponseInterceptor> res = new ArrayList<>();

	/**
	 * <p>Add the {@link HttpRequestInterceptor}.
	 * @param interceptor
	 * @return added the interceptor object.
	 */
	public HttpProcessorBuilder addInterceptor(HttpRequestInterceptor interceptor) {
		req.add(interceptor);
		return this;
	}

	/**
	 * <p>Add the {@link HttpResponseInterceptor}.
	 * @param interceptor
	 * @return added the interceptor object.
	 */
	public HttpProcessorBuilder addInterceptor(HttpResponseInterceptor interceptor) {
		res.add(interceptor);
		return this;
	}

	/**
	 * <p>Create a new {@code HttpProcessor} and returns.
	 * @return Implements of {@code HttpProcessor}.
	 */
	public HttpProcessor build() {
		return new ImmutableHttpProcessor(req, res);
	}
}
