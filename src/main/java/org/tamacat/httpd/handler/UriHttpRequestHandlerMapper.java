/*
 * Copyright (c) 2026, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.handler;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestMapper;
import org.apache.hc.core5.http.impl.routing.PathPatternMatcher;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;
import org.tamacat.httpd.util.RequestUtils;

/**
 * <p>Maintains a map of {@link HttpRequestHandler} keyed by a request URI pattern.
 * <br>
 * Patterns may have three formats:
 * <ul>
 *   <li>{@code *}</li>
 *   <li>{@code *&lt;uri&gt;}</li>
 *   <li>{@code &lt;uri&gt;*}</li>
 * </ul>
 *
 * <p>This class replaces {@code org.apache.http.protocol.UriHttpRequestHandlerMapper},
 * which has no counterpart in HttpComponents Core 5.x. The matching rules are
 * delegated to core5's {@link PathPatternMatcher}, whose {@code match} and
 * {@code isBetter} implementations are identical to the ones that
 * {@code org.apache.http.protocol.UriPatternMatcher} used in 4.4.
 *
 * @since 2.0
 */
public class UriHttpRequestHandlerMapper implements HttpRequestMapper<HttpRequestHandler> {

	private final PathPatternMatcher matcher = PathPatternMatcher.INSTANCE;

	private final Map<String, HttpRequestHandler> handlerMap = new LinkedHashMap<>();

	/**
	 * <p>Registers the given {@link HttpRequestHandler} as a handler for URIs
	 * matching the given pattern.
	 * @param pattern the pattern to register the handler for.
	 * @param handler the handler.
	 */
	public synchronized void register(String pattern, HttpRequestHandler handler) {
		Args.notNull(pattern, "Pattern");
		Args.notNull(handler, "Handler");
		handlerMap.put(pattern, handler);
	}

	/**
	 * <p>Removes registered handler, if exists, for the given pattern.
	 * @param pattern the pattern to unregister the handler for.
	 */
	public synchronized void unregister(String pattern) {
		if (pattern == null) {
			return;
		}
		handlerMap.remove(pattern);
	}

	/**
	 * <p>Looks up a handler matching the given request URI.
	 * @param request the request
	 * @param context the execution context (not used for the path matching)
	 * @return handler or {@code null} if no match is found.
	 */
	@Override
	public HttpRequestHandler resolve(HttpRequest request, HttpContext context) {
		if (request == null) {
			return null;
		}
		return lookup(getRequestPath(request));
	}

	/**
	 * <p>Looks up a handler matching the given request path.
	 * @param path the request path (without the query string)
	 * @return handler or {@code null} if no match is found.
	 */
	public synchronized HttpRequestHandler lookup(String path) {
		if (path == null) {
			return null;
		}
		// direct match?
		HttpRequestHandler handler = handlerMap.get(path);
		if (handler == null) {
			// pattern match?
			String bestMatch = null;
			for (Map.Entry<String, HttpRequestHandler> entry : handlerMap.entrySet()) {
				String pattern = entry.getKey();
				if (matcher.match(pattern, path) && matcher.isBetter(pattern, bestMatch)) {
					handler = entry.getValue();
					bestMatch = pattern;
				}
			}
		}
		return handler;
	}

	/**
	 * <p>Extracts the request path from the given {@link HttpRequest}.
	 * <p>The query string ({@code ?}) and the fragment ({@code #}) are cut off,
	 * the same way {@code UriHttpRequestHandlerMapper#getRequestPath} did in 4.4.
	 * @param request
	 */
	protected String getRequestPath(HttpRequest request) {
		String path = request.getPath();
		return path != null ? RequestUtils.getPath(path) : null;
	}

	@Override
	public String toString() {
		return handlerMap.toString();
	}
}
