/*
 * Copyright (c) 2026 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.tamacat.httpd.mock.ScriptedBackendServer.bodyFor;
import static org.tamacat.httpd.mock.ScriptedBackendServer.chunked;
import static org.tamacat.httpd.mock.ScriptedBackendServer.contentLength;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.HttpServerRequestHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.io.CloseMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.tamacat.httpd.config.DefaultReverseUrl;
import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.httpd.config.ServiceType;
import org.tamacat.httpd.config.ServiceUrl;
import org.tamacat.httpd.core.ClientHttpConnection;
import org.tamacat.httpd.core.HttpContextKeys;
import org.tamacat.httpd.exception.ForbiddenException;
import org.tamacat.httpd.filter.ResponseFilter;
import org.tamacat.httpd.mock.ScriptedBackendServer;
import org.tamacat.httpd.util.RequestUtils;

/**
 * <p>Backend connections of the reverse proxy, end to end against a loopback
 * backend: backend keep-alive is off, so every request gets a connection of its
 * own, sends {@code Connection: close}, and the connection is closed once the
 * response has been sent.
 *
 * <p>Every exchange runs through the real request path: a fresh context per
 * request that carries the worker's backend-connection list,
 * {@link TamacatHttpServerRequestHandler} as the server request handler, and a
 * response trigger that sends the entity to the end and then closes the response,
 * the way core5's {@code HttpService.submitResponse} does. After each exchange the
 * list's connections are closed and the list cleared - what {@code DefaultWorker}
 * does once {@code handleRequest} returns (covered by {@code DefaultWorkerTest}).
 *
 * <p>The failure cases are regression tests from the time 2.0 reused backend
 * connections: an exception dropped a received response before its body was read,
 * the connection was reused, and the next request parsed the leftover body as its
 * status line and failed with 503. With a connection per request that cannot
 * happen; these tests keep it that way.
 */
@Timeout(30)
public class ReverseProxyHandlerBackendConnectionTest {

	ScriptedBackendServer backend;
	final List<ClientHttpConnection> backendConns = new ArrayList<>(); //DefaultWorker.backendConns
	final List<ClientHttpConnection> opened = new ArrayList<>();

	@AfterEach
	public void tearDown() throws Exception {
		for (ClientHttpConnection conn : opened) {
			conn.close();
		}
		if (backend != null) {
			backend.close();
		}
	}

	/** Each response names its own exchange; the body is never empty. */
	void startBackend(String... extraHeaders) throws IOException {
		backend = new ScriptedBackendServer((connectionNo, requestNo, requestLine) ->
			contentLength(bodyFor(connectionNo, requestNo, requestLine), extraHeaders));
	}

	ReverseProxyHandler handler(ReverseProxyHandler handler) throws Exception {
		return handler(handler, null);
	}

	ReverseProxyHandler handler(ReverseProxyHandler handler, AtomicBoolean failReverseUrlOnce) throws Exception {
		Properties props = new Properties();
		props.setProperty("ServerName", "tamacat-test");
		props.setProperty("BackEndSocketTimeout", "3000");
		ServiceUrl serviceUrl = new ServiceUrl(new ServerConfig(props));
		serviceUrl.setPath("/test/");
		serviceUrl.setType(ServiceType.REVERSE);
		serviceUrl.setHost(new URI("http://localhost/test/").toURL());
		DefaultReverseUrl reverseUrl = failReverseUrlOnce == null
			? new DefaultReverseUrl(serviceUrl)
			: new DefaultReverseUrl(serviceUrl) {
				@Override
				public String getConvertRequestedUrl(String path) {
					if (failReverseUrlOnce.getAndSet(false)) {
						throw new IllegalStateException("simulated failure while rewriting the response");
					}
					return super.getConvertRequestedUrl(path);
				}
			};
		reverseUrl.setReverse(new URI("http://127.0.0.1:" + backend.getPort() + "/examples/").toURL());
		serviceUrl.setReverseUrl(reverseUrl);
		handler.setServiceUrl(serviceUrl);
		return handler;
	}

	static final class Exchange {
		int status;
		String body;
	}

	Exchange exchange(ReverseProxyHandler handler, String path) throws Exception {
		HttpServerRequestHandler server = new TamacatHttpServerRequestHandler((request, context) -> handler);
		HttpContext context = new HttpCoreContext();
		context.setAttribute(HttpContextKeys.HTTP_OUT_CONN, backendConns);
		context.setAttribute(RequestUtils.REMOTE_ADDRESS, InetAddress.getLoopbackAddress());
		ClassicHttpRequest request = new BasicClassicHttpRequest("GET", path);
		request.setHeader("Host", "localhost");
		Exchange exchange = new Exchange();
		server.handle(request, new HttpServerRequestHandler.ResponseTrigger() {
			@Override
			public void sendInformation(ClassicHttpResponse response) {
			}

			@Override
			public void submitResponse(ClassicHttpResponse response) throws IOException {
				try {
					exchange.status = response.getCode();
					exchange.body = response.getEntity() != null ? EntityUtils.toString(response.getEntity()) : null;
				} catch (Exception e) {
					throw new IOException(e);
				} finally {
					response.close();
				}
			}
		}, context);
		//What DefaultWorker does once handleRequest() has returned.
		opened.addAll(backendConns);
		for (ClientHttpConnection conn : backendConns) {
			conn.close(CloseMode.GRACEFUL);
		}
		backendConns.clear();
		return exchange;
	}

	/**
	 * Three requests on one inbound connection use three backend connections, and
	 * each is closed after its response - the backend sees all three closed.
	 */
	@Test
	public void testEachRequestUsesABackendConnectionOfItsOwn() throws Exception {
		startBackend();
		ReverseProxyHandler handler = handler(new ReverseProxyHandler());

		for (int i = 1; i <= 3; i++) {
			Exchange exchange = exchange(handler, "/test/r" + i + ".html");
			assertEquals(200, exchange.status);
			assertEquals(bodyFor(i, 1, "GET /examples/r" + i + ".html HTTP/1.1"), exchange.body,
				"request " + i + " must be the first request on a connection of its own");
		}
		assertEquals(3, backend.getConnectionCount());
		assertEquals(3, backend.awaitClosedByClient(3, 2000), "every backend connection must be closed by the proxy");
	}

	/**
	 * The backend is told that the connection carries a single request, so it does
	 * not keep it idle; the RequestConnControl interceptor would otherwise send
	 * "keep-alive".
	 */
	@Test
	public void testConnectionCloseIsSentToTheBackend() throws Exception {
		startBackend();
		ReverseProxyHandler handler = handler(new ReverseProxyHandler());

		exchange(handler, "/test/r1.html");
		exchange(handler, "/test/r2.html");

		assertEquals(List.of("close", "close"), backend.getConnectionHeaders());
	}

	/**
	 * The response body is streamed from the backend connection after the handler
	 * has returned, so the connection must stay open until the response has been
	 * sent: closing it in the handler would cut the body short. The body is far
	 * larger than the connection's 8 KB input buffer, so most of it is still on the
	 * socket when the handler returns - a small body would already be buffered and
	 * pass even if the connection had been closed early.
	 */
	@Test
	public void testStreamedBodyIsSentBeforeTheConnectionIsClosed() throws Exception {
		String chunk = "x".repeat(100_000);
		backend = new ScriptedBackendServer((connectionNo, requestNo, requestLine) ->
			chunked("<begin>" + chunk, chunk, chunk + "<end>"));
		ReverseProxyHandler handler = handler(new ReverseProxyHandler());

		Exchange exchange = exchange(handler, "/test/stream.html");

		assertEquals(200, exchange.status);
		assertEquals(300_000 + "<begin>".length() + "<end>".length(), exchange.body.length());
		assertTrue(exchange.body.startsWith("<begin>") && exchange.body.endsWith("<end>"));
	}

	/** (a) The backend response is dropped inside forwardRequest: a response interceptor throws. */
	@Test
	public void testResponseInterceptorFailureDoesNotAffectNextRequest() throws Exception {
		startBackend();
		AtomicBoolean failOnce = new AtomicBoolean(true);
		ReverseProxyHandler handler = new ReverseProxyHandler();
		handler.addHttpResponseInterceptor((response, entity, context) -> {
			if (failOnce.getAndSet(false)) {
				throw new IllegalStateException("simulated response interceptor failure");
			}
		});
		assertNextRequestIsServed(handler(handler), 503);
	}

	/**
	 * (b) The backend response is dropped in doRequest after forwardRequest returned
	 * but before the entity was handed to the client response: rewriting the
	 * Location header throws.
	 */
	@Test
	public void testFailureBeforeEntityHandOffDoesNotAffectNextRequest() throws Exception {
		startBackend("Location: http://127.0.0.1/examples/moved.html");
		AtomicBoolean failOnce = new AtomicBoolean(true);
		assertNextRequestIsServed(handler(new ReverseProxyHandler(), failOnce), 503);
		assertFalse(failOnce.get(), "the simulated failure must actually have fired");
	}

	/**
	 * (c) The backend entity reached the client response, but a response filter
	 * throws and TamacatHttpServerRequestHandler replaces it with an error page.
	 * A 403 keeps the inbound connection alive in the running server, which is
	 * when a reused backend connection used to fail the next request.
	 */
	@Test
	public void testResponseFilterFailureDoesNotAffectNextRequest() throws Exception {
		startBackend();
		AtomicBoolean failOnce = new AtomicBoolean(true);
		ReverseProxyHandler handler = new ReverseProxyHandler();
		handler.setHttpFilter(new ResponseFilter() {
			@Override
			public void init(ServiceUrl serviceUrl) {
			}

			@Override
			public void afterResponse(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context) {
				if (failOnce.getAndSet(false)) {
					throw new ForbiddenException("simulated response filter failure");
				}
			}
		});
		assertNextRequestIsServed(handler(handler), 403);
	}

	void assertNextRequestIsServed(ReverseProxyHandler handler, int failedStatus) throws Exception {
		Exchange first = exchange(handler, "/test/r1.html");
		assertEquals(failedStatus, first.status, "the failing request itself is answered with an error page");
		assertFalse(opened.get(0).isOpen(), "the failed request's backend connection is closed with it");

		Exchange second = exchange(handler, "/test/r2.html");
		assertEquals(200, second.status, "the next, unrelated request must not fail");
		assertEquals(bodyFor(2, 1, "GET /examples/r2.html HTTP/1.1"), second.body);
	}
}
