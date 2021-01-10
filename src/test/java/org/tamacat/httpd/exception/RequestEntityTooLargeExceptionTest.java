package org.tamacat.httpd.exception;

import static org.junit.Assert.*;

import org.junit.Test;

public class RequestEntityTooLargeExceptionTest {

	@Test
	public void testRequestEntityTooLargeException() {
		assertEquals(413, new RequestEntityTooLargeException().getHttpStatus().getStatusCode());
		assertEquals(null, new RequestEntityTooLargeException().getMessage());
	}

	@Test
	public void testRequestEntityTooLargeExceptionString() {
		assertEquals(413, new RequestEntityTooLargeException("Request Entity Too Large").getHttpStatus().getStatusCode());
		assertEquals("Request Entity Too Large", new RequestEntityTooLargeException("Request Entity Too Large").getMessage());
	}

	@Test
	public void testRequestEntityTooLargeExceptionThrowable() {
		assertEquals(413, new RequestEntityTooLargeException(new RuntimeException("TEST")).getHttpStatus().getStatusCode());
		assertEquals("java.lang.RuntimeException: TEST", new RequestEntityTooLargeException(new RuntimeException("TEST")).getMessage());
		assertEquals("TEST", new RequestEntityTooLargeException(new RuntimeException("TEST")).getCause().getMessage());
	}

	@Test
	public void testRequestEntityTooLargeExceptionStringThrowable() {
		assertEquals(413, new RequestEntityTooLargeException("Request Entity Too Large", new RuntimeException("TEST")).getHttpStatus().getStatusCode());
		assertEquals("Request Entity Too Large", new RequestEntityTooLargeException("Request Entity Too Large", new RuntimeException("TEST")).getMessage());
		assertEquals("TEST", new RequestEntityTooLargeException("Request Entity Too Large", new RuntimeException("TEST")).getCause().getMessage());
	}

}
