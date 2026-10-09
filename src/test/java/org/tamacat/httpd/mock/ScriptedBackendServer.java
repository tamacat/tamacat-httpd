/*
 * Copyright (c) 2026 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.mock;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <p>A loopback HTTP/1.1 backend for reverse-proxy tests. Every request is answered
 * with raw bytes chosen by the test, and every connection is kept open (keep-alive)
 * until the client closes it or this server is closed.
 *
 * <p>Raw bytes rather than a real HTTP server let a test choose exactly how a
 * response body is delimited (Content-Length, chunked or close-delimited), which is
 * what decides whether an unread body is left on the wire for the next exchange.
 *
 * <p>Only body-less requests (GET/HEAD) are supported: the request head is read up
 * to the empty line, and a request body would not be consumed.
 *
 * @since 1.6
 */
public class ScriptedBackendServer implements Closeable {

	/** Builds the raw response bytes for one request. */
	@FunctionalInterface
	public interface Responder {
		byte[] respond(int connectionNo, int requestNo, String requestLine);
	}

	private final ServerSocket serverSocket;
	private final Responder responder;
	private final boolean closeAfterResponse;
	private final AtomicInteger connections = new AtomicInteger();
	private final AtomicInteger closedByClient = new AtomicInteger();
	private final List<String> connectionHeaders = new CopyOnWriteArrayList<>();
	private final List<Socket> accepted = new CopyOnWriteArrayList<>();

	public ScriptedBackendServer(Responder responder) throws IOException {
		this(responder, false);
	}

	/**
	 * @param closeAfterResponse true: close each connection right after its first
	 *   response, e.g. to cut a body short.
	 */
	public ScriptedBackendServer(Responder responder, boolean closeAfterResponse) throws IOException {
		this.responder = responder;
		this.closeAfterResponse = closeAfterResponse;
		this.serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
		Thread acceptor = new Thread(this::acceptLoop, "scripted-backend-acceptor");
		acceptor.setDaemon(true);
		acceptor.start();
	}

	public int getPort() {
		return serverSocket.getLocalPort();
	}

	/** The number of TCP connections accepted so far. */
	public int getConnectionCount() {
		return connections.get();
	}

	/**
	 * The value of the {@code Connection} header of every request received so far,
	 * in arrival order; {@code null} for a request without one.
	 */
	public List<String> getConnectionHeaders() {
		return connectionHeaders;
	}

	/**
	 * Wait until the client has closed at least {@code count} connections (this
	 * server read end of stream on them), or the timeout expires.
	 * @return the number of connections the client has closed.
	 */
	public int awaitClosedByClient(int count, long timeoutMillis) throws InterruptedException {
		long deadline = System.currentTimeMillis() + timeoutMillis;
		while (closedByClient.get() < count && System.currentTimeMillis() < deadline) {
			Thread.sleep(10);
		}
		return closedByClient.get();
	}

	void acceptLoop() {
		try {
			while (true) {
				Socket socket = serverSocket.accept();
				accepted.add(socket);
				int connectionNo = connections.incrementAndGet();
				Thread worker = new Thread(() -> serve(socket, connectionNo),
					"scripted-backend-" + connectionNo);
				worker.setDaemon(true);
				worker.start();
			}
		} catch (IOException e) {
			//the server socket was closed.
		}
	}

	void serve(Socket socket, int connectionNo) {
		try (Socket s = socket) {
			BufferedReader in = new BufferedReader(
				new InputStreamReader(s.getInputStream(), StandardCharsets.ISO_8859_1));
			OutputStream out = s.getOutputStream();
			int requestNo = 0;
			String line;
			while ((line = in.readLine()) != null) {
				if (line.isEmpty()) continue;
				String requestLine = line;
				String connection = null;
				while ((line = in.readLine()) != null && !line.isEmpty()) {
					if (line.regionMatches(true, 0, "Connection:", 0, 11)) {
						connection = line.substring(11).trim();
					}
				}
				connectionHeaders.add(connection);
				requestNo++;
				out.write(responder.respond(connectionNo, requestNo, requestLine));
				out.flush();
				if (closeAfterResponse) {
					return;
				}
			}
			closedByClient.incrementAndGet(); //orderly close (FIN)
		} catch (IOException e) {
			//the client went away abruptly (RST), or close() shut the socket.
			closedByClient.incrementAndGet();
		}
	}

	@Override
	public void close() throws IOException {
		serverSocket.close();
		for (Socket socket : accepted) {
			try {
				socket.close();
			} catch (IOException ignore) {
			}
		}
	}

	/**
	 * A body that names the exchange it answers, so a test can tell which request a
	 * response really belongs to.
	 */
	public static String bodyFor(int connectionNo, int requestNo, String requestLine) {
		return "conn#" + connectionNo + " req#" + requestNo + " [" + requestLine + "]";
	}

	/** A 200 response delimited by Content-Length, with optional extra header lines. */
	public static byte[] contentLength(String body, String... headers) {
		byte[] content = body.getBytes(StandardCharsets.ISO_8859_1);
		return concat(head(200, "OK", "Content-Length: " + content.length, headers), content);
	}

	/** A 200 response with chunked transfer coding, one chunk per argument. */
	public static byte[] chunked(String... chunks) {
		StringBuilder body = new StringBuilder();
		for (String chunk : chunks) {
			body.append(Integer.toHexString(chunk.length())).append("\r\n").append(chunk).append("\r\n");
		}
		body.append("0\r\n\r\n");
		return concat(head(200, "OK", "Transfer-Encoding: chunked"),
			body.toString().getBytes(StandardCharsets.ISO_8859_1));
	}

	/**
	 * A 200 response with neither Content-Length nor Transfer-Encoding: its body ends
	 * only when the connection closes, which this server does not do on its own.
	 */
	public static byte[] closeDelimited(String body) {
		return concat(head(200, "OK", null), body.getBytes(StandardCharsets.ISO_8859_1));
	}

	/** A response head only, for status codes and methods that carry no body. */
	public static byte[] headOnly(int status, String reason, String... headers) {
		return head(status, reason, null, headers);
	}

	static byte[] head(int status, String reason, String framing, String... headers) {
		StringBuilder head = new StringBuilder();
		head.append("HTTP/1.1 ").append(status).append(' ').append(reason).append("\r\n");
		head.append("Content-Type: text/plain\r\n");
		if (framing != null) {
			head.append(framing).append("\r\n");
		}
		for (String header : headers) {
			head.append(header).append("\r\n");
		}
		head.append("\r\n");
		return head.toString().getBytes(StandardCharsets.ISO_8859_1);
	}

	static byte[] concat(byte[] a, byte[] b) {
		byte[] result = new byte[a.length + b.length];
		System.arraycopy(a, 0, result, 0, a.length);
		System.arraycopy(b, 0, result, a.length, b.length);
		return result;
	}
}
