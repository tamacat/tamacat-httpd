/*
 * Copyright (c) 2026 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.tamacat.httpd.mock.ScriptedBackendServer.bodyFor;
import static org.tamacat.httpd.mock.ScriptedBackendServer.chunked;
import static org.tamacat.httpd.mock.ScriptedBackendServer.closeDelimited;
import static org.tamacat.httpd.mock.ScriptedBackendServer.contentLength;
import static org.tamacat.httpd.mock.ScriptedBackendServer.headOnly;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Properties;

import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.MalformedChunkCodingException;
import org.apache.hc.core5.http.TruncatedChunkException;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.httpd.mock.ScriptedBackendServer;
import org.tamacat.httpd.mock.ScriptedBackendServer.Responder;

/**
 * <p>Tests for {@link ClientHttpConnection#isResponseContentPending()}: a backend
 * connection is reusable only once the body of its latest response was read to the
 * end. Each test talks to a real loopback backend, so the bytes left on the wire
 * are real and a follow-up exchange shows whether the connection is still aligned.
 */
@Timeout(30)
public class ClientHttpConnectionTest {

	ScriptedBackendServer backend;
	ClientHttpConnection conn;
	final HttpRequestExecutor executor = new HttpRequestExecutor();

	@AfterEach
	public void tearDown() throws Exception {
		if (conn != null) {
			conn.close();
		}
		if (backend != null) {
			backend.close();
		}
	}

	void connect(Responder responder) throws IOException {
		connect(new ScriptedBackendServer(responder));
	}

	void connect(ScriptedBackendServer server) throws IOException {
		backend = server;
		Properties props = new Properties();
		props.setProperty("BackEndSocketTimeout", "3000");
		conn = new ClientHttpConnection(new ServerConfig(props));
		conn.bind(new Socket(InetAddress.getLoopbackAddress(), backend.getPort()));
	}

	ClassicHttpResponse execute(String method, String path) throws Exception {
		return executor.execute(new BasicClassicHttpRequest(method, path), conn, HttpCoreContext.create());
	}

	/** The follow-up exchange must get its own response: the wire is still aligned. */
	void assertNextExchangeIsAligned(int requestNo) throws Exception {
		try (ClassicHttpResponse next = execute("GET", "/next")) {
			assertEquals(200, next.getCode());
			assertEquals(bodyFor(1, requestNo, "GET /next HTTP/1.1"), EntityUtils.toString(next.getEntity()));
		}
		assertFalse(conn.isResponseContentPending());
	}

	@Test
	public void testNotPendingBeforeAnyResponse() throws Exception {
		connect((c, r, line) -> contentLength(bodyFor(c, r, line)));
		assertFalse(conn.isResponseContentPending());
	}

	@Test
	public void testContentLengthBodyReadToTheEndIsNotPending() throws Exception {
		connect((c, r, line) -> contentLength(bodyFor(c, r, line)));
		ClassicHttpResponse response = execute("GET", "/first");
		assertTrue(conn.isResponseContentPending(), "control: the body has not been read yet");

		assertEquals(bodyFor(1, 1, "GET /first HTTP/1.1"), EntityUtils.toString(response.getEntity()));
		assertFalse(conn.isResponseContentPending());
		assertNextExchangeIsAligned(2);
	}

	/**
	 * The defect being guarded against: a dropped response leaves its body on the
	 * wire, and isStale() alone cannot tell, because unread data counts as "not stale".
	 */
	@Test
	public void testDroppedBodyIsPendingAlthoughNotStale() throws Exception {
		connect((c, r, line) -> contentLength(bodyFor(c, r, line)));
		execute("GET", "/dropped"); //neither read nor closed

		assertTrue(conn.isResponseContentPending());
		assertTrue(conn.isOpen());
		assertFalse(conn.isStale(), "isStale() reports the unread body as available data");
	}

	@Test
	public void testClosedUnreadContentLengthBodyIsDrained() throws Exception {
		connect((c, r, line) -> contentLength(bodyFor(c, r, line)));
		execute("GET", "/first").close(); //HttpService closes the response this way

		assertFalse(conn.isResponseContentPending());
		assertNextExchangeIsAligned(2);
	}

	@Test
	public void testPartiallyReadChunkedBodyIsDrainedOnClose() throws Exception {
		connect((c, r, line) -> r == 1
			? chunked("first-chunk;", "second-chunk;", "third-chunk")
			: contentLength(bodyFor(c, r, line)));
		ClassicHttpResponse response = execute("GET", "/first");
		InputStream content = response.getEntity().getContent();
		assertEquals('f', content.read());
		assertTrue(conn.isResponseContentPending(), "control: only one byte has been read");

		content.close();
		assertFalse(conn.isResponseContentPending());
		assertNextExchangeIsAligned(2);
	}

	/**
	 * A close-delimited body ends only when the backend closes the connection, which
	 * this backend never does: closing the body must neither wait for that nor make
	 * the connection look reusable.
	 */
	@Test
	public void testCloseDelimitedBodyClosedBeforeItsEndStaysPending() throws Exception {
		connect((c, r, line) -> closeDelimited(bodyFor(c, r, line)));
		ClassicHttpResponse response = execute("GET", "/first");
		assertEquals(-1, response.getEntity().getContentLength(), "precondition: no Content-Length");
		assertFalse(response.getEntity().isChunked(), "precondition: not chunked");

		long start = System.nanoTime();
		response.close();
		long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

		assertTrue(conn.isResponseContentPending());
		assertTrue(elapsedMillis < 2000,
			"close() must not wait for the end of a close-delimited body: " + elapsedMillis + " ms");
	}

	@Test
	public void testEmptyBodyIsNeverPending() throws Exception {
		connect((c, r, line) -> contentLength(""));
		ClassicHttpResponse response = execute("GET", "/empty");
		assertNotNull(response.getEntity());
		assertFalse(conn.isResponseContentPending(), "a zero-length body leaves nothing on the wire");
	}

	@Test
	public void testResponsesWithoutBodyAreNeverPending() throws Exception {
		connect((c, r, line) -> line.startsWith("HEAD")
			? headOnly(200, "OK", "Content-Length: 42")
			: headOnly(204, "No Content"));
		ClassicHttpResponse head = execute("HEAD", "/head");
		assertNull(head.getEntity());
		assertFalse(conn.isResponseContentPending());

		ClassicHttpResponse noContent = execute("GET", "/no-content");
		assertEquals(204, noContent.getCode());
		assertFalse(conn.isResponseContentPending());
	}

	/** After a read failure the wire position is unknown, so the connection stays pending. */
	@Test
	public void testFailedReadStaysPending() throws Exception {
		connect((c, r, line) -> concat(headOnly(200, "OK", "Transfer-Encoding: chunked"), "zz\r\nbroken"));
		ClassicHttpResponse response = execute("GET", "/broken");

		assertThrows(MalformedChunkCodingException.class, () -> EntityUtils.toString(response.getEntity()));
		assertTrue(conn.isResponseContentPending());
	}

	/**
	 * core5's ChunkedInputStream reports end of stream on the read after a truncated
	 * chunk, although the body never ended: the failure itself must keep the body
	 * pending, not only the absence of end of stream.
	 */
	@Test
	public void testFailedReadStaysPendingEvenIfTheStreamLaterReportsItsEnd() throws Exception {
		connect(new ScriptedBackendServer((c, r, line) ->
			concat(headOnly(200, "OK", "Transfer-Encoding: chunked"), "a\r\nhello"), true));
		ClassicHttpResponse response = execute("GET", "/truncated");
		InputStream content = response.getEntity().getContent();
		byte[] buffer = new byte[64];

		assertThrows(TruncatedChunkException.class, () -> {
			while (content.read(buffer) != -1) {
				//read up to the truncation.
			}
		});
		assertEquals(-1, content.read(buffer), "precondition: the stream now reports end of stream");
		assertTrue(conn.isResponseContentPending());

		content.close();
		assertTrue(conn.isResponseContentPending());
	}

	/** Tracking follows the latest response: a newer, fully read body clears it. */
	@Test
	public void testLatestResponseDecides() throws Exception {
		connect((c, r, line) -> contentLength(bodyFor(c, r, line)));
		ClassicHttpResponse first = execute("GET", "/first");
		EntityUtils.consume(first.getEntity());
		ClassicHttpResponse second = execute("GET", "/second");
		assertTrue(conn.isResponseContentPending());

		EntityUtils.consume(second.getEntity());
		assertFalse(conn.isResponseContentPending());
	}

	static byte[] concat(byte[] head, String body) {
		byte[] content = body.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
		byte[] result = new byte[head.length + content.length];
		System.arraycopy(head, 0, result, 0, head.length);
		System.arraycopy(content, 0, result, head.length, content.length);
		return result;
	}
}
