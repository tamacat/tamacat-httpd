package org.tamacat.httpd.filter;

import static org.junit.Assert.*;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.httpd.config.ServiceUrl;
import org.tamacat.httpd.mock.HttpObjectFactory;

public class HeaderLoggingFilterTest {

	HeaderLoggingFilter filter;

	@Before
	public void setUp() throws Exception {
		ServerConfig config = new ServerConfig();
		ServiceUrl serviceUrl = new ServiceUrl(config);
		filter = new HeaderLoggingFilter();
		filter.init(serviceUrl);
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testDoFilter() {
		ClassicHttpRequest request = HttpObjectFactory.createHttpRequest("GET", "/");
		request.setHeader("Test","OK");
		ClassicHttpResponse response = HttpObjectFactory.createHttpResponse(200, "OK");
		filter.doFilter(request, response, HttpObjectFactory.createHttpContext());
		assertTrue(true);
	}

	@Test
	public void testAfterResponse() {
		ClassicHttpRequest request = HttpObjectFactory.createHttpRequest("GET", "/");
		ClassicHttpResponse response = HttpObjectFactory.createHttpResponse(200, "OK");
		response.setHeader("Test","OK");
		filter.afterResponse(request, response, HttpObjectFactory.createHttpContext());
		assertTrue(true);
	}

}
