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
 * <p>Test double for {@link ClientHttpConnection} that reports a fixed,
 * caller-controlled {@code isOpen()}/{@code isStale()} state and records
 * close invocations, without touching a real {@link java.net.Socket}.
 *
 * <p>Used by the deprecation-resource-lea intent's FR-1 (backend connection
 * reuse/close, BR-1/BR-2/BR-2a) tests. The real {@code isStale()} check reads
 * from the bound socket with a short timeout, which is not reliably
 * reproducible with a fake/in-memory socket (a single byte becomes
 * unavailable after the first read); overriding it directly lets the tests
 * exercise both branches of BR-1's reuse condition deterministically.
 *
 * @since 2.0
 */
public class TrackingClientHttpConnection extends ClientHttpConnection {

	private boolean open;
	private boolean stale;

	/** Set when {@link #close()} (the {@code Closeable}/{@code AutoCloseable} overload) is called. */
	public boolean closeCalled;

	/** Set when {@link #close(CloseMode)} (the {@code ModalCloseable} overload) is called. */
	public boolean closeModeCalled;

	public TrackingClientHttpConnection(ServerConfig serverConfig, boolean open, boolean stale) {
		super(serverConfig);
		this.open = open;
		this.stale = stale;
	}

	@Override
	public boolean isOpen() {
		return open;
	}

	@Override
	public boolean isStale() {
		return stale;
	}

	@Override
	public void close() throws IOException {
		closeCalled = true;
		open = false;
	}

	@Override
	public void close(CloseMode closeMode) {
		closeModeCalled = true;
		open = false;
	}
}
