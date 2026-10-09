/*
 * Copyright (c) 2026 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.mock;

import java.io.IOException;

import org.apache.hc.core5.io.CloseMode;
import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.httpd.core.ClientHttpConnection;

/**
 * <p>Test double for {@link ClientHttpConnection} that records how it was closed,
 * without touching a real {@link java.net.Socket}. It reports itself open until
 * one of the close methods is called.
 *
 * @since 2.0
 */
public class TrackingClientHttpConnection extends ClientHttpConnection {

	private boolean open = true;

	/** Set when {@link #close()} (the {@code Closeable}/{@code AutoCloseable} overload) is called. */
	public boolean closeCalled;

	/** The mode passed to {@link #close(CloseMode)}, or {@code null} if it was not called. */
	public CloseMode closeMode;

	/** How many times either close method was called. */
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
	public void close(CloseMode closeMode) {
		this.closeMode = closeMode;
		closeCount++;
		open = false;
	}
}
