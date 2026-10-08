/*
 * Copyright (c) 2026 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.tamacat.httpd.mock.ScriptedBackendServer.bodyFor;
import static org.tamacat.httpd.mock.ScriptedBackendServer.contentLength;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.HttpServerRequestHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
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
 * <p>Regression tests: a backend connection whose response body was never read
 * must not be reused for the next request of the same inbound connection.
 *
 * <p>When an exception on the proxy side drops the backend response after it was
 * received but before its entity reached the client, the body stays unread on the
 * backend connection. That connection is still open and {@code isStale()} is false
 * (unread data is available), so it used to be reused, and the next exchange parsed
 * the leftover body as its status line: an unrelated request failed with 503.
 *
 * <p>In the running server this needs the error response to keep the inbound
 * connection alive. A plain exception becomes 503, for which
 * {@code HttpResponseConnControl} sends {@code Connection: close}; the worker then
 * exits and closes its backend connections, hiding the defect. An HttpException
 * with another status (401, 403, 404, 500, ...) keeps the inbound connection, and
 * the defect showed (verified against the packaged server with a 403). These
 * tests drive the request handler directly, so the status does not change their
 * outcome; the response filter case throws a 403 to mirror that trigger.
 *
 * <p>Every exchange here runs through the real request path: a fresh context per
 * request that shares one backend connection map (as {@code DefaultWorker} does),
 * {@link TamacatHttpServerRequestHandler} as the server request handler, and a
 * response trigger that sends the entity to the end and then closes the response,
 * the way core5's {@code HttpService.submitResponse} does.
 */
@Timeout(30)
public class ReverseProxyHandlerBackendReuseTest {

	ScriptedBackendServer backend;
	final Map<HttpHost, ClientHttpConnection> backendConns = new HashMap<>();

	@AfterEach
	public void tearDown() throws Exception {
		for (ClientHttpConnection conn : backendConns.values()) {
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
		return exchange;
	}

	ClientHttpConnection currentBackendConn() {
		assertEquals(1, backendConns.size(), "a single backend target host is in use");
		return backendConns.values().iterator().next();
	}

	/**
	 * Control for the tests below: without any exception the backend connection is
	 * reused across requests. A fix that simply stopped reusing connections would
	 * make the other tests pass and fail this one.
	 */
	@Test
	public void testBackendConnectionIsReusedAcrossRequests() throws Exception {
		startBackend();
		ReverseProxyHandler handler = handler(new ReverseProxyHandler());

		for (int i = 1; i <= 3; i++) {
			Exchange exchange = exchange(handler, "/test/r" + i + ".html");
			assertEquals(200, exchange.status);
			assertEquals(bodyFor(1, i, "GET /examples/r" + i + ".html HTTP/1.1"), exchange.body);
		}
		assertEquals(1, backend.getConnectionCount(), "all three requests must share one backend connection");
	}

	/** (a) The backend response is dropped inside forwardRequest: a response interceptor throws. */
	@Test
	public void testResponseInterceptorFailureDoesNotPoisonNextRequest() throws Exception {
		startBackend();
		AtomicBoolean failOnce = new AtomicBoolean(true);
		ReverseProxyHandler handler = new ReverseProxyHandler();
		handler.addHttpResponseInterceptor((response, entity, context) -> {
			if (failOnce.getAndSet(false)) {
				throw new IllegalStateException("simulated response interceptor failure");
			}
		});
		assertNextRequestIsServedOnFreshConnection(handler(handler), 503);
	}

	/**
	 * (b) The backend response is dropped in doRequest after forwardRequest returned
	 * but before the entity was handed to the client response: rewriting the
	 * Location header throws.
	 */
	@Test
	public void testFailureBeforeEntityHandOffDoesNotPoisonNextRequest() throws Exception {
		startBackend("Location: http://127.0.0.1/examples/moved.html");
		AtomicBoolean failOnce = new AtomicBoolean(true);
		assertNextRequestIsServedOnFreshConnection(handler(new ReverseProxyHandler(), failOnce), 503);
		assertFalse(failOnce.get(), "the simulated failure must actually have fired");
	}

	/**
	 * (c) The backend entity reached the client response, but a response filter
	 * throws and TamacatHttpServerRequestHandler replaces it with an error page.
	 * A 403 keeps the inbound connection alive in the running server.
	 */
	@Test
	public void testResponseFilterFailureDoesNotPoisonNextRequest() throws Exception {
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
		assertNextRequestIsServedOnFreshConnection(handler(handler), 403);
	}

	void assertNextRequestIsServedOnFreshConnection(ReverseProxyHandler handler, int failedStatus) throws Exception {
		Exchange first = exchange(handler, "/test/r1.html");
		assertEquals(failedStatus, first.status, "the failing request itself is answered with an error page");
		ClientHttpConnection firstConn = currentBackendConn();
		assertTrue(firstConn.isOpen(), "precondition: the connection with the unread body is still open");

		Exchange second = exchange(handler, "/test/r2.html");
		assertEquals(200, second.status, "the next, unrelated request must not fail");
		//The body names the exchange: it proves the response belongs to this request,
		//read on a new backend connection, and not to the dropped first exchange.
		assertEquals(bodyFor(2, 1, "GET /examples/r2.html HTTP/1.1"), second.body);
		assertEquals(2, backend.getConnectionCount());
		assertFalse(firstConn.isOpen(), "the abandoned connection must be closed, not leaked");
		assertNotSame(firstConn, currentBackendConn());

		Exchange third = exchange(handler, "/test/r3.html");
		assertEquals(200, third.status);
		assertEquals(bodyFor(2, 2, "GET /examples/r3.html HTTP/1.1"), third.body,
			"the replacement connection is itself reused once its response was read");
	}
}
