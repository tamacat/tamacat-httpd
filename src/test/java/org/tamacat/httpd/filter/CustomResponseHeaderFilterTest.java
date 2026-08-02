package org.tamacat.httpd.filter;

import static org.junit.Assert.*;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.Test;
import org.tamacat.httpd.mock.HttpObjectFactory;
import org.tamacat.httpd.util.HeaderUtils;

public class CustomResponseHeaderFilterTest {
	
	HttpContext context = HttpObjectFactory.createHttpContext();
	ClassicHttpRequest request = HttpObjectFactory.createHttpRequest("GET", "/test/");
	ClassicHttpResponse response = HttpObjectFactory.createHttpResponse(200, "OK");
	
	@Test
	public void testSetAppendHeader() {
		CustomResponseHeaderFilter filter = new CustomResponseHeaderFilter();
		filter.setAppendHeader("Connection", "Keep-Alive");
		filter.afterResponse(request, response, context);
		assertEquals("Keep-Alive", HeaderUtils.getHeader(response, "Connection"));
	}

	@Test
	public void testSetRemoveHeader() {
		response.setHeader("Connection", "Keep-Alive");
		CustomResponseHeaderFilter filter = new CustomResponseHeaderFilter();
		filter.setRemoveHeader("Connection");
		filter.afterResponse(request, response, context);
		assertEquals(null, response.getFirstHeader("Connection"));
	}
}
