/*
 * Copyright (c) 2009, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.filter;

import java.io.IOException;

import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.ResponseConnControl;
import org.apache.hc.core5.util.Args;
import org.tamacat.httpd.util.RequestUtils;

/**
 * Support a keep-alive for Transfer-Encoding chunked on HTTP/1.1.
 * (customize the org.apache.hc.core5.http.protocol.ResponseConnControl)
 *
 * <p>Migration notes (2.0):
 * <ul>
 *  <li>{@code process} takes the core5 {@link EntityDetails} argument. The entity that
 *      4.4 read back off the response through {@code HttpResponse#getEntity()} is now
 *      exactly this argument, so no downcast is needed here.</li>
 *  <li>{@code response.getStatusLine().getStatusCode()} is {@code response.getCode()},
 *      and the protocol version of the status line is {@code response.getVersion()}
 *      (which core5 allows to be {@code null}; {@link RequestUtils#getVersion} supplies
 *      HTTP/1.1 in that case, matching {@code org.apache.hc.core5.http.message.StatusLine}).</li>
 *  <li>{@code org.apache.http.protocol.HTTP} is gone: the header names come from
 *      {@link HttpHeaders} and the connection tokens from {@link HeaderElements}.
 *      The emitted token spelling therefore changes from {@code "Close"} /
 *      {@code "Keep-Alive"} to {@code "close"} / {@code "keep-alive"}. HTTP compares
 *      connection tokens case-insensitively, so this is a wire-format cosmetic change.</li>
 * </ul>
 */
public class HttpResponseConnControl extends ResponseConnControl {

	@Override
	public void process(final HttpResponse response, final EntityDetails entity, final HttpContext context)
			throws HttpException, IOException {
		Args.notNull(response, "HTTP response");

		final HttpCoreContext corecontext = HttpCoreContext.cast(context);

		// Always drop connection after certain type of responses
		final int status = response.getCode();
		if (status == HttpStatus.SC_BAD_REQUEST || status == HttpStatus.SC_REQUEST_TIMEOUT
				|| status == HttpStatus.SC_LENGTH_REQUIRED || status == HttpStatus.SC_REQUEST_TOO_LONG
				|| status == HttpStatus.SC_REQUEST_URI_TOO_LONG || status == HttpStatus.SC_SERVICE_UNAVAILABLE
				|| status == HttpStatus.SC_NOT_IMPLEMENTED) {
			response.setHeader(HttpHeaders.CONNECTION, HeaderElements.CLOSE);
			return;
		}
		final Header explicit = response.getFirstHeader(HttpHeaders.CONNECTION);
		if (explicit != null && HeaderElements.CLOSE.equalsIgnoreCase(explicit.getValue())) {
			// Connection persistence explicitly disabled
			return;
		}
		// Always drop connection for HTTP/1.0 responses and below
		// if the content body cannot be correctly delimited
		if (entity != null) {
			final ProtocolVersion ver = RequestUtils.getVersion(response);
			if (ver.greaterEquals(HttpVersion.HTTP_1_1)) {
				response.setHeader(HttpHeaders.CONNECTION, HeaderElements.KEEP_ALIVE);
				return;
			} else if (entity.getContentLength() < 0 && (!entity.isChunked() || ver.lessEquals(HttpVersion.HTTP_1_0))) {
				response.setHeader(HttpHeaders.CONNECTION, HeaderElements.CLOSE);
				return;
			}
		}
		// Drop connection if requested by the client or request was <= 1.0
		final HttpRequest request = corecontext != null ? corecontext.getRequest() : null;
		if (request != null) {
			final Header header = request.getFirstHeader(HttpHeaders.CONNECTION);
			if (header != null) {
				response.setHeader(HttpHeaders.CONNECTION, header.getValue());
			} else if (RequestUtils.getVersion(request).lessEquals(HttpVersion.HTTP_1_0)) {
				response.setHeader(HttpHeaders.CONNECTION, HeaderElements.CLOSE);
			}
		}
	}
}
