package org.tamacat.httpd.core;

import static org.junit.Assert.*;

import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tamacat.httpd.mock.DummySocket;
import org.tamacat.httpd.mock.HttpObjectFactory;

public class KeepAliveConnReuseStrategyTest {

	KeepAliveConnReuseStrategy reuse;
	ClassicHttpResponse response;
	HttpContext context = new HttpCoreContext();

	@Before
	public void setUp() throws Exception {
		reuse = new KeepAliveConnReuseStrategy();
		reuse.setKeepAliveTimeout(1500);
		reuse.setMaxKeepAliveRequests(100);
		response = HttpObjectFactory.createHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testSetKeepAliveTimeout() {
		reuse.setKeepAliveTimeout(0);
		assertEquals(0, reuse.keepAliveTimeout);
	}

	@Test
	public void testSetMaxKeepAliveRequests() {
		reuse.setMaxKeepAliveRequests(0);

	}

	@Test
	public void testSetDisabledKeepAlive() {
		reuse.setDisabledKeepAlive(true);
		//core5's ConnectionReuseStrategy takes the request as well; 4.4 passed only the response.
		assertFalse(reuse.keepAlive(
			HttpObjectFactory.createHttpRequest("GET", "/"), response, context));
	}

	@Test
	public void testKeepAliveHttpResponseHttpContext() {

	}

	@Test
	public void testKeepAliveCheck_HTTP_1_1() {
		assertTrue(reuse.keepAliveCheck(response, context));

		response.setHeader(HttpHeaders.CONNECTION, HeaderElements.CLOSE);
		assertFalse(reuse.keepAliveCheck(response, context));
	}

	@Test
	public void testKeepAliveCheck_HTTP_1_0() {
		response = HttpObjectFactory.createHttpResponse(HttpVersion.HTTP_1_0, 200, "OK");

		response.setHeader(HttpHeaders.CONTENT_LENGTH, "123");
		assertFalse(reuse.keepAliveCheck(response, context));

		response.setHeader(HttpHeaders.CONNECTION, HeaderElements.CLOSE);
		assertFalse(reuse.keepAliveCheck(response, context));

		response.setHeader(HttpHeaders.CONNECTION, HeaderElements.KEEP_ALIVE);
		assertTrue(reuse.keepAliveCheck(response, context));
	}

	@Test
	public void testKeepAliveCheck_TransferEncoding() {
		response = HttpObjectFactory.createHttpResponse(HttpVersion.HTTP_1_0, 200, "OK");

		response.setHeader(HttpHeaders.TRANSFER_ENCODING, "none");
		response.removeHeaders(HttpHeaders.CONTENT_LENGTH);
		response.setHeader(HttpHeaders.CONNECTION, HeaderElements.KEEP_ALIVE);
		assertFalse(reuse.keepAliveCheck(response, context));

		response.setHeader(HttpHeaders.TRANSFER_ENCODING, "chunked");
		response.removeHeaders(HttpHeaders.CONTENT_LENGTH);
		response.setHeader(HttpHeaders.CONNECTION, HeaderElements.KEEP_ALIVE);
		assertTrue(reuse.keepAliveCheck(response, context));
	}


	@Test
	public void testIsKeepAliveTimeout() throws Exception {
		assertFalse(reuse.isKeepAliveTimeout(context));

		context.setAttribute(HttpContextKeys.HTTP_IN_CONN, new Object());
		assertFalse(reuse.isKeepAliveTimeout(context));

		ServerHttpConnection conn = new ServerHttpConnection(8192);
		//The connection must be bound: core5 moved the request counter from
		//HttpConnection#getMetrics() (which existed on an unbound 4.4 connection and
		//reported 0) onto EndpointDetails, which is null until bind(). The
		//maxKeepAliveRequests branch below only has meaning on a bound connection,
		//which is the only state DefaultWorker ever consults it in.
		conn.bind(new DummySocket());
		context.setAttribute(HttpContextKeys.HTTP_IN_CONN, conn);
		assertFalse(reuse.isKeepAliveTimeout(context));

		reuse.setKeepAliveTimeout(1);
		Thread.sleep(100);
		assertTrue(reuse.isKeepAliveTimeout(context));

		reuse.setKeepAliveTimeout(5000);
		reuse.setMaxKeepAliveRequests(0);
		assertTrue(reuse.isKeepAliveTimeout(context));
	}

	/**
	 * An unbound connection has no EndpointDetails, so the request count is unknown.
	 * The maxKeepAliveRequests limit must not fire on an unknown count.
	 */
	@Test
	public void testIsKeepAliveTimeout_unboundConnection() {
		ServerHttpConnection conn = new ServerHttpConnection(8192);
		context.setAttribute(HttpContextKeys.HTTP_IN_CONN, conn);
		reuse.setKeepAliveTimeout(5000);
		reuse.setMaxKeepAliveRequests(0);
		assertFalse(reuse.isKeepAliveTimeout(context));
	}

	@Test
	public void testCanResponseHaveBody() {
		response.setCode(200);
		assertTrue(reuse.canResponseHaveBody(response));

		response.setCode(204);
		assertFalse(reuse.canResponseHaveBody(response));

		response.setCode(304);
		assertFalse(reuse.canResponseHaveBody(response));

		response.setCode(205);
		assertFalse(reuse.canResponseHaveBody(response));

		response.setCode(404);
		assertTrue(reuse.canResponseHaveBody(response));
	}

}
