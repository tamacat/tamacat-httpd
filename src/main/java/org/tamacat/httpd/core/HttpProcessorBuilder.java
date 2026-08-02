/*
 * Copyright (c) 2009, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core;

import java.util.ArrayList;
import java.util.List;

import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.protocol.DefaultHttpProcessor;
import org.apache.hc.core5.http.protocol.HttpProcessor;

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
	 *
	 * <p>HttpComponents Core 5.x removed {@code protocol.ImmutableHttpProcessor}. Its
	 * counterpart is {@link DefaultHttpProcessor}, which offers the same
	 * {@code (List&lt;HttpRequestInterceptor&gt;, List&lt;HttpResponseInterceptor&gt;)}
	 * constructor and copies both lists defensively, so the built processor stays
	 * immutable after construction exactly as before.
	 *
	 * <p>core5 also ships its own {@code protocol.HttpProcessorBuilder}. It is
	 * deliberately not used as a replacement for this class: its API is
	 * {@code add}/{@code addFirst}/{@code addLast} rather than
	 * {@code addInterceptor}, so swapping to it would break every caller of the
	 * tamacat builder for no functional gain, and the two share a simple name.
	 *
	 * @return Implements of {@code HttpProcessor}.
	 */
	public HttpProcessor build() {
		return new DefaultHttpProcessor(req, res);
	}
}
