package org.tamacat.httpd.handler;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URL;

import org.tamacat.httpcore4.HttpRequest;
import org.tamacat.httpcore4.HttpResponse;
import org.tamacat.httpcore4.HttpVersion;
import org.tamacat.httpcore4.protocol.HttpContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tamacat.httpd.config.DefaultReverseUrl;
import org.tamacat.httpd.config.ReverseUrl;
import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.httpd.config.ServiceUrl;
import org.tamacat.httpd.mock.HttpObjectFactory;

public class ReverseHttpRequestFactoryTest {

	ServiceUrl serviceUrl;
	ReverseUrl reverseUrl;

	@BeforeEach
	public void setUp() throws Exception {
		ServerConfig config = new ServerConfig();
		serviceUrl = new ServiceUrl(config);
		serviceUrl.setPath("/");
		reverseUrl = new DefaultReverseUrl(serviceUrl);
		reverseUrl.setReverse(new URL("http://localhost:8080/"));
	}

	@AfterEach
	public void tearDown() throws Exception {
	}

	@Test
	public void testGetInstanceGET() {
		HttpRequest request = HttpObjectFactory.createHttpRequest("GET", "/");
		HttpResponse response = HttpObjectFactory.createHttpResponse(200, "OK");
		HttpContext context = HttpObjectFactory.createHttpContext();

		assertFalse(
			ReverseHttpRequestFactory.getInstance(request, response, context, reverseUrl, HttpVersion.HTTP_1_1)
			instanceof ReverseHttpEntityEnclosingRequest);

	}

	@Test
	public void testGetInstancePOST() {
		HttpRequest request = HttpObjectFactory.createHttpRequest("POST", "/");
		HttpResponse response = HttpObjectFactory.createHttpResponse(200, "OK");
		HttpContext context = HttpObjectFactory.createHttpContext();

		assertTrue(
			ReverseHttpRequestFactory.getInstance(request, response, context, reverseUrl, HttpVersion.HTTP_1_1)
			instanceof ReverseHttpEntityEnclosingRequest);
	}

}
