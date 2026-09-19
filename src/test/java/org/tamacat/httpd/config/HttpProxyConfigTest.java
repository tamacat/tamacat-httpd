/*
 * Copyright 2021 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.config;

import static org.junit.Assert.*;

import org.junit.Test;

public class HttpProxyConfigTest {

	@Test
	public void testIsDirect() {
		HttpProxyConfig config = new HttpProxyConfig();
		assertEquals(true, config.isDirect());
		
		config.setProxyHost("localhost");
		config.setProxyPort(9999);
		assertEquals(false, config.isDirect());
	}

	// testSetProxyHttpClientBuilder() removed in 1.6.0: exercised
	// setProxy(HttpClientBuilder), which was removed along with the
	// httpclient dependency [BR-6, Step 6a.1].

	@Test
	public void testTunnel() {
		HttpProxyConfig config = new HttpProxyConfig();
		config.setProxyHost("localhost");
		config.setProxyPort(9999);
		//config.tunnel(new HttpHost("localhost:9999"));
	}

	@Test
	public void testGetProxyHttpHost() {
		HttpProxyConfig config = new HttpProxyConfig();
		config.setProxyHost("localhost");
		config.setProxyPort(9999);
		
		assertEquals("localhost", config.getProxyHttpHost().getHostName());
		assertEquals(9999, config.getProxyHttpHost().getPort());
	}

	@Test
	public void testCreateProxySocket() {
		HttpProxyConfig config = new HttpProxyConfig();
		config.setProxyHost("localhost");
		config.setProxyPort(9999);
		
		//config.createProxySocket());
	}

	@Test
	public void testGetCredentials() {
		HttpProxyConfig config = new HttpProxyConfig();
		config.setProxyHost("localhost");
		config.setProxyPort(9999);
		config.setUsername("user");
		config.setPassword("password");

		// getCredentials() reshaped in 1.6.0: returns HttpProxyConfig.ProxyCredentials
		// (a local username/password holder) instead of httpclient's
		// org.apache.http.auth.Credentials [Step 6a.4/6a.5].
		assertEquals("user", config.getCredentials().getUsername());
		assertEquals("password", config.getCredentials().getPassword());
	}

	@Test
	public void testGetNonProxyHosts() {
		HttpProxyConfig config = new HttpProxyConfig();
		config.setProxyHost("localhost");
		config.setProxyPort(9999);
		config.setNonProxyHosts("localhost");
		
		assertEquals("localhost", config.getNonProxyHosts());
	}

	@Test
	public void testSetProxy() {
		HttpProxyConfig config = new HttpProxyConfig();
		config.setProxyHost("localhost");
		config.setProxyPort(9999);
		config.setUsername("user");
		config.setPassword("password");
		
		config.setProxy();
		
		assertEquals("localhost", System.getProperty("http.proxyHost"));
		assertEquals("9999", System.getProperty("http.proxyPort"));
		
		assertEquals("localhost", System.getProperty("https.proxyHost"));
		assertEquals("9999", System.getProperty("https.proxyPort"));
		
		config.setNonProxyHosts("localhost");
		config.setProxy();
		assertEquals("localhost", System.getProperty("http.nonProxyHosts"));
		assertEquals("localhost", System.getProperty("https.nonProxyHosts"));
	}
}
