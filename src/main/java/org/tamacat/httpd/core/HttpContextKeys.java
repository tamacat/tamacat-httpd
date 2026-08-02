/*
 * Copyright 2026 tamacat.org
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package org.tamacat.httpd.core;

/**
 * <p>The {@link org.apache.hc.core5.http.protocol.HttpContext} attribute names that
 * tamacat-httpd owns.
 *
 * <p>These four names used to be repeated as string literals in six places. They are
 * tamacat's own vocabulary: HttpComponents Core 5.x defines no equivalent constants.
 * The nearest core5 attribute is
 * {@code HttpCoreContext.CONNECTION_ENDPOINT ("http.connection-endpoint")}, but it
 * carries an {@code EndpointDetails} - connection metadata - not the connection object
 * itself, and core5 deprecates the constant in favour of
 * {@code HttpCoreContext#getEndpointDetails()}. It is therefore not a replacement for
 * {@link #HTTP_IN_CONN} / {@link #HTTP_OUT_CONN}, which hold the live
 * {@link ServerHttpConnection} / {@link ClientHttpConnection} instances that the
 * keep-alive strategies call {@code setSocketTimeout} on.
 *
 * <p>The literal values are unchanged from 1.5, so an existing deployment that reads
 * these attributes from a custom filter keeps working.
 *
 * @since 2.0
 */
public final class HttpContextKeys {

	/**
	 * The inbound (client-facing) {@link ServerHttpConnection} of the current exchange.
	 * Set by {@code DefaultWorker}, read by {@code KeepAliveConnReuseStrategy} and
	 * {@code RequestUtils#getServerHttpConnection}.
	 */
	public static final String HTTP_IN_CONN = "http.in-conn";

	/**
	 * The outbound (backend-facing) {@link ClientHttpConnection} of the current
	 * exchange. Read by {@code BackEndKeepAliveConnReuseStrategy}.
	 */
	public static final String HTTP_OUT_CONN = "http.out-conn";

	/**
	 * The {@link org.tamacat.httpd.config.ReverseUrl} that the reverse proxy resolved
	 * for the current exchange.
	 */
	public static final String REVERSE_URL = "reverseUrl";

	/**
	 * The authenticated remote user name, set by the authentication filters and read
	 * by the access log and the reverse proxy authorization header.
	 */
	public static final String REMOTE_USER = "REMOTE_USER";

	/** Cannot instantiate. */
	private HttpContextKeys() {}
}
