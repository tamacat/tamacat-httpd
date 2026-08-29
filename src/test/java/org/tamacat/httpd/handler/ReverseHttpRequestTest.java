/*
 * Copyright (c) 2009, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.handler;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

	@BeforeEach
	public void setUp() throws Exception {
		config = new ServerConfig();
		ServiceConfig serviceConfig	= new ServiceConfig();

		ServiceUrl serviceUrl = new ServiceUrl(config);
		serviceUrl.setHandlerName("ReverseHandler");
		serviceUrl.setPath("/test2/");
		serviceUrl.setType(ServiceType.REVERSE);

		reverseUrl = new DefaultReverseUrl(serviceUrl);
		reverseUrl.setReverse(new URI("http://localhost:8080/test/").toURL());
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
					new BasicClassicHttpRequest("GET","/test2/test.jsp"),
					new HttpCoreContext(),
					reverseUrl);

		assertNotNull(request.getHeaders());
		assertEquals("/test/test.jsp", request.getRequestUri());
	}

	@Test
	public void testReverseHttpRequest2() throws CloneNotSupportedException {
		ReverseHttpRequest request =
			new ReverseHttpRequest(
					new BasicClassicHttpRequest("GET","/test2/test.jsp?id=123&key=value"),
					new HttpCoreContext(),
					reverseUrl);

		assertNotNull(request.getHeaders());
		assertEquals("/test/test.jsp?id=123&key=value", request.getRequestUri());
	}

	/*
	 * The three tests below were ReverseHttpEntityEnclosingRequestTest up to 1.5.
	 * ADR-007 merged ReverseHttpEntityEnclosingRequest into ReverseHttpRequest because
	 * core5's ClassicHttpRequest already extends HttpEntityContainer, so the entity-
	 * carrying case is now just a POST against this same class.
	 */

	@Test
	public void testReverseHttpRequestWithEntity() throws Exception {
		ReverseHttpRequest request =
			new ReverseHttpRequest(
					new BasicClassicHttpRequest("POST","/test2/test.jsp"),
					new HttpCoreContext(),
					reverseUrl);
		request.setEntity(new StringEntity("test", ContentType.TEXT_PLAIN));

		assertNotNull(request.getEntity());
		assertNotNull(request.getHeaders());
		assertEquals("/test/test.jsp", request.getRequestUri());
	}

	@Test
	public void testReverseHttpRequestWithEntity2() throws Exception {
		ReverseHttpRequest request =
			new ReverseHttpRequest(
					new BasicClassicHttpRequest("POST","/test2/test.jsp?id=123&key=value"),
					new HttpCoreContext(),
					reverseUrl);
		request.setEntity(new StringEntity("test", ContentType.TEXT_PLAIN));
		assertNotNull(request.getEntity());
		assertNotNull(request.getHeaders());
		assertEquals("/test/test.jsp?id=123&key=value", request.getRequestUri());
	}

	@Test
	public void testExpectContinue() {
		ReverseHttpRequest request =
				new ReverseHttpRequest(
						new BasicClassicHttpRequest("POST","/test2/test.jsp?id=123&key=value"),
						new HttpCoreContext(),
						reverseUrl);
		assertNull(request.getFirstHeader(HttpHeaders.EXPECT));

		request.setHeader(HttpHeaders.EXPECT, HeaderElements.CONTINUE);
		assertEquals(HeaderElements.CONTINUE,
			request.getFirstHeader(HttpHeaders.EXPECT).getValue());
	}

	@Test
	public void testRewriteHostHeader() {
		ReverseHttpRequest request =
				new ReverseHttpRequest(
						new BasicClassicHttpRequest("GET","/test2/test.jsp"),
						new HttpCoreContext(),
						reverseUrl);

		request.setHeader(HttpHeaders.HOST, "www.example.com:8080");

		HttpContext context = HttpObjectFactory.createHttpContext();
		request.rewriteHostHeader(request, context);

	}
	
	@Test
	public void testReverseHttpRequest_1_0() throws CloneNotSupportedException {
		ClassicHttpRequest originalRequest1 = new BasicClassicHttpRequest("GET", "/test2/test.jsp");
		originalRequest1.setVersion(HttpVersion.HTTP_1_0);
		ReverseHttpRequest request1 =
			new ReverseHttpRequest(
					originalRequest1,
					new HttpCoreContext(),
					reverseUrl,
					HttpVersion.HTTP_1_0);
		assertNull(request1.getFirstHeader(HttpHeaders.HOST));
		
		ClassicHttpRequest originalRequest2 = new BasicClassicHttpRequest("GET", "/test2/test.jsp");
		originalRequest2.setVersion(HttpVersion.HTTP_1_0);
		originalRequest2.setHeader(HttpHeaders.HOST, "localhost");
		ReverseHttpRequest request2 =
				new ReverseHttpRequest(
						originalRequest2,
						new HttpCoreContext(),
						reverseUrl,
						HttpVersion.HTTP_1_1);
		assertEquals("localhost:8080", request2.getFirstHeader(HttpHeaders.HOST).getValue());
	}
	
	@Test
	public void testReverseHttpRequest_1_1() throws CloneNotSupportedException {
		ClassicHttpRequest originalRequest = new BasicClassicHttpRequest("GET", "/test2/test.jsp");
		originalRequest.setVersion(HttpVersion.HTTP_1_1);
		originalRequest.setHeader(HttpHeaders.HOST, "localhost");
		
		ReverseHttpRequest request =
			new ReverseHttpRequest(
					originalRequest,
					new HttpCoreContext(),
					reverseUrl,
					HttpVersion.HTTP_1_1);
		assertEquals("localhost:8080", request.getFirstHeader(HttpHeaders.HOST).getValue());
	}

//	@Test
//	public void testClone() throws CloneNotSupportedException {
//		ReverseHttpRequest request =
//			new ReverseHttpRequest(
//					new BasicClassicHttpRequest("GET","/test/test.jsp"),
//					reverseUrl);
//		ReverseHttpRequest clone = request.clone();
//		assertNotSame(clone, request);
//		assertNotSame(clone.reverseUrl, request.reverseUrl);
//	}
}
