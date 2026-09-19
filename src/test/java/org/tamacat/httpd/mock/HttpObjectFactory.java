package org.tamacat.httpd.mock;

import org.tamacat.httpcore4.HttpRequest;
import org.tamacat.httpcore4.HttpResponse;
import org.tamacat.httpcore4.ProtocolVersion;
import org.tamacat.httpcore4.message.BasicHttpEntityEnclosingRequest;
import org.tamacat.httpcore4.message.BasicHttpRequest;
import org.tamacat.httpcore4.message.BasicHttpResponse;
import org.tamacat.httpcore4.protocol.BasicHttpContext;
import org.tamacat.httpcore4.protocol.HTTP;
import org.tamacat.httpcore4.protocol.HttpContext;

public class HttpObjectFactory {

	public static HttpRequest createHttpRequest(String method, String uri) {
		HttpRequest req = null;
		if ("POST".equalsIgnoreCase(method)) {
			req = new BasicHttpEntityEnclosingRequest(method, uri);
		} else {
			req = new BasicHttpRequest(method, uri);
		}
		req.setHeader(HTTP.CONTENT_TYPE, "application/x-www-form-urlencoded");
		return req;
	}

	public static HttpResponse createHttpResponse(int status, String reason) {
		return new BasicHttpResponse(new ProtocolVersion("HTTP",1,1), status, reason);
	}

	public static HttpResponse createHttpResponse(ProtocolVersion ver, int status, String reason) {
		return new BasicHttpResponse(ver, status, reason);
	}

	public static HttpContext createHttpContext() {
		return new BasicHttpContext();
	}
}
