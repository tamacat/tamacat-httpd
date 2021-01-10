/*
 * Copyright 2021 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.config;

import static org.junit.Assert.*;

import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
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

	@Test
	public void testSetProxyHttpClientBuilder() {
		HttpProxyConfig config = new HttpProxyConfig();
		config.setProxyHost("localhost");
		config.setProxyPort(9999);
		
		HttpClientBuilder builder = HttpClients.custom();
		config.setProxy(builder);		
	}

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
		
		assertEquals("user", config.getCredentials().getUserPrincipal().getName());
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
