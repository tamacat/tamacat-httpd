package org.tamacat.httpd.exception;

import static org.junit.Assert.*;

import org.junit.Test;

public class InternalServerErrorExceptionTest {

	@Test
	public void testInternalServerErrorException() {
		assertEquals(500, new InternalServerErrorException().getHttpStatus().getStatusCode());
		assertEquals(null, new InternalServerErrorException().getMessage());
	}

	@Test
	public void testInternalServerErrorExceptionThrowable() {
		assertEquals(500, new InternalServerErrorException("Internal Server Error").getHttpStatus().getStatusCode());
		assertEquals("Internal Server Error", new InternalServerErrorException("Internal Server Error").getMessage());
	}

	@Test
	public void testInternalServerErrorExceptionString() {
		assertEquals(500, new InternalServerErrorException(new RuntimeException("TEST")).getHttpStatus().getStatusCode());
		assertEquals("java.lang.RuntimeException: TEST", new InternalServerErrorException(new RuntimeException("TEST")).getMessage());
		assertEquals("TEST", new InternalServerErrorException(new RuntimeException("TEST")).getCause().getMessage());
	}

	@Test
	public void testInternalServerErrorExceptionStringThrowable() {
		assertEquals(500, new InternalServerErrorException("Internal Server Error", new RuntimeException("TEST")).getHttpStatus().getStatusCode());
		assertEquals("Internal Server Error", new InternalServerErrorException("Internal Server Error", new RuntimeException("TEST")).getMessage());
		assertEquals("TEST", new InternalServerErrorException("Internal Server Error", new RuntimeException("TEST")).getCause().getMessage());
	}

}
