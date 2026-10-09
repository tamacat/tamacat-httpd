/*
 * Copyright (c) 2026 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.mock;

import java.io.IOException;

import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.httpd.core.ClientHttpConnection;

/**
 * <p>Test double for {@link ClientHttpConnection} that records how it was closed,
 * without touching a real {@link java.net.Socket}. It reports itself open until
 * {@link #close()} or {@link #shutdown()} is called.
 *
 * @since 1.6
 */
public class TrackingClientHttpConnection extends ClientHttpConnection {

	private boolean open = true;

	/** Set when {@link #close()} (the normal close) is called. */
	public boolean closeCalled;

	/** Set when {@link #shutdown()} (the abortive close) is called. */
	public boolean shutdownCalled;

	/** How many times either method was called. */
	public int closeCount;

	public TrackingClientHttpConnection(ServerConfig serverConfig) {
		super(serverConfig);
	}

	@Override
	public boolean isOpen() {
		return open;
	}

	@Override
	public void close() throws IOException {
		closeCalled = true;
		closeCount++;
		open = false;
	}

	@Override
	public void shutdown() throws IOException {
		shutdownCalled = true;
		closeCount++;
		open = false;
	}
}
