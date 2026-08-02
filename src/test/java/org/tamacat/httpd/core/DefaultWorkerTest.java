package org.tamacat.httpd.core;

import static org.junit.Assert.*;

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
import org.tamacat.io.RuntimeIOException;

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
		worker.handleException(new RuntimeIOException("test"));
	}

	@Test
	public void testIsClosed() throws Exception {
		assertFalse(worker.isClosed());

		//worker.shutdown(
				worker.conn.close();//);
		//assertTrue(worker.isClosed());
	}
}
