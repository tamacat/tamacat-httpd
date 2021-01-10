package org.tamacat.httpd.exception;

import static org.junit.Assert.*;

import org.junit.Test;

public class TooManyRequestsExceptionTest {

	@Test
	public void testTooManyRequestsException() {
		assertEquals(429, new TooManyRequestsException().getHttpStatus().getStatusCode());
		assertEquals(null, new TooManyRequestsException().getMessage());
	}

	@Test
	public void testTooManyRequestsExceptionString() {
		assertEquals(429, new TooManyRequestsException("Too Many Requests").getHttpStatus().getStatusCode());
		assertEquals("Too Many Requests", new TooManyRequestsException("Too Many Requests").getMessage());
	}

	@Test
	public void testTooManyRequestsExceptionThrowable() {
		assertEquals(429, new TooManyRequestsException(new RuntimeException("TEST")).getHttpStatus().getStatusCode());
		assertEquals("java.lang.RuntimeException: TEST", new TooManyRequestsException(new RuntimeException("TEST")).getMessage());
		assertEquals("TEST", new TooManyRequestsException(new RuntimeException("TEST")).getCause().getMessage());
	}

	@Test
	public void testTooManyRequestsExceptionStringThrowable() {
		assertEquals(429, new TooManyRequestsException("Too Many Requests", new RuntimeException("TEST")).getHttpStatus().getStatusCode());
		assertEquals("Too Many Requests", new TooManyRequestsException("Too Many Requests", new RuntimeException("TEST")).getMessage());
		assertEquals("TEST", new TooManyRequestsException("Too Many Requests", new RuntimeException("TEST")).getCause().getMessage());
	}

}
