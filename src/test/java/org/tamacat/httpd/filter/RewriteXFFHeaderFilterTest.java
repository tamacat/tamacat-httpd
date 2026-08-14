/*
 * Copyright (c) 2023, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.filter;

import static org.junit.Assert.*;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.HttpContext;
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
		ClassicHttpRequest request = createHttpRequest("GET", "/");
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
		ClassicHttpRequest request = createHttpRequest("GET", "/");
		RewriteXFFHeaderFilter filter = new RewriteXFFHeaderFilter();
		request.setHeader("X-Forwarded-For", "xxx.xxx.xxx.xxx, 192.168.1.1, 192.168.10.11");
		
		filter.setConvertMode("last");
		filter.doFilter(request, null, null);
		assertEquals("192.168.10.11", HeaderUtils.getHeader(request, "X-Forwarded-For"));
	}

	@Test
	public void testDoFilter_First() {
		ClassicHttpRequest request = createHttpRequest("GET", "/");
		RewriteXFFHeaderFilter filter = new RewriteXFFHeaderFilter();
		request.setHeader("X-Forwarded-For", "xxx.xxx.xxx.xxx, 192.168.1.1, 192.168.10.11");
		
		filter.setConvertMode("first");
		filter.doFilter(request, null, null);
		assertEquals("xxx.xxx.xxx.xxx", HeaderUtils.getHeader(request, "X-Forwarded-For"));
	}
	
	@Test
	public void testDoFilter_Remove() {
		ClassicHttpRequest request = createHttpRequest("GET", "/");
		RewriteXFFHeaderFilter filter = new RewriteXFFHeaderFilter();
		request.setHeader("X-Forwarded-For", "192.168.1.1");
		
		filter.setProcessingMode("remove");
		filter.doFilter(request, null, null);
		assertEquals(null, HeaderUtils.getHeader(request, "X-Forwarded-For"));
	}
	
	@Test
	public void testDoFilter_Append() {
		ClassicHttpRequest request = createHttpRequest("GET", "/");
		RewriteXFFHeaderFilter filter = new RewriteXFFHeaderFilter();
		request.setHeader("X-Forwarded-For", "192.168.1.1");

		filter.setProcessingMode("append");
		filter.doFilter(request, null, null);
		assertEquals("192.168.1.1", HeaderUtils.getHeader(request, "X-Reverse-Forwarded-For"));
	}
	
	@Test
	public void testDoFilter_Override() {
		ClassicHttpRequest request = createHttpRequest("GET", "/");
		RewriteXFFHeaderFilter filter = new RewriteXFFHeaderFilter();
		request.setHeader("X-Reverse-Forwarded-For", "192.168.1.1");

		filter.setProcessingMode("override");
		filter.doFilter(request, null, null);
		assertEquals("192.168.1.1", HeaderUtils.getHeader(request, "X-Forwarded-For"));
	}
	
	@Test
	public void testDoFilter_AppendAndOverride_Last() {
		ClassicHttpRequest request = createHttpRequest("GET", "/");
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
		ClassicHttpRequest request = createHttpRequest("GET", "/");
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
		ClassicHttpRequest request = createHttpRequest("GET", "/");
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
	
	public static ClassicHttpRequest createHttpRequest(String method, String uri) {
		if ("POST".equalsIgnoreCase(method)) {
			return new BasicClassicHttpRequest(method, uri);
		} else {
			return new BasicClassicHttpRequest(method, uri);
		}
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
		return new BasicHttpContext();
	}
}
