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
