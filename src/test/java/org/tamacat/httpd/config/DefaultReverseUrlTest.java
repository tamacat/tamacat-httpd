/*
 * Copyright (c) 2009, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.config;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.URL;

import org.tamacat.httpcore4.HttpHost;
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
		serviceUrl.setHost(new URL("http://localhost/test/"));
		reverseUrl = new DefaultReverseUrl(serviceUrl);
		reverseUrl.setReverse(new URL("http://localhost:8080/test2/"));
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
		serviceUrl.setHost(new URL("http://localhost"));
		assertEquals(
			"http://localhost/test/abc.html",
			reverseUrl.getConvertRequestedUrl("http://localhost:8080/test2/abc.html")
		);

		serviceUrl.setHost(new URL("http://localhost:10080"));
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

	/**
	 * T-URL-1, accepted side. The argument is the whole request URI, query
	 * included. Everything {@code java.net.URI} accepts is forwarded exactly as
	 * before - in particular unencoded non-ASCII characters and square brackets
	 * in the query, which {@code URI} does not reject.
	 */
	@Test
	public void testGetReverseUrlForwardsRequestUrisAcceptedByUriSyntax() {
		assertEquals("http://localhost:8080/test2/x",
			reverseUrl.getReverseUrl("/test/x").toString());
		assertEquals("http://localhost:8080/test2/x?a=1",
			reverseUrl.getReverseUrl("/test/x?a=1").toString());
		assertEquals("http://localhost:8080/test2/x/é",
			reverseUrl.getReverseUrl("/test/x/é").toString());
		assertEquals("http://localhost:8080/test2/x?q=日本",
			reverseUrl.getReverseUrl("/test/x?q=日本").toString());
		assertEquals("http://localhost:8080/test2/x?a[]=1",
			reverseUrl.getReverseUrl("/test/x?a[]=1").toString());
	}

	/**
	 * T-URL-1, rejected side. A request URI that is not valid {@code java.net.URI}
	 * syntax has no backend URL (the caller answers 404). Each rejected input is
	 * paired with an accepted one that differs only in the offending character, so
	 * an implementation that returned null for everything would fail here too.
	 */
	@Test
	public void testGetReverseUrlRejectsRequestUrisRejectedByUriSyntax() {
		assertNotNull(reverseUrl.getReverseUrl("/test/xy"));
		assertNull(reverseUrl.getReverseUrl("/test/x y"), "a space in the path");
		assertNull(reverseUrl.getReverseUrl("/test/x|y"), "a vertical bar in the path");

		assertNotNull(reverseUrl.getReverseUrl("/test/x?a=12"));
		assertNull(reverseUrl.getReverseUrl("/test/x?a=1|2"), "a vertical bar in the query");

		assertNotNull(reverseUrl.getReverseUrl("/test/x?a=b"));
		assertNull(reverseUrl.getReverseUrl("/test/x?a={b}"), "braces in the query");

		assertNotNull(reverseUrl.getReverseUrl("/test/x%7a"));
		assertNull(reverseUrl.getReverseUrl("/test/x%zz"), "a malformed percent escape");
	}

	/**
	 * T-URL-1, the configured backend must not be replaceable by the request.
	 * With a service path that has no trailing "/" and a reverse URL that has no
	 * path, what is left of the request URI does not start with "/", so it is
	 * parsed together with the authority: "@evil.example" would turn the
	 * configured host:port into user-info and make evil.example the host.
	 */
	@Test
	public void testGetReverseUrlRejectsRequestThatWouldReplaceTheBackendHost() throws Exception {
		ServiceUrl service = new ServiceUrl(config);
		service.setPath("/test");
		service.setType(ServiceType.REVERSE);
		DefaultReverseUrl reverse = new DefaultReverseUrl(service);
		reverse.setReverse(URI.create("http://localhost:8080").toURL());

		//control: the same configuration does forward an ordinary request
		URL url = reverse.getReverseUrl("/test/x");
		assertNotNull(url);
		assertEquals("localhost", url.getHost());
		assertEquals(8080, url.getPort());
		assertEquals("/x", url.getFile());

		assertNull(reverse.getReverseUrl("/test@evil.example/x"));
	}
}
