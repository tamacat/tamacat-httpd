package org.tamacat.httpd.filter;

import static org.junit.jupiter.api.Assertions.*;

import org.tamacat.httpcore4.HttpRequest;
import org.tamacat.httpcore4.HttpResponse;
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
		HttpRequest request = HttpObjectFactory.createHttpRequest("GET", "/");
		request.setHeader("Test","OK");
		HttpResponse response = HttpObjectFactory.createHttpResponse(200, "OK");
		filter.doFilter(request, response, HttpObjectFactory.createHttpContext());
		assertTrue(true);
	}

	@Test
	public void testAfterResponse() {
		HttpRequest request = HttpObjectFactory.createHttpRequest("GET", "/");
		HttpResponse response = HttpObjectFactory.createHttpResponse(200, "OK");
		response.setHeader("Test","OK");
		filter.afterResponse(request, response, HttpObjectFactory.createHttpContext());
		assertTrue(true);
	}

}
