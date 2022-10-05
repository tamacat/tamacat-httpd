package org.tamacat.httpd.handler;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RedirectHttpHandlerTest {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testSetStatusCode() {
		RedirectHttpHandler handler = new RedirectHttpHandler();
		assertEquals("Found", handler.httpStatus.getReasonPhrase());

		handler.setStatusCode(302);
		assertEquals("Found", handler.httpStatus.getReasonPhrase());
		
		handler.setStatusCode(301);
		assertEquals("Moved Permanently", handler.httpStatus.getReasonPhrase());
	}

}
