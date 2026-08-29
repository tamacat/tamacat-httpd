/*
 * Copyright (c) 2015 tamacat.org
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SSLSNIContextCreatorTest {

	private static final Logger LOG = LoggerFactory.getLogger(SSLSNIContextCreatorTest.class);
	
	@Test
	public void testGetSSLContext() {
		ServerConfig config = new ServerConfig(new Properties());
		config.setParam("https.keyStoreFile", "https/test.keystore");
		config.setParam("https.keyPassword", "nopassword");
		config.setParam("https.keyStoreType", "JKS");
		config.setParam("https.protocol", "TLSv1_2");
		
		SSLSNIContextCreator ctx = new SSLSNIContextCreator(config);
		assertEquals("TLSv1.2", ctx.getSSLContext().getProtocol());
	}
	
	@Test
	public void testGetSSLContextSNI() throws Exception {
		ServerConfig config = new ServerConfig(new Properties());
		config.setParam("https.keyStoreFile", "https/sni-test-keystore.jks");
		config.setParam("https.keyPassword", "nopassword");
		config.setParam("https.keyStoreType", "JKS");
		config.setParam("https.protocol", "TLSv1.2");
		config.setParam("https.defaultAlias", "test01.example.com");
		
		SSLSNIContextCreator creator = new SSLSNIContextCreator(config);
		SSLContext ctx = creator.getSSLContext();
		assertEquals("TLSv1.2", ctx.getProtocol());
		//LOG.debug(String.join(",", ctx.getServerSocketFactory().getDefaultCipherSuites()));
		LOG.debug(String.join(",", ctx.getServerSocketFactory().getSupportedCipherSuites()));
	}
	
	@Test
	public void testGetSSLContextError() {
		//IllegalArgumentException
		ServerConfig config = new ServerConfig(new Properties());
		String keyStoreFile = "nofile";
		config.setParam("https.keyStoreFile", keyStoreFile);
		config.setParam("https.keyStoreType", "JKS");
		config.setParam("https.defaultAlias", "test01.example.com");

		SSLSNIContextCreator creator = new SSLSNIContextCreator(config);
		try {
			creator.getSSLContext();
			fail();
		} catch (Exception e) {
			//System.out.println(e.getMessage());
			//core-absorption BR-4: RuntimeIOException(cause) -> UncheckedIOException(new IOException(cause)).
			//The original IllegalArgumentException is preserved as the cause chain (getCause().getCause()),
			//but the top-level message now carries an extra "java.io.IOException: " wrapper prefix.
			assertEquals("java.io.IOException: java.lang.IllegalArgumentException: https.keyStoreFile ["+keyStoreFile+"] file not found.", e.getMessage());
			assertEquals(IllegalArgumentException.class, e.getCause().getCause().getClass());
		}
	}
	
	@Test
	public void testGetDefaultAlias() {
		ServerConfig config = new ServerConfig(new Properties());
		config.setParam("https.defaultAlias", "www.example.com");
		SSLSNIContextCreator creator = new SSLSNIContextCreator(config);
		assertEquals("www.example.com", creator.getDefaultAlias());

		creator.setDefaultAlias("test.example.com");
		assertEquals("test.example.com", creator.getDefaultAlias());
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
	 * {@link SSLSNIContextCreator} subclass that wraps the keystore {@link URL}
	 * so the {@link InputStream} {@code getSSLContext()} reads from can be
	 * observed after the call returns. See
	 * {@code SSLContextCreatorTest.ClosingTrackerCreator} for why this does not
	 * use OS-level file locking instead.
	 */
	static class ClosingTrackerSNICreator extends SSLSNIContextCreator {
		TrackingInputStream lastStream;

		ClosingTrackerSNICreator(ServerConfig config) {
			super(config);
		}

		@Override
		protected URL getKeyStoreFile() {
			final URL real = super.getKeyStoreFile();
			try {
				// See SSLContextCreatorTest.ClosingTrackerCreator.getKeyStoreFile()
				// for why a synthetic scheme is needed: URL.of(URI, handler) refuses
				// to override the handler for a JDK-trusted protocol such as "file".
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
	 * FR-6/B-3: the keystore {@link InputStream} that the SNI branch of
	 * {@code getSSLContext()} opens must be closed once the keystore has been
	 * loaded.
	 */
	@Test
	public void testGetSSLContextClosesKeyStoreStream() throws Exception {
		ServerConfig config = new ServerConfig(new Properties());
		config.setParam("https.keyStoreFile", "https/sni-test-keystore.jks");
		config.setParam("https.keyPassword", "nopassword");
		config.setParam("https.keyStoreType", "JKS");
		config.setParam("https.protocol", "TLSv1.2");
		config.setParam("https.defaultAlias", "test01.example.com");

		ClosingTrackerSNICreator creator = new ClosingTrackerSNICreator(config);
		creator.getSSLContext();

		assertNotNull(creator.lastStream, "getKeyStoreFile() must have been consulted");
		assertTrue(creator.lastStream.closed, "the keystore InputStream must be closed");
	}
}
