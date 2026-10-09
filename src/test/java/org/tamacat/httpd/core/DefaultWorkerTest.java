package org.tamacat.httpd.core;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.SSLHandshakeException;

import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.impl.io.HttpService;
import org.apache.hc.core5.http.io.HttpServerConnection;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.io.CloseMode;
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
	 * {@link HttpService} test double that stands in for a real HTTP exchange over
	 * the (garbage-content) {@link DummySocket} stream. For each call it records the
	 * context and what the shared {@code HTTP_OUT_CONN} list held when the call
	 * started, then adds the backend connections a reverse-proxy request would have
	 * opened - exactly what {@code ReverseProxyHandler.getClientHttpConnection()}
	 * does - and finally throws on the chosen call, which is also how these tests
	 * stop {@code run()}'s loop.
	 */
	static class CapturingHttpService extends HttpService {
		final List<HttpContext> contexts = new ArrayList<>();
		final List<List<ClientHttpConnection>> connsAtStart = new ArrayList<>();
		final List<Boolean> inboundOpenAtStart = new ArrayList<>();
		final List<List<TrackingClientHttpConnection>> connsToAdd = new ArrayList<>();
		int throwOnCall = 1;
		Throwable toThrow = new RuntimeException("stop the request loop");

		CapturingHttpService() {
			super(new HttpProcessorBuilder().build(),
				new TamacatHttpServerRequestHandler(new UriHttpRequestHandlerMapper()),
				new KeepAliveConnReuseStrategy(), null);
		}

		CapturingHttpService addOnCall(TrackingClientHttpConnection... conns) {
			connsToAdd.add(Arrays.asList(conns));
			return this;
		}

		@Override
		public void handleRequest(HttpServerConnection conn, HttpContext context)
				throws IOException, org.apache.hc.core5.http.HttpException {
			int call = contexts.size() + 1;
			contexts.add(context);
			@SuppressWarnings("unchecked")
			List<ClientHttpConnection> conns =
				(List<ClientHttpConnection>) context.getAttribute(HttpContextKeys.HTTP_OUT_CONN);
			connsAtStart.add(new ArrayList<>(conns));
			inboundOpenAtStart.add(conn.isOpen());
			if (call <= connsToAdd.size()) {
				conns.addAll(connsToAdd.get(call - 1));
			}
			if (call == throwOnCall) {
				if (toThrow instanceof Error) throw (Error) toThrow;
				throw (RuntimeException) toThrow;
			}
		}
	}

	static TrackingClientHttpConnection backendConn() {
		return new TrackingClientHttpConnection(new ServerConfig());
	}

	/**
	 * FR-3: the per-request {@code HttpContext} that {@code run()} creates must
	 * be a {@code HttpCoreContext}, not the deprecated {@code BasicHttpContext}.
	 */
	@Test
	public void testContextIsHttpCoreContext() {
		CapturingHttpService service = new CapturingHttpService();
		worker.setHttpService(service);

		worker.run();

		assertTrue(service.contexts.get(0) instanceof HttpCoreContext);
	}

	/**
	 * The worker's own backend-connection list - the same instance for every
	 * request, never a copy - is what each request's context carries, so the
	 * connections ReverseProxyHandler adds are the ones the worker closes.
	 */
	@Test
	public void testBackendConnectionListIsSharedIntoEveryRequestContext() {
		CapturingHttpService service = new CapturingHttpService();
		service.throwOnCall = 2;
		worker.setHttpService(service);

		worker.run();

		assertEquals(2, service.contexts.size());
		assertNotSame(service.contexts.get(0), service.contexts.get(1), "control: a new context per request");
		for (HttpContext context : service.contexts) {
			assertSame(worker.backendConns, context.getAttribute(HttpContextKeys.HTTP_OUT_CONN));
		}
	}

	/**
	 * Backend keep-alive is off: the connections a request opened are closed
	 * gracefully as soon as that request has been handled - before the next
	 * request on the same inbound connection starts - while the inbound
	 * connection itself stays open. Only the per-request close uses GRACEFUL (the
	 * exit cleanup uses IMMEDIATE), so the close mode tells the two apart.
	 */
	@Test
	public void testBackendConnectionsAreClosedAfterEachRequest() {
		TrackingClientHttpConnection first = backendConn();
		CapturingHttpService service = new CapturingHttpService().addOnCall(first);
		service.throwOnCall = 2;
		worker.setHttpService(service);

		worker.run();

		assertEquals(CloseMode.GRACEFUL, first.closeMode, "closed by the per-request close, not the exit cleanup");
		assertEquals(1, first.closeCount);
		assertTrue(service.connsAtStart.get(1).isEmpty(), "the next request starts with no backend connection");
		assertTrue(service.inboundOpenAtStart.get(1), "the inbound (client) connection is kept for the next request");
	}

	/** Every backend connection one request opened is closed, not only the first. */
	@Test
	public void testAllBackendConnectionsOfOneRequestAreClosed() {
		TrackingClientHttpConnection a = backendConn();
		TrackingClientHttpConnection b = backendConn();
		CapturingHttpService service = new CapturingHttpService().addOnCall(a, b);
		service.throwOnCall = 2;
		worker.setHttpService(service);

		worker.run();

		assertEquals(CloseMode.GRACEFUL, a.closeMode);
		assertEquals(CloseMode.GRACEFUL, b.closeMode);
	}

	/**
	 * A request that ends in an exception skips the per-request close; the exit
	 * cleanup must still close its backend connections (immediately: the
	 * connection may be in any state), then the inbound connection.
	 */
	@Test
	public void testBackendConnectionsAreClosedWhenTheRequestThrows() {
		TrackingClientHttpConnection conn = backendConn();
		CapturingHttpService service = new CapturingHttpService().addOnCall(conn);
		worker.setHttpService(service);

		worker.run();

		assertEquals(CloseMode.IMMEDIATE, conn.closeMode);
		assertTrue(worker.backendConns.isEmpty());
		assertFalse(worker.conn.isOpen(), "the client connection is shut down too");
	}

	/** run() catches only Exception; an Error still goes through the exit cleanup. */
	@Test
	public void testBackendConnectionsAreClosedWhenTheRequestThrowsAnError() {
		TrackingClientHttpConnection conn = backendConn();
		CapturingHttpService service = new CapturingHttpService().addOnCall(conn);
		service.toThrow = new LinkageError("simulated");
		worker.setHttpService(service);

		assertThrows(LinkageError.class, () -> worker.run());

		assertEquals(CloseMode.IMMEDIATE, conn.closeMode);
		assertTrue(worker.backendConns.isEmpty());
	}
}
