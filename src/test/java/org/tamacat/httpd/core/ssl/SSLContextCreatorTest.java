/*
 * Copyright (c) 2009, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core.ssl;

import static org.junit.jupiter.api.Assertions.*;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Properties;

import javax.net.ssl.SSLContext;

import org.junit.jupiter.api.Test;
import org.tamacat.httpd.config.ServerConfig;

public class SSLContextCreatorTest {

	@Test
	public void testSSLContextCreatorServerConfig() throws Exception {
		ServerConfig config = new ServerConfig(new Properties());
		config.setParam("https.keyStoreFile", "https/test.keystore");
		config.setParam("https.keyPassword", "nopassword");
		config.setParam("https.keyStoreType", "JKS");
		config.setParam("https.protocol", "TLS");

		DefaultSSLContextCreator creator = new DefaultSSLContextCreator(config);
		SSLContext ctx = creator.getSSLContext();
		assertNotNull(ctx);
	}

	@Test
	public void testGetSSLContext() throws Exception {
		DefaultSSLContextCreator creator = new DefaultSSLContextCreator();
		creator.setKeyStoreFile("https/test.keystore");
		creator.setKeyPassword("nopassword");
		creator.setKeyStoreType("JKS");
		creator.setSSLProtocol("TLS");

		SSLContext ctx = creator.getSSLContext();
		assertNotNull(ctx);
	}

	/** {@link InputStream} that records whether {@code close()} was called. */
	static class TrackingInputStream extends FilterInputStream {
		boolean closed;
		TrackingInputStream(InputStream in) {
			super(in);
		}
		@Override
		public void close() throws IOException {
			closed = true;
			super.close();
		}
	}

	/**
	 * {@link DefaultSSLContextCreator} subclass that wraps the keystore
	 * {@link URL} so the {@link InputStream} {@code getSSLContext()} reads from
	 * can be observed after the call returns, without relying on OS-level file
	 * locking (which does not reliably surface a leak here: the JDK opens
	 * {@code file:} URL streams in a sharing mode that a second reader is not
	 * blocked by, even while the first stream is still open).
	 */
	static class ClosingTrackerCreator extends DefaultSSLContextCreator {
		TrackingInputStream lastStream;

		@Override
		protected URL getKeyStoreFile() {
			final URL real = super.getKeyStoreFile();
			try {
				// URL.of(URI, URLStreamHandler) - the non-deprecated replacement for
				// the removed new URL(URL,String,URLStreamHandler) constructor -
				// refuses to override the handler for a protocol the JDK already
				// trusts (e.g. "file", to prevent handler-spoofing): it throws
				// IllegalArgumentException("Can't override URLStreamHandler for
				// protocol file"). Wrapping `real` under a synthetic, non-built-in
				// scheme sidesteps that guard while still routing every stream
				// through this test's tracking handler; production code only ever
				// calls openStream()/openConnection() on the result, never
				// inspects the scheme, so this is invisible outside the test double.
				URI trackingUri = new URI("trackedfile:" + real.toExternalForm());
				return URL.of(trackingUri, new URLStreamHandler() {
					@Override
					protected URLConnection openConnection(URL u) throws IOException {
						return new URLConnection(real) {
							@Override
							public void connect() throws IOException {
							}
							@Override
							public InputStream getInputStream() throws IOException {
								lastStream = new TrackingInputStream(real.openStream());
								return lastStream;
							}
						};
					}
				});
			} catch (URISyntaxException | MalformedURLException e) {
				throw new IllegalStateException(e);
			}
		}
	}

	/**
	 * FR-6/B-3: the keystore {@link InputStream} that {@code getSSLContext()}
	 * opens must be closed once the keystore has been loaded.
	 */
	@Test
	public void testGetSSLContextClosesKeyStoreStream() throws Exception {
		ClosingTrackerCreator creator = new ClosingTrackerCreator();
		creator.setKeyStoreFile("https/test.keystore");
		creator.setKeyPassword("nopassword");
		creator.setKeyStoreType("JKS");
		creator.setSSLProtocol("TLS");

		creator.getSSLContext();

		assertNotNull(creator.lastStream, "getKeyStoreFile() must have been consulted");
		assertTrue(creator.lastStream.closed, "the keystore InputStream must be closed");
	}
}
