package org.tamacat.httpd.mock;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * <p>Factory of the HTTP message objects for the tests.
 *
 * <p>Migrated to HttpComponents Core 5.x. In 4.4 a request that carried an entity
 * had to be a {@code BasicClassicHttpRequest}; core5 folds the entity into
 * {@link ClassicHttpRequest} itself, so the method distinction is gone.
 */
public class HttpObjectFactory {

	public static ClassicHttpRequest createHttpRequest(String method, String uri) {
		ClassicHttpRequest req = new BasicClassicHttpRequest(method, uri);
		req.setHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_FORM_URLENCODED.getMimeType());
		return req;
	}

	public static ClassicHttpResponse createHttpResponse(int status, String reason) {
		return new BasicClassicHttpResponse(status, reason);
	}

	public static ClassicHttpResponse createHttpResponse(ProtocolVersion ver, int status, String reason) {
		ClassicHttpResponse response = new BasicClassicHttpResponse(status, reason);
		response.setVersion(ver);
		return response;
	}

	public static HttpContext createHttpContext() {
		return new HttpCoreContext();
	}
}
