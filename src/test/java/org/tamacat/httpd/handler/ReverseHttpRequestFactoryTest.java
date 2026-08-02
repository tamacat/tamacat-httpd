package org.tamacat.httpd.handler;

import static org.junit.Assert.*;

import java.net.URL;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tamacat.httpd.config.DefaultReverseUrl;
import org.tamacat.httpd.config.ReverseUrl;
import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.httpd.config.ServiceUrl;
import org.tamacat.httpd.mock.HttpObjectFactory;

public class ReverseHttpRequestFactoryTest {

	ServiceUrl serviceUrl;
	ReverseUrl reverseUrl;

	@Before
	public void setUp() throws Exception {
		ServerConfig config = new ServerConfig();
		serviceUrl = new ServiceUrl(config);
		serviceUrl.setPath("/");
		reverseUrl = new DefaultReverseUrl(serviceUrl);
		reverseUrl.setReverse(new URL("http://localhost:8080/"));
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testGetInstanceGET() {
		ClassicHttpRequest request = HttpObjectFactory.createHttpRequest("GET", "/");
		ClassicHttpResponse response = HttpObjectFactory.createHttpResponse(200, "OK");
		HttpContext context = HttpObjectFactory.createHttpContext();

		//Up to 1.5 the factory returned ReverseHttpRequest for a request without an
		//entity and ReverseHttpEntityEnclosingRequest for one with. ADR-007 collapsed
		//both branches into ReverseHttpRequest, so the returned type no longer
		//discriminates; the entity itself does.
		ReverseHttpRequest target = ReverseHttpRequestFactory.getInstance(
			request, response, context, reverseUrl, HttpVersion.HTTP_1_1);
		assertNotNull(target);
		assertNull(target.getEntity());
	}

	@Test
	public void testGetInstancePOST() {
		ClassicHttpRequest request = HttpObjectFactory.createHttpRequest("POST", "/");
		ClassicHttpResponse response = HttpObjectFactory.createHttpResponse(200, "OK");
		HttpContext context = HttpObjectFactory.createHttpContext();

		request.setEntity(new StringEntity("a=b", ContentType.APPLICATION_FORM_URLENCODED));

		ReverseHttpRequest target = ReverseHttpRequestFactory.getInstance(
			request, response, context, reverseUrl, HttpVersion.HTTP_1_1);
		assertNotNull(target);
		//ADR-007: the entity now travels on ReverseHttpRequest itself.
		assertNotNull(target.getEntity());
	}

}
