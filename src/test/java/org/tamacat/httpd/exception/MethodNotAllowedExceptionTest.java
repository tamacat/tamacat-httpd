package org.tamacat.httpd.exception;

import static org.junit.Assert.*;

import org.junit.Test;

public class MethodNotAllowedExceptionTest {

	@Test
	public void testMethodNotAllowedException() {
		assertEquals(405, new MethodNotAllowedException().getHttpStatus().getStatusCode());
		assertEquals(null, new MethodNotAllowedException().getMessage());
	}

	@Test
	public void testMethodNotAllowedExceptionString() {
		assertEquals(405, new MethodNotAllowedException("Method Not Allowed").getHttpStatus().getStatusCode());
		assertEquals("Method Not Allowed", new MethodNotAllowedException("Method Not Allowed").getMessage());
	}

	@Test
	public void testMethodNotAllowedExceptionThrowable() {
		assertEquals(405, new MethodNotAllowedException(new RuntimeException("TEST")).getHttpStatus().getStatusCode());
		assertEquals("java.lang.RuntimeException: TEST", new MethodNotAllowedException(new RuntimeException("TEST")).getMessage());
		assertEquals("TEST", new MethodNotAllowedException(new RuntimeException("TEST")).getCause().getMessage());
	}

	@Test
	public void testMethodNotAllowedExceptionStringThrowable() {
		assertEquals(405, new MethodNotAllowedException("Method Not Allowed", new RuntimeException("TEST")).getHttpStatus().getStatusCode());
		assertEquals("Method Not Allowed", new MethodNotAllowedException("Method Not Allowed", new RuntimeException("TEST")).getMessage());
		assertEquals("TEST", new MethodNotAllowedException("Method Not Allowed", new RuntimeException("TEST")).getCause().getMessage());
	}

}
