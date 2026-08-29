package org.tamacat.httpd.handler;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RedirectHttpHandlerTest {

	@BeforeEach
	public void setUp() throws Exception {
	}

	@AfterEach
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
