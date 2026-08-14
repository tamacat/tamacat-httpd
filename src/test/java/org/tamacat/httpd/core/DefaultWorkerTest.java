package org.tamacat.httpd.core;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import javax.net.ssl.SSLHandshakeException;

import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.impl.io.HttpService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.httpd.handler.TamacatHttpServerRequestHandler;
import org.tamacat.httpd.handler.UriHttpRequestHandlerMapper;
import org.tamacat.httpd.mock.DummySocket;
import org.slf4j.MDC;

public class DefaultWorkerTest {

	DefaultWorker worker;

	@Before
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
}
