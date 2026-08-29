package org.tamacat.httpd.core;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Map;

import javax.net.ssl.SSLHandshakeException;

import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.impl.io.HttpService;
import org.apache.hc.core5.http.io.HttpServerConnection;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.httpd.handler.TamacatHttpServerRequestHandler;
import org.tamacat.httpd.handler.UriHttpRequestHandlerMapper;
import org.tamacat.httpd.mock.DummySocket;
import org.tamacat.httpd.mock.TrackingClientHttpConnection;
import org.slf4j.MDC;

public class DefaultWorkerTest {

	DefaultWorker worker;

	@BeforeEach
	public void setUp() throws Exception {
		//core5 has no doService() extension point: DefaultHttpService is replaced by
		//impl.io.HttpService with a TamacatHttpServerRequestHandler injected into it.
		HttpService httpService = new HttpService(
				new HttpProcessorBuilder().build(),
				new TamacatHttpServerRequestHandler(new UriHttpRequestHandlerMapper()),
				new KeepAliveConnReuseStrategy(), null);
		worker = new DefaultWorker();
		worker.setServerConfig(new ServerConfig());
		worker.setSocket(new DummySocket());
		worker.setHttpService(httpService);
	}

	@AfterEach
	public void tearDown() throws Exception {
		worker.shutdown(worker.conn);
	}

	@Test
	public void testRun() {
		new Thread(worker).start();
	}

	@Test
	public void testHandleException() {
		worker.handleException(new SSLHandshakeException("test"));
		worker.handleException(new SocketException("test"));
		worker.handleException(new ConnectionClosedException("test"));
		worker.handleException(new SocketTimeoutException("test"));
		worker.handleException(new UncheckedIOException(new IOException("test")));
	}

	@Test
	public void testIsClosed() throws Exception {
		assertFalse(worker.isClosed());

		//worker.shutdown(
				worker.conn.close();//);
		//assertTrue(worker.isClosed());
	}

	/**
	 * core-absorption BR-3: org.tamacat.log.DiagnosticContext -> org.slf4j.MDC.
	 * shutdown() previously called DiagnosticContext#remove(), now calls MDC#clear();
	 * verify the logging context is actually cleared (happy-path floor for the
	 * slf4j logging conversion, per code-generation-plan Step 13).
	 */
	@Test
	public void testShutdownClearsMDC() {
		MDC.put("ip", "127.0.0.1");
		MDC.put("user", "tester");
		assertNotNull(MDC.get("ip"));

		worker.shutdown(worker.conn);

		assertNull(MDC.get("ip"));
		assertNull(MDC.get("user"));
	}

	/**
	 * {@link HttpService} test double that captures the {@link HttpContext}
	 * {@code run()} passes to {@code handleRequest()}, and can optionally inject a
	 * backend connection - under a caller-chosen target host key, into the shared
	 * {@code Map<HttpHost, ClientHttpConnection>} that {@code run()} put under
	 * {@code HTTP_OUT_CONN} - and/or throw, so FR-1/FR-3's wiring inside
	 * {@code DefaultWorker.run()} can be exercised without a real HTTP exchange
	 * over the (garbage-content) {@link DummySocket} stream. {@code toThrow} is
	 * also how these tests force {@code run()}'s {@code while} loop to stop
	 * after exactly one iteration. Putting into the map mirrors exactly what
	 * {@code ReverseProxyHandler.getClientHttpConnection()} does in production.
	 * <p>{@code capturedConnsSnapshot} is a defensive copy of the map taken at
	 * {@code handleRequest()} time, not the live map itself: {@code run()}'s
	 * {@code finally} shuts down and {@code clear()}s {@code backendConns} once
	 * the (single, {@code toThrow}-forced) loop iteration ends, so asserting
	 * against the live map after {@code run()} returns would only ever see it
	 * empty.
	 */
	static class CapturingHttpService extends HttpService {
		HttpContext capturedContext;
		Map<HttpHost, ClientHttpConnection> capturedConnsSnapshot;
		HttpHost hostToInject;
		TrackingClientHttpConnection connToInject;
		RuntimeException toThrow;

		CapturingHttpService() {
			super(new HttpProcessorBuilder().build(),
				new TamacatHttpServerRequestHandler(new UriHttpRequestHandlerMapper()),
				new KeepAliveConnReuseStrategy(), null);
		}

		@Override
		public void handleRequest(HttpServerConnection conn, HttpContext context)
				throws IOException, org.apache.hc.core5.http.HttpException {
			capturedContext = context;
			@SuppressWarnings("unchecked")
			Map<HttpHost, ClientHttpConnection> conns =
				(Map<HttpHost, ClientHttpConnection>) context.getAttribute(HttpContextKeys.HTTP_OUT_CONN);
			if (connToInject != null) {
				conns.put(hostToInject, connToInject);
			}
			capturedConnsSnapshot = new java.util.HashMap<>(conns);
			if (toThrow != null) {
				throw toThrow;
			}
		}
	}

	/**
	 * FR-3: the per-request {@code HttpContext} that {@code run()} creates must
	 * be a {@code HttpCoreContext}, not the deprecated {@code BasicHttpContext}.
	 */
	@Test
	public void testContextIsHttpCoreContext() {
		CapturingHttpService service = new CapturingHttpService();
		service.toThrow = new RuntimeException("stop after first iteration");
		worker.setHttpService(service);

		worker.run();

		assertNotNull(service.capturedContext);
		assertTrue(service.capturedContext instanceof HttpCoreContext);
	}

	/**
	 * FR-1/BR-1 (revised): the worker's own backend-connection map - the same
	 * instance every request, never a copy - must be exposed (by reference) on
	 * the new request's context, so {@code ReverseProxyHandler} can find and
	 * reuse whichever target host's entry (if any) is already there.
	 * <p>Checked via {@code capturedConnsSnapshot} (taken while
	 * {@code handleRequest()} runs), not {@code worker.backendConns} after
	 * {@code run()} returns: BR-2's exit cleanup shuts down and clears that map
	 * once the (single, forced) loop iteration ends, so it is always empty by
	 * the time {@code run()} returns.
	 */
	@Test
	public void testBackendConnCarriedIntoContext() {
		HttpHost host = new HttpHost("http", "backend.example", 8080);
		TrackingClientHttpConnection preset =
			new TrackingClientHttpConnection(new ServerConfig(), true, false);
		worker.backendConns.put(host, preset);

		CapturingHttpService service = new CapturingHttpService();
		service.toThrow = new RuntimeException("stop after first iteration");
		worker.setHttpService(service);

		worker.run();

		Object attr = service.capturedContext.getAttribute(HttpContextKeys.HTTP_OUT_CONN);
		assertSame(worker.backendConns, attr, "the worker's own backend-connection map instance must be shared "
			+ "into the context, not a copy");
		assertSame(preset, service.capturedConnsSnapshot.get(host), "the preset entry for this target host must be visible while "
			+ "handling the request");
	}

	/**
	 * FR-1/BR-2 (revised): because {@code run()} shares its own
	 * {@code backendConns} map instance into the context (never a copy), a
	 * connection a request handler stores into that map is immediately visible -
	 * without an explicit write-back step - and is still shutdown at worker exit
	 * even though {@code handleRequest()} itself throws mid-request. BR-2a's
	 * explicit per-request write-back step is retired by this revision: there is
	 * nothing left to write back, since the mutation already happened in the
	 * shared instance (the "takeoshi nashi" / no-drop requirement is now met
	 * structurally, not by an extra sync step).
	 */
	@Test
	public void testBackendConnVisibleAfterExceptionBecauseMapIsShared() {
		HttpHost host = new HttpHost("http", "backend.example", 8080);
		TrackingClientHttpConnection newConn =
			new TrackingClientHttpConnection(new ServerConfig(), true, false);

		CapturingHttpService service = new CapturingHttpService();
		service.hostToInject = host;
		service.connToInject = newConn;
		service.toThrow = new RuntimeException("simulated mid-request failure");
		worker.setHttpService(service);

		worker.run();

		assertSame(newConn, service.capturedConnsSnapshot.get(host), "a backend connection stored into the shared map mid-request must be "
			+ "visible immediately - without an explicit write-back step - because the "
			+ "map itself is the shared mutable state");
		assertTrue(newConn.closeModeCalled, "a backend connection created mid-request must still be shutdown at "
			+ "worker exit even though handleRequest() threw - it must not be dropped "
			+ "(the \"takeoshi nashi\" / no-drop requirement)");
	}

	/**
	 * BR-2 (revised): when the worker thread exits (the {@code while} loop is
	 * left, normally or via exception), both the client-side connection and
	 * every backend connection the worker is holding must be shutdown, at the
	 * same time, and the map cleared afterward.
	 */
	@Test
	public void testShutdownClosesClientAndBackendConnOnWorkerExit() {
		HttpHost host = new HttpHost("http", "backend.example", 8080);
		TrackingClientHttpConnection preset =
			new TrackingClientHttpConnection(new ServerConfig(), true, false);
		worker.backendConns.put(host, preset);

		CapturingHttpService service = new CapturingHttpService();
		service.toThrow = new RuntimeException("stop after first iteration");
		worker.setHttpService(service);

		worker.run();

		assertTrue(preset.closeModeCalled, "the backend connection must be shutdown when the worker exits (BR-2)");
		assertFalse(worker.conn.isOpen(), "the client connection must also be shutdown");
		assertTrue(worker.backendConns.isEmpty(), "the map must be cleared once every entry has been shutdown");
	}

	/**
	 * BR-2 (revised): a worker that forwarded to more than one distinct backend
	 * target in the same inbound (keep-alive) connection holds more than one
	 * map entry - at worker exit, every one of them must be shutdown, not just
	 * one. This is the direct regression test for the §12a code-generation
	 * review iteration-1 defect: a single-field design could only ever track
	 * (and shutdown) one backend connection, silently leaking any others.
	 */
	@Test
	public void testShutdownClosesAllBackendConnectionsForMultipleTargetHostsOnWorkerExit() {
		HttpHost hostA = new HttpHost("http", "backend-a.example", 8080);
		HttpHost hostB = new HttpHost("http", "backend-b.example", 9090);
		TrackingClientHttpConnection presetA =
			new TrackingClientHttpConnection(new ServerConfig(), true, false);
		TrackingClientHttpConnection presetB =
			new TrackingClientHttpConnection(new ServerConfig(), true, false);
		worker.backendConns.put(hostA, presetA);
		worker.backendConns.put(hostB, presetB);

		CapturingHttpService service = new CapturingHttpService();
		service.toThrow = new RuntimeException("stop after first iteration");
		worker.setHttpService(service);

		worker.run();

		assertTrue(presetA.closeModeCalled, "target A's connection must be shutdown when the worker exits (BR-2)");
		assertTrue(presetB.closeModeCalled, "target B's connection must also be shutdown when the worker exits (BR-2)");
		assertTrue(worker.backendConns.isEmpty(), "the map must be cleared once every entry has been shutdown");
	}
}
