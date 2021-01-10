package org.tamacat.httpd.exception;

import static org.junit.Assert.*;

import org.junit.Test;

public class ConflictExceptionTest {

	@Test
	public void testConflictException() {
		assertEquals(409, new ConflictException().getHttpStatus().getStatusCode());
		assertEquals(null, new ConflictException().getMessage());
	}

	@Test
	public void testConflictExceptionString() {
		assertEquals(409, new ConflictException("Conflict").getHttpStatus().getStatusCode());
		assertEquals("Conflict", new ConflictException("Conflict").getMessage());
	}

	@Test
	public void testConflictExceptionThrowable() {
		assertEquals(409, new ConflictException(new RuntimeException("TEST")).getHttpStatus().getStatusCode());
		assertEquals("java.lang.RuntimeException: TEST", new ConflictException(new RuntimeException("TEST")).getMessage());
		assertEquals("TEST", new ConflictException(new RuntimeException("TEST")).getCause().getMessage());
	}

	@Test
	public void testConflictExceptionStringThrowable() {
		assertEquals(409, new ConflictException("Conflict", new RuntimeException("TEST")).getHttpStatus().getStatusCode());
		assertEquals("Conflict", new ConflictException("Conflict", new RuntimeException("TEST")).getMessage());
		assertEquals("TEST", new ConflictException("Conflict", new RuntimeException("TEST")).getCause().getMessage());
	}

}
