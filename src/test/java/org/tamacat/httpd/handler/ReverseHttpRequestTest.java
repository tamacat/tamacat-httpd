/*
 * Copyright (c) 2009, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.handler;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URL;

import org.tamacat.httpcore4.HttpRequest;
import org.tamacat.httpcore4.HttpVersion;
import org.tamacat.httpcore4.message.BasicHttpRequest;
import org.tamacat.httpcore4.protocol.BasicHttpContext;
import org.tamacat.httpcore4.protocol.HTTP;
import org.tamacat.httpcore4.protocol.HttpContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tamacat.httpd.config.DefaultReverseUrl;
import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.httpd.config.ReverseUrl;
import org.tamacat.httpd.config.ServiceConfig;
import org.tamacat.httpd.config.ServiceType;
import org.tamacat.httpd.config.ServiceUrl;
import org.tamacat.httpd.exception.NotFoundException;
import org.tamacat.httpd.mock.HttpObjectFactory;
import org.tamacat.httpd.util.RequestUtils;

public class ReverseHttpRequestTest {

	ReverseUrl reverseUrl;
	ServiceUrl url;
	ServerConfig config;

	@BeforeEach
	public void setUp() throws Exception {
		config = new ServerConfig();
		ServiceConfig serviceConfig	= new ServiceConfig();

		ServiceUrl serviceUrl = new ServiceUrl(config);
		serviceUrl.setHandlerName("ReverseHandler");
		serviceUrl.setPath("/test2/");
		serviceUrl.setType(ServiceType.REVERSE);

		reverseUrl = new DefaultReverseUrl(serviceUrl);
		reverseUrl.setReverse(new URL("http://localhost:8080/test/"));
		serviceUrl.setReverseUrl(reverseUrl);
		serviceConfig.addServiceUrl(serviceUrl);

		url = serviceConfig.getServiceUrl("/test2/");
		reverseUrl = url.getReverseUrl();
	}

	@AfterEach
	public void tearDown() throws Exception {
	}

	@Test
	public void testReverseHttpRequest() throws CloneNotSupportedException {
		ReverseHttpRequest request =
			new ReverseHttpRequest(
					new BasicHttpRequest("GET","/test2/test.jsp"),
					new BasicHttpContext(),
					reverseUrl);

		assertNotNull(request.getAllHeaders());
		assertEquals("/test/test.jsp", request.getRequestLine().getUri());
	}

	@Test
	public void testReverseHttpRequest2() throws CloneNotSupportedException {
		ReverseHttpRequest request =
			new ReverseHttpRequest(
					new BasicHttpRequest("GET","/test2/test.jsp?id=123&key=value"),
					new BasicHttpContext(),
					reverseUrl);

		assertNotNull(request.getAllHeaders());
		assertEquals("/test/test.jsp?id=123&key=value", request.getRequestLine().getUri());
	}

	@Test
	public void testRewriteHostHeader() {
		ReverseHttpRequest request =
				new ReverseHttpRequest(
						new BasicHttpRequest("GET","/test2/test.jsp"),
						new BasicHttpContext(),
						reverseUrl);

		request.setHeader(HTTP.TARGET_HOST, "www.example.com:8080");

		HttpContext context = HttpObjectFactory.createHttpContext();
		request.rewriteHostHeader(request, context);

	}
	
	@Test
	public void testReverseHttpRequest_1_0() throws CloneNotSupportedException {
		HttpRequest originalRequest1 = new BasicHttpRequest("GET","/test2/test.jsp", HttpVersion.HTTP_1_0);
		ReverseHttpRequest request1 =
			new ReverseHttpRequest(
					originalRequest1,
					new BasicHttpContext(),
					reverseUrl,
					HttpVersion.HTTP_1_0);
		assertNull(request1.getFirstHeader(HTTP.TARGET_HOST));
		
		HttpRequest originalRequest2 = new BasicHttpRequest("GET","/test2/test.jsp", HttpVersion.HTTP_1_0);
		originalRequest2.setHeader(HTTP.TARGET_HOST, "localhost");
		ReverseHttpRequest request2 =
				new ReverseHttpRequest(
						originalRequest2,
						new BasicHttpContext(),
						reverseUrl,
						HttpVersion.HTTP_1_1);
		assertEquals("localhost:8080", request2.getFirstHeader(HTTP.TARGET_HOST).getValue());
	}
	
	@Test
	public void testReverseHttpRequest_1_1() throws CloneNotSupportedException {
		HttpRequest originalRequest = new BasicHttpRequest("GET","/test2/test.jsp", HttpVersion.HTTP_1_1);
		originalRequest.setHeader(HTTP.TARGET_HOST, "localhost");
		
		ReverseHttpRequest request =
			new ReverseHttpRequest(
					originalRequest,
					new BasicHttpContext(),
					reverseUrl,
					HttpVersion.HTTP_1_1);
		assertEquals("localhost:8080", request.getFirstHeader(HTTP.TARGET_HOST).getValue());
	}

//	@Test
//	public void testClone() throws CloneNotSupportedException {
//		ReverseHttpRequest request =
//			new ReverseHttpRequest(
//					new BasicHttpRequest("GET","/test/test.jsp"),
//					reverseUrl);
//		ReverseHttpRequest clone = request.clone();
//		assertNotSame(clone, request);
//		assertNotSame(clone.reverseUrl, request.reverseUrl);
//	}

	/**
	 * T-URL-2. A request URI the backend URL cannot be built from is a 404
	 * ({@link NotFoundException}), not a NullPointerException from the
	 * superclass constructor argument.
	 */
	@Test
	public void testRequestUriWithoutBackendUrlIsNotFound() {
		//control: the same request without the offending character is forwarded
		ReverseHttpRequest request = new ReverseHttpRequest(
				new BasicHttpRequest("GET", "/test2/x?a=12"),
				new BasicHttpContext(),
				reverseUrl);
		assertEquals("/test/x?a=12", request.getRequestLine().getUri());

		assertThrows(NotFoundException.class, () -> new ReverseHttpRequest(
				new BasicHttpRequest("GET", "/test2/x?a=1|2"),
				new BasicHttpContext(),
				reverseUrl));
	}

	/**
	 * T-URL-3. The request URI is fine but the Host header value cannot be the
	 * host of a URL: that is a 404 as well, not a NullPointerException in
	 * rewriteHostHeader. A valid Host header - including one with an underscore,
	 * which java.net.URI does not parse as a host name - is still rewritten to
	 * the backend.
	 */
	@Test
	public void testInvalidHostHeaderIsNotFound() {
		HttpRequest valid = new BasicHttpRequest("GET", "/test2/test.jsp", HttpVersion.HTTP_1_1);
		valid.setHeader(HTTP.TARGET_HOST, "my_host");
		ReverseHttpRequest request = new ReverseHttpRequest(
				valid, new BasicHttpContext(), reverseUrl, HttpVersion.HTTP_1_1);
		assertEquals("localhost:8080", request.getFirstHeader(HTTP.TARGET_HOST).getValue());

		HttpRequest invalid = new BasicHttpRequest("GET", "/test2/test.jsp", HttpVersion.HTTP_1_1);
		invalid.setHeader(HTTP.TARGET_HOST, "h{1}");
		assertThrows(NotFoundException.class, () -> new ReverseHttpRequest(
				invalid, new BasicHttpContext(), reverseUrl, HttpVersion.HTTP_1_1));
	}

	/**
	 * T-URL-3b. RequestUtils.getRequestURL, which rewriteHostHeader uses, joins
	 * the Host header and the request URI. A request URI that does not start
	 * with "/" ("*", a relative path, "@host/...", a bare query, or an
	 * absolute-form URI, which StandardHttpRequestFactory normally rewrites to
	 * origin-form before it gets here) must give null, with or without a port
	 * in the Host header or the server configuration, so it can never change
	 * the host of the URL. Origin-form request URIs still give the URL with the
	 * Host header's host.
	 */
	@Test
	public void testGetRequestURLRejectsRequestUriNotStartingWithSlash() throws Exception {
		String[] rejected = {"*", "test.html", "@evil.example/x", "?q=1", "http://evil.example/x"};
		ServiceUrl[] services = {null, serviceUrlOnPort("8080"), serviceUrlOnPort("80")};
		for (String host : new String[] {"example.com", "example.com:8080"}) {
			for (ServiceUrl service : services) {
				for (String uri : rejected) {
					String label = host + " " + (service == null ? "-" : service.getServerConfig().getPort()) + " " + uri;
					HttpRequest req = new BasicHttpRequest("GET", uri, HttpVersion.HTTP_1_1);
					req.setHeader(HTTP.TARGET_HOST, host);
					assertNull(RequestUtils.getRequestURL(req, null, service), label);
					assertNull(RequestUtils.getRequestHostURL(req, null, service), label);
				}
			}
		}

		// accepted-side controls: the host is the Host header's host
		String[][] accepted = {
			{"example.com", "/test.html", "http://example.com/test.html", "http://example.com"},
			{"example.com", "/x?q=1", "http://example.com/x?q=1", "http://example.com"},
			{"example.com:8080", "/test.html", "http://example.com:8080/test.html", "http://example.com:8080"},
			{"example.com:8080", "/x?q=1", "http://example.com:8080/x?q=1", "http://example.com:8080"},
		};
		for (String[] c : accepted) {
			HttpRequest req = new BasicHttpRequest("GET", c[1], HttpVersion.HTTP_1_1);
			req.setHeader(HTTP.TARGET_HOST, c[0]);
			URL url = RequestUtils.getRequestURL(req, null);
			assertNotNull(url, c[0] + " " + c[1]);
			assertEquals(c[2], url.toString());
			assertEquals("example.com", url.getHost());
			assertNull(url.getUserInfo());
			assertEquals(c[3], RequestUtils.getRequestHostURL(req, null, null));
		}
		HttpRequest req = new BasicHttpRequest("GET", "/x?q=1", HttpVersion.HTTP_1_1);
		req.setHeader(HTTP.TARGET_HOST, "example.com");
		assertEquals("http://example.com:8080/x?q=1",
			RequestUtils.getRequestURL(req, null, serviceUrlOnPort("8080")).toString());
		assertEquals("http://example.com/x?q=1",
			RequestUtils.getRequestURL(req, null, serviceUrlOnPort("80")).toString());
	}

	static ServiceUrl serviceUrlOnPort(String port) {
		ServerConfig config = new ServerConfig();
		config.setParam("Port", port);
		return new ServiceUrl(config);
	}
}
