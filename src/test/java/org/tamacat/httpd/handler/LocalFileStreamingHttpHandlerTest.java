package org.tamacat.httpd.handler;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;

import org.tamacat.httpcore4.HttpRequest;
import org.tamacat.httpcore4.HttpResponse;
import org.tamacat.httpcore4.protocol.HttpContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tamacat.httpd.config.ServiceUrl;
import org.tamacat.httpd.mock.HttpObjectFactory;

public class LocalFileStreamingHttpHandlerTest {

	HttpRequest request = HttpObjectFactory.createHttpRequest("GET", "/");
	HttpResponse response = HttpObjectFactory.createHttpResponse(200, "OK");
	HttpContext context = HttpObjectFactory.createHttpContext();
	LocalFileStreamingHttpHandler handler;

	@BeforeEach
	public void setUp() throws Exception {
		handler = new LocalFileStreamingHttpHandler();

		ServiceUrl serviceUrl = new ServiceUrl();
		serviceUrl.setPath("/");
		handler.setServiceUrl(serviceUrl);
		handler.setListings(true);
		handler.setDocsRoot("./src/test/resources/htdocs/root/");
		handler.doRequest(request, response, context);
	}

	@AfterEach
	public void tearDown() throws Exception {
	}

	@Test
	public void testDoRequest() throws Exception {
		handler.doRequest(request, response, context);
	}

	@Test
	public void testSetAcceptRanges() {
		handler.setAcceptRanges(true);
		assertTrue(handler.acceptRanges);
	}

	@Test
	public void testSetBufferSize() {
		handler.setBufferSize(8192);
	}

	@Test
	public void testPartialContent() {
		handler.partialContent(response, new File("./src/test/resources/htdocs/root/index.html"), 0, 10);
	}

}
