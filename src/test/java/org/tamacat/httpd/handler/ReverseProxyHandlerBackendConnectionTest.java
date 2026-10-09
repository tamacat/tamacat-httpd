/*
 * Copyright (c) 2026 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.handler;

import static org.junit.Assert.*;
import static org.tamacat.httpd.mock.ScriptedBackendServer.bodyFor;
import static org.tamacat.httpd.mock.ScriptedBackendServer.chunked;
import static org.tamacat.httpd.mock.ScriptedBackendServer.contentLength;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.junit.After;
import org.junit.Test;
import org.tamacat.httpcore4.HttpRequest;
import org.tamacat.httpcore4.HttpResponse;
import org.tamacat.httpcore4.HttpVersion;
import org.tamacat.httpcore4.message.BasicHttpRequest;
import org.tamacat.httpcore4.protocol.HttpContext;
import org.tamacat.httpcore4.util.EntityUtils;
import org.tamacat.httpd.config.DefaultReverseUrl;
import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.httpd.config.ServiceType;
import org.tamacat.httpd.config.ServiceUrl;
import org.tamacat.httpd.core.ClientHttpConnection;
import org.tamacat.httpd.core.DefaultWorker;
import org.tamacat.httpd.mock.HttpObjectFactory;
import org.tamacat.httpd.mock.ScriptedBackendServer;
import org.tamacat.httpd.util.RequestUtils;

/**
 * <p>Backend connections of the reverse proxy, end to end against a loopback
 * backend: every request gets a connection of its own, sends
 * {@code Connection: Close}, and the connection is closed once the response has
 * been sent. Before this, nothing closed it and it stayed open until garbage
 * collection.
 *
 * <p>Each exchange runs the handler with a fresh context that carries the
 * worker's backend-connection list, reads the response entity to the end (as
 * {@code HttpService} does when it sends the response), then closes the list's
 * connections and clears it - what {@code DefaultWorker} does once
 * {@code handleRequest} returns (covered by {@code DefaultWorkerTest}).
 */
public class ReverseProxyHandlerBackendConnectionTest {

	ScriptedBackendServer backend;
	final List<ClientHttpConnection> backendConns = new ArrayList<>(); //DefaultWorker.backendConns
	final List<ClientHttpConnection> opened = new ArrayList<>();

	@After
	public void tearDown() throws Exception {
		for (ClientHttpConnection conn : opened) {
			conn.shutdown();
		}
		if (backend != null) {
			backend.close();
		}
	}

	ReverseProxyHandler handler() throws Exception {
		Properties props = new Properties();
		props.setProperty("ServerName", "tamacat-test");
		props.setProperty("BackEndSocketTimeout", "3000");
		ServiceUrl serviceUrl = new ServiceUrl(new ServerConfig(props));
		serviceUrl.setPath("/test/");
		serviceUrl.setType(ServiceType.REVERSE);
		serviceUrl.setHost(new URL("http://localhost/test/"));
		DefaultReverseUrl reverseUrl = new DefaultReverseUrl(serviceUrl);
		reverseUrl.setReverse(new URL("http://127.0.0.1:" + backend.getPort() + "/examples/"));
		serviceUrl.setReverseUrl(reverseUrl);
		ReverseProxyHandler handler = new ReverseProxyHandler();
		handler.setServiceUrl(serviceUrl);
		return handler;
	}

	String exchange(ReverseProxyHandler handler, String path) throws IOException {
		HttpContext context = HttpObjectFactory.createHttpContext();
		context.setAttribute(DefaultWorker.HTTP_OUT_CONN, backendConns);
		context.setAttribute(RequestUtils.REMOTE_ADDRESS, InetAddress.getLoopbackAddress());
		HttpRequest request = new BasicHttpRequest("GET", path, HttpVersion.HTTP_1_1);
		request.setHeader("Host", "localhost");
		HttpResponse response = HttpObjectFactory.createHttpResponse(200, "OK");
		handler.handle(request, response, context);
		assertEquals(200, response.getStatusLine().getStatusCode());
		String body = EntityUtils.toString(response.getEntity()); //HttpService sends it
		//What DefaultWorker does once handleRequest() has returned.
		opened.addAll(backendConns);
		for (ClientHttpConnection conn : backendConns) {
			conn.close();
		}
		backendConns.clear();
		return body;
	}

	/**
	 * Three requests use three backend connections, and each is closed after its
	 * response - the backend sees all three closed.
	 */
	@Test(timeout = 30000)
	public void testEachRequestUsesABackendConnectionOfItsOwn() throws Exception {
		backend = new ScriptedBackendServer((connectionNo, requestNo, requestLine) ->
			contentLength(bodyFor(connectionNo, requestNo, requestLine)));
		ReverseProxyHandler handler = handler();

		for (int i = 1; i <= 3; i++) {
			assertEquals("request " + i + " must be the first request on a connection of its own",
				bodyFor(i, 1, "GET /examples/r" + i + ".html HTTP/1.1"), exchange(handler, "/test/r" + i + ".html"));
		}
		assertEquals(3, backend.getConnectionCount());
		assertEquals("every backend connection must be closed by the proxy", 3, backend.awaitClosedByClient(3, 2000));
	}

	/**
	 * The backend is told that the connection carries a single request, so it does
	 * not keep it idle; the RequestConnControl interceptor would otherwise send
	 * "Keep-Alive".
	 */
	@Test(timeout = 30000)
	public void testConnectionCloseIsSentToTheBackend() throws Exception {
		backend = new ScriptedBackendServer((connectionNo, requestNo, requestLine) ->
			contentLength(bodyFor(connectionNo, requestNo, requestLine)));
		ReverseProxyHandler handler = handler();

		exchange(handler, "/test/r1.html");
		exchange(handler, "/test/r2.html");

		assertEquals(Arrays.asList("Close", "Close"), backend.getConnectionHeaders());
	}

	/**
	 * The response body is streamed from the backend connection after the handler
	 * has returned, so the connection must stay open until the response has been
	 * sent: closing it in the handler would cut the body short. The body is far
	 * larger than the connection's input buffer, so most of it is still on the
	 * socket when the handler returns - a small body would already be buffered and
	 * pass even if the connection had been closed early.
	 */
	@Test(timeout = 30000)
	public void testStreamedBodyIsSentBeforeTheConnectionIsClosed() throws Exception {
		char[] filler = new char[100000];
		Arrays.fill(filler, 'x');
		String chunk = new String(filler);
		backend = new ScriptedBackendServer((connectionNo, requestNo, requestLine) ->
			chunked("<begin>" + chunk, chunk, chunk + "<end>"));
		ReverseProxyHandler handler = handler();

		String body = exchange(handler, "/test/stream.html");

		assertEquals(300000 + "<begin>".length() + "<end>".length(), body.length());
		assertTrue(body.startsWith("<begin>") && body.endsWith("<end>"));
	}
}
