package org.tamacat.httpd.filter;

import static org.junit.jupiter.api.Assertions.*;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.httpd.config.ServiceUrl;
import org.tamacat.httpd.mock.HttpObjectFactory;

public class HeaderLoggingFilterTest {

	HeaderLoggingFilter filter;

	@BeforeEach
	public void setUp() throws Exception {
		ServerConfig config = new ServerConfig();
		ServiceUrl serviceUrl = new ServiceUrl(config);
		filter = new HeaderLoggingFilter();
		filter.init(serviceUrl);
	}

	@AfterEach
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
