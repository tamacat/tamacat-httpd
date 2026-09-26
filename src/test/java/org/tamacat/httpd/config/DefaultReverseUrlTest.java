/*
 * Copyright (c) 2009, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.config;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;

import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DefaultReverseUrlTest {

	ServiceUrl serviceUrl;
	ServerConfig config;
	DefaultReverseUrl reverseUrl;

	@BeforeEach
	public void setUp() throws Exception {
		config = new ServerConfig();
		serviceUrl = new ServiceUrl(config);
		serviceUrl.setPath("/test/");
		serviceUrl.setType(ServiceType.REVERSE);
		serviceUrl.setHost(new URI("http://localhost/test/").toURL());
		reverseUrl = new DefaultReverseUrl(serviceUrl);
		reverseUrl.setReverse(new URI("http://localhost:8080/test2/").toURL());
	}

	@Test
	public void testGetHost() {
		assertEquals("http://localhost", reverseUrl.getHost().toString());
	}

	@Test
	public void testGetPath() {
		assertEquals("/test/", reverseUrl.getServiceUrl().getPath());
	}

	@Test
	public void testGetReverse() {
		assertEquals(
			"http://localhost:8080/test2/",
			reverseUrl.getReverse().toString()
		);
	}

	@Test
	public void testGetReverseUrl() {
		assertEquals(
			"http://localhost:8080/test2/abc.html",
			reverseUrl.getReverseUrl("/test/abc.html").toString()
		);

		assertNull(reverseUrl.getReverseUrl(null));

		assertNull(reverseUrl.getReverseUrl("te://*@\\({}[]st test"));
	}

	/**
	 * The request path is client-controlled. With a service path that does not end in
	 * "/" and a reverse URL that has no path, the remainder of the request path lands
	 * directly after the authority, so "@evil.example" would turn the configured
	 * "host:port" into user-info and make evil.example the host.
	 */
	@Test
	public void testGetReverseUrlDoesNotLetRequestPathChangeAuthority() throws Exception {
		ServiceUrl su = new ServiceUrl(config);
		su.setPath("/test");
		su.setType(ServiceType.REVERSE);
		su.setHost(new URI("http://localhost/test").toURL());
		DefaultReverseUrl noPathReverse = new DefaultReverseUrl(su);
		noPathReverse.setReverse(new URI("http://localhost:8080").toURL());

		//a legitimate request still resolves to the configured backend.
		assertEquals("http://localhost:8080/abc.html",
			noPathReverse.getReverseUrl("/test/abc.html").toString());

		String[] hijack = {
			"/test@evil.example/x",
			"/test:80@evil.example/x",
			"/test.evil.example/x",
		};
		for (String path : hijack) {
			java.net.URL url = noPathReverse.getReverseUrl(path);
			if (url != null) {
				assertEquals("localhost", url.getHost(), path);
				assertEquals(8080, url.getPort(), path);
				assertNull(url.getUserInfo(), path);
			}
		}
	}

	@Test
	public void testGetTargetAddress() {
		assertEquals("localhost", reverseUrl.getTargetAddress().getHostName());
		assertEquals(8080, reverseUrl.getTargetAddress().getPort());
	}

	@Test
	public void testGetConvertRequestedUrl() throws Exception {
		serviceUrl.setHost(new URI("http://localhost").toURL());
		assertEquals(
			"http://localhost/test/abc.html",
			reverseUrl.getConvertRequestedUrl("http://localhost:8080/test2/abc.html")
		);

		serviceUrl.setHost(new URI("http://localhost:10080").toURL());
		assertEquals(
			"http://localhost:10080/test/abc.html",
			reverseUrl.getConvertRequestedUrl("http://localhost:8080/test2/abc.html")
		);
	}

	@Test
	public void testGetTargetHost() throws Exception {
		HttpHost host = reverseUrl.getTargetHost();
		assertEquals("http", host.getSchemeName());
		assertEquals("localhost", host.getHostName());
		assertEquals(8080, host.getPort());
	}

	@Test
	public void testClone() {
	}
}
