package org.tamacat.httpd.mock;

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;

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
