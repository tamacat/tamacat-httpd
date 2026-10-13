/*
 * Copyright (c) 2009, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core.ssl;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URL;
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

	/**
	 * T-URL-5. A CRL location starting with http:// or https:// is turned into a
	 * URL without being opened. A value that is valid {@code java.net.URI} syntax
	 * gives the same URL as before; one that is not (a space) is reported with the
	 * message a CRL that cannot be found has always had.
	 */
	@Test
	public void testGetCRLFileFromHttpUrl() {
		DefaultSSLContextCreator creator = new DefaultSSLContextCreator();

		creator.setCrlFile("http://localhost/crl.pem");
		URL url = creator.getCRLFile();
		assertEquals("http", url.getProtocol());
		assertEquals("localhost", url.getHost());
		assertEquals("/crl.pem", url.getFile());
		assertEquals("http://localhost/crl.pem", url.toString());

		creator.setCrlFile("https://localhost:8443/ca/crl.der");
		assertEquals("https://localhost:8443/ca/crl.der", creator.getCRLFile().toString());

		creator.setCrlFile("http://localhost/crl file.pem");
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, creator::getCRLFile);
		assertEquals("https.CRL [http://localhost/crl file.pem] file not found.", e.getMessage());
	}
}
