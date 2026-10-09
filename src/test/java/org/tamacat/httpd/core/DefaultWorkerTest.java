package org.tamacat.httpd.core;

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.SSLHandshakeException;

import org.tamacat.httpcore4.ConnectionClosedException;
import org.tamacat.httpcore4.HttpServerConnection;
import org.tamacat.httpcore4.impl.DefaultHttpResponseFactory;
import org.tamacat.httpcore4.protocol.HttpContext;
import org.tamacat.httpcore4.protocol.HttpService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.httpd.handler.DefaultHttpService;
import org.tamacat.httpd.mock.DummySocket;
import org.tamacat.httpd.mock.TrackingClientHttpConnection;
import org.tamacat.httpd.core.util.RuntimeIOException;

public class DefaultWorkerTest {

	DefaultWorker worker;

	@Before
	public void setUp() throws Exception {
		HttpService httpService = new DefaultHttpService(
				new HttpProcessorBuilder(),
				new KeepAliveConnReuseStrategy(),
				new DefaultHttpResponseFactory(), null, null);
		worker = new DefaultWorker();
		worker.setServerConfig(new ServerConfig());
		worker.setSocket(new DummySocket());
		worker.setHttpService(httpService);
	}

	@After
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
		worker.handleException(new RuntimeIOException("test"));
	}

	@Test
	public void testIsClosed() throws Exception {
		assertFalse(worker.isClosed());

		//worker.shutdown(
				worker.conn.close();//);
		//assertTrue(worker.isClosed());
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
			super(new HttpProcessorBuilder().build(), new KeepAliveConnReuseStrategy(),
				new DefaultHttpResponseFactory());
		}

		CapturingHttpService addOnCall(TrackingClientHttpConnection... conns) {
			connsToAdd.add(Arrays.asList(conns));
			return this;
		}

		@Override
		public void handleRequest(HttpServerConnection conn, HttpContext context)
				throws IOException, org.tamacat.httpcore4.HttpException {
			int call = contexts.size() + 1;
			contexts.add(context);
			@SuppressWarnings("unchecked")
			List<ClientHttpConnection> conns =
				(List<ClientHttpConnection>) context.getAttribute(DefaultWorker.HTTP_OUT_CONN);
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
		assertNotSame("control: a new context per request", service.contexts.get(0), service.contexts.get(1));
		for (HttpContext context : service.contexts) {
			assertSame(worker.backendConns, context.getAttribute(DefaultWorker.HTTP_OUT_CONN));
		}
	}

	/**
	 * The connections a request opened are closed normally as soon as that request
	 * has been handled - before the next request on the same inbound connection
	 * starts - while the inbound connection itself stays open. Only the
	 * per-request close uses close() (the exit cleanup uses shutdown()), so the
	 * method called tells the two apart.
	 */
	@Test
	public void testBackendConnectionsAreClosedAfterEachRequest() {
		TrackingClientHttpConnection first = backendConn();
		CapturingHttpService service = new CapturingHttpService().addOnCall(first);
		service.throwOnCall = 2;
		worker.setHttpService(service);

		worker.run();

		assertTrue("closed by the per-request close", first.closeCalled);
		assertFalse("not by the exit cleanup", first.shutdownCalled);
		assertEquals(1, first.closeCount);
		assertTrue("the next request starts with no backend connection", service.connsAtStart.get(1).isEmpty());
		assertTrue("the inbound (client) connection is kept for the next request", service.inboundOpenAtStart.get(1));
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

		assertTrue(a.closeCalled);
		assertTrue(b.closeCalled);
	}

	/**
	 * A request that ends in an exception skips the per-request close; the exit
	 * cleanup must still close its backend connections (abortively: the
	 * connection may be in any state), then the inbound connection.
	 */
	@Test
	public void testBackendConnectionsAreClosedWhenTheRequestThrows() {
		TrackingClientHttpConnection conn = backendConn();
		CapturingHttpService service = new CapturingHttpService().addOnCall(conn);
		worker.setHttpService(service);

		worker.run();

		assertTrue(conn.shutdownCalled);
		assertTrue(worker.backendConns.isEmpty());
		assertFalse("the client connection is shut down too", worker.conn.isOpen());
	}

	/** run() catches only Exception; an Error still goes through the exit cleanup. */
	@Test
	public void testBackendConnectionsAreClosedWhenTheRequestThrowsAnError() {
		TrackingClientHttpConnection conn = backendConn();
		CapturingHttpService service = new CapturingHttpService().addOnCall(conn);
		service.toThrow = new LinkageError("simulated");
		worker.setHttpService(service);

		assertThrows(LinkageError.class, () -> worker.run());

		assertTrue(conn.shutdownCalled);
		assertTrue(worker.backendConns.isEmpty());
	}
}
