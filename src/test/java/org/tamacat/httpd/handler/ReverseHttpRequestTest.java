/*
 * Copyright (c) 2009, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.handler;

import static org.junit.Assert.*;

import java.net.URL;

import org.apache.http.HttpRequest;
import org.apache.http.HttpVersion;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tamacat.httpd.config.DefaultReverseUrl;
import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.httpd.config.ReverseUrl;
import org.tamacat.httpd.config.ServiceConfig;
import org.tamacat.httpd.config.ServiceType;
import org.tamacat.httpd.config.ServiceUrl;
import org.tamacat.httpd.mock.HttpObjectFactory;

public class ReverseHttpRequestTest {

	ReverseUrl reverseUrl;
	ServiceUrl url;
	ServerConfig config;

	@Before
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

	@After
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
}
