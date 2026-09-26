package org.tamacat.httpd.handler;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tamacat.httpd.config.DefaultReverseUrl;
import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.httpd.config.ServiceType;
import org.tamacat.httpd.config.ServiceUrl;
import org.tamacat.httpd.core.util.PropertyUtils;
import org.tamacat.httpd.mock.HttpObjectFactory;

public class RedirectHttpHandlerTest {

	@BeforeEach
	public void setUp() throws Exception {
	}

	@AfterEach
	public void tearDown() throws Exception {
	}

	@Test
	public void testSetStatusCode() {
		RedirectHttpHandler handler = new RedirectHttpHandler();
		assertEquals("Found", handler.httpStatus.getReasonPhrase());

		handler.setStatusCode(302);
		assertEquals("Found", handler.httpStatus.getReasonPhrase());

		handler.setStatusCode(301);
		assertEquals("Moved Permanently", handler.httpStatus.getReasonPhrase());
	}

	/**
	 * @param servicePath service path of the {@code <url>}
	 * @param reverse the {@code <reverse>} URL
	 */
	RedirectHttpHandler createHandler(String servicePath, String reverse) throws Exception {
		ServerConfig serverConfig = new ServerConfig(PropertyUtils.getProperties("server.properties"));
		ServiceUrl serviceUrl = new ServiceUrl(serverConfig);
		serviceUrl.setPath(servicePath);
		serviceUrl.setType(ServiceType.REVERSE);
		serviceUrl.setHost(new URI("http://localhost" + servicePath).toURL());
		DefaultReverseUrl reverseUrl = new DefaultReverseUrl(serviceUrl);
		reverseUrl.setReverse(new URI(reverse).toURL());
		serviceUrl.setReverseUrl(reverseUrl);

		RedirectHttpHandler handler = new RedirectHttpHandler();
		handler.setServiceUrl(serviceUrl);
		return handler;
	}

	@Test
	public void testDoRequestRedirectsToTheConfiguredBackend() throws Exception {
		RedirectHttpHandler handler = createHandler("/test/", "http://localhost:8080/examples/");
		ClassicHttpRequest request = HttpObjectFactory.createHttpRequest("GET", "/test/path?param=value");
		ClassicHttpResponse response = HttpObjectFactory.createHttpResponse(200, "OK");
		HttpContext context = HttpObjectFactory.createHttpContext();

		handler.doRequest(request, response, context);

		assertEquals(302, response.getCode());
		assertEquals("http://localhost:8080/examples/path?param=value",
			response.getFirstHeader("Location").getValue());
	}

	/**
	 * A service path without a trailing "/" and a reverse URL without a path put the
	 * rest of the request path right after "host:port". "@evil.example" there must not
	 * make the redirect leave the configured backend.
	 */
	@Test
	public void testDoRequestNeverRedirectsToAnotherHost() throws Exception {
		RedirectHttpHandler handler = createHandler("/test", "http://localhost:8080");
		ClassicHttpRequest request = HttpObjectFactory.createHttpRequest("GET", "/test@evil.example/x");
		ClassicHttpResponse response = HttpObjectFactory.createHttpResponse(200, "OK");
		HttpContext context = HttpObjectFactory.createHttpContext();

		handler.doRequest(request, response, context);

		assertEquals(404, response.getCode());
		assertNull(response.getFirstHeader("Location"));
		for (Header h : response.getHeaders()) {
			assertFalse(h.getValue().contains("evil.example"), h.toString());
		}
	}
}
