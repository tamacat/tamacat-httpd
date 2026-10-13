package org.tamacat.httpd.handler;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;

import org.tamacat.httpcore4.HttpResponse;
import org.tamacat.httpcore4.message.BasicHttpRequest;
import org.tamacat.httpcore4.protocol.HttpContext;
import org.tamacat.httpd.config.DefaultReverseUrl;
import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.httpd.config.ServiceType;
import org.tamacat.httpd.config.ServiceUrl;
import org.tamacat.httpd.core.util.PropertyUtils;
import org.tamacat.httpd.mock.HttpObjectFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
	 * T-URL-4. A request URI the redirect target cannot be built from is answered
	 * with 404, not with the 503 a NullPointerException used to end in. The same
	 * handler still redirects a request without the offending character.
	 */
	@Test
	public void testDoRequestAnswers404WhenTheTargetUrlCannotBeBuilt() throws Exception {
		RedirectHttpHandler handler = new RedirectHttpHandler();
		ServiceUrl serviceUrl = new ServiceUrl(
			new ServerConfig(PropertyUtils.getProperties("server.properties")));
		serviceUrl.setPath("/test/");
		serviceUrl.setType(ServiceType.REVERSE);
		DefaultReverseUrl reverseUrl = new DefaultReverseUrl(serviceUrl);
		reverseUrl.setReverse(URI.create("http://localhost:8080/test2/").toURL());
		serviceUrl.setReverseUrl(reverseUrl);
		handler.setServiceUrl(serviceUrl);
		HttpContext context = HttpObjectFactory.createHttpContext();

		//control
		HttpResponse redirected = HttpObjectFactory.createHttpResponse(200, "OK");
		handler.doRequest(new BasicHttpRequest("GET", "/test/x?a=12"), redirected, context);
		assertEquals(302, redirected.getStatusLine().getStatusCode());
		assertEquals("http://localhost:8080/test2/x?a=12",
			redirected.getFirstHeader("Location").getValue());

		HttpResponse notFound = HttpObjectFactory.createHttpResponse(200, "OK");
		handler.doRequest(new BasicHttpRequest("GET", "/test/x?a=1|2"), notFound, context);
		assertEquals(404, notFound.getStatusLine().getStatusCode());
		assertNull(notFound.getFirstHeader("Location"));
	}
}
