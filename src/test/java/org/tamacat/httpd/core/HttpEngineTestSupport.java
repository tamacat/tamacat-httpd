package org.tamacat.httpd.core;

import java.net.ServerSocket;

/**
 * <p>Test-only access to {@link HttpEngine}'s server socket, for tests in other
 * packages that start an engine in a background thread (see
 * {@code HttpdTest}). Not a test itself: the name does not match the Surefire
 * includes.</p>
 */
public final class HttpEngineTestSupport {

	private HttpEngineTestSupport() {}

	/**
	 * @return true once {@link HttpEngine#startHttpd()} has finished
	 * {@code init()} (including the middleware start-up) and opened its
	 * server socket, i.e. the engine is in its accept loop.
	 */
	public static boolean isListening(HttpEngine engine) {
		ServerSocket socket = engine.serverSocket;
		return socket != null && socket.isBound() && !socket.isClosed();
	}
}
