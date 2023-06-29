/*
 * Copyright (c) 2023, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.filter;

import static org.junit.Assert.*;

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.httpd.config.ServiceUrl;
import org.tamacat.httpd.util.HeaderUtils;

public class RewriteXFFHeaderFilterTest {

	ServiceUrl serviceUrl;

	@Before
	public void setUp() throws Exception {
		ServerConfig config = new ServerConfig();
		serviceUrl = new ServiceUrl(config);
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testDoFilter() {
		HttpRequest request = createHttpRequest("GET", "/");
		RewriteXFFHeaderFilter filter = new RewriteXFFHeaderFilter();
		
		request.setHeader("X-Forwarded-For", "192.168.1.1");
		filter.doFilter(request, null, null);
		assertEquals("192.168.1.1", HeaderUtils.getHeader(request, "X-Forwarded-For"));

		request.setHeader("X-Forwarded-For", "192.168.1.1, 127.0.0.1");
		filter.doFilter(request, null, null);
		assertEquals("127.0.0.1", HeaderUtils.getHeader(request, "X-Forwarded-For"));
	}
	
	@Test
	public void testDoFilter_Last() {
		HttpRequest request = createHttpRequest("GET", "/");
		RewriteXFFHeaderFilter filter = new RewriteXFFHeaderFilter();
		request.setHeader("X-Forwarded-For", "xxx.xxx.xxx.xxx, 192.168.1.1, 192.168.10.11");
		
		filter.setConvertMode("last");
		filter.doFilter(request, null, null);
		assertEquals("192.168.10.11", HeaderUtils.getHeader(request, "X-Forwarded-For"));
	}

	@Test
	public void testDoFilter_First() {
		HttpRequest request = createHttpRequest("GET", "/");
		RewriteXFFHeaderFilter filter = new RewriteXFFHeaderFilter();
		request.setHeader("X-Forwarded-For", "xxx.xxx.xxx.xxx, 192.168.1.1, 192.168.10.11");
		
		filter.setConvertMode("first");
		filter.doFilter(request, null, null);
		assertEquals("xxx.xxx.xxx.xxx", HeaderUtils.getHeader(request, "X-Forwarded-For"));
	}
	
	@Test
	public void testDoFilter_Remove() {
		HttpRequest request = createHttpRequest("GET", "/");
		RewriteXFFHeaderFilter filter = new RewriteXFFHeaderFilter();
		request.setHeader("X-Forwarded-For", "192.168.1.1");
		
		filter.setProcessingMode("remove");
		filter.doFilter(request, null, null);
		assertEquals(null, HeaderUtils.getHeader(request, "X-Forwarded-For"));
	}
	
	@Test
	public void testDoFilter_Append() {
		HttpRequest request = createHttpRequest("GET", "/");
		RewriteXFFHeaderFilter filter = new RewriteXFFHeaderFilter();
		request.setHeader("X-Forwarded-For", "192.168.1.1");

		filter.setProcessingMode("append");
		filter.doFilter(request, null, null);
		assertEquals("192.168.1.1", HeaderUtils.getHeader(request, "X-Reverse-Forwarded-For"));
	}
	
	@Test
	public void testDoFilter_Override() {
		HttpRequest request = createHttpRequest("GET", "/");
		RewriteXFFHeaderFilter filter = new RewriteXFFHeaderFilter();
		request.setHeader("X-Reverse-Forwarded-For", "192.168.1.1");

		filter.setProcessingMode("override");
		filter.doFilter(request, null, null);
		assertEquals("192.168.1.1", HeaderUtils.getHeader(request, "X-Forwarded-For"));
	}
	
	@Test
	public void testDoFilter_AppendAndOverride_Last() {
		HttpRequest request = createHttpRequest("GET", "/");
		RewriteXFFHeaderFilter filter = new RewriteXFFHeaderFilter();
		request.setHeader("X-Forwarded-For", "127.0.0.1, 192.168.1.1");

		filter.setProcessingMode("append");
		filter.setConvertMode("last");
		
		filter.doFilter(request, null, null);
		assertEquals("192.168.1.1", HeaderUtils.getHeader(request, "X-Reverse-Forwarded-For"));

		filter.setProcessingMode("override");
		filter.doFilter(request, null, null);
		assertEquals("192.168.1.1", HeaderUtils.getHeader(request, "X-Forwarded-For"));
	}

	@Test
	public void testDoFilter_AppendAndOverride_First() {
		HttpRequest request = createHttpRequest("GET", "/");
		RewriteXFFHeaderFilter filter = new RewriteXFFHeaderFilter();
		request.setHeader("X-Forwarded-For", "127.0.0.1, 192.168.1.1");

		filter.setProcessingMode("append");
		filter.setConvertMode("first");
		
		filter.doFilter(request, null, null);
		assertEquals("127.0.0.1", HeaderUtils.getHeader(request, "X-Reverse-Forwarded-For"));

		filter.setProcessingMode("override");
		filter.doFilter(request, null, null);
		assertEquals("127.0.0.1", HeaderUtils.getHeader(request, "X-Forwarded-For"));
	}

	@Test
	public void testDoFilter_AppendAndOverride_None() {
		HttpRequest request = createHttpRequest("GET", "/");
		RewriteXFFHeaderFilter filter = new RewriteXFFHeaderFilter();
		request.setHeader("X-Forwarded-For", "127.0.0.1, 192.168.1.1");

		filter.setProcessingMode("append");
		filter.setConvertMode("none");
		
		filter.doFilter(request, null, null);
		assertEquals("127.0.0.1, 192.168.1.1", HeaderUtils.getHeader(request, "X-Reverse-Forwarded-For"));

		filter.setProcessingMode("override");
		filter.doFilter(request, null, null);
		assertEquals("127.0.0.1, 192.168.1.1", HeaderUtils.getHeader(request, "X-Forwarded-For"));
	}
	
	public static HttpRequest createHttpRequest(String method, String uri) {
		if ("POST".equalsIgnoreCase(method)) {
			return new BasicHttpEntityEnclosingRequest(method, uri);
		} else {
			return new BasicHttpRequest(method, uri);
		}
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
