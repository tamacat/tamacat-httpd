package org.tamacat.httpd.exception;

import static org.junit.Assert.*;

import org.junit.Test;

public class BadRequestExceptionTest {

	@Test
	public void testBadRequestException() {
		assertEquals(400, new BadRequestException().getHttpStatus().getStatusCode());
		assertEquals(null, new BadRequestException().getMessage());
	}

	@Test
	public void testBadRequestExceptionString() {
		assertEquals(400, new BadRequestException("Bad Request").getHttpStatus().getStatusCode());
		assertEquals("Bad Request", new BadRequestException("Bad Request").getMessage());
	}

	@Test
	public void testBadRequestExceptionThrowable() {
		assertEquals(400, new BadRequestException(new RuntimeException("TEST")).getHttpStatus().getStatusCode());
		assertEquals("java.lang.RuntimeException: TEST", new BadRequestException(new RuntimeException("TEST")).getMessage());
		assertEquals("TEST", new BadRequestException(new RuntimeException("TEST")).getCause().getMessage());
	}

	@Test
	public void testBadRequestExceptionStringThrowable() {
		assertEquals(400, new BadRequestException("Bad Request", new RuntimeException("TEST")).getHttpStatus().getStatusCode());
		assertEquals("Bad Request", new BadRequestException("Bad Request", new RuntimeException("TEST")).getMessage());
		assertEquals("TEST", new BadRequestException("Bad Request", new RuntimeException("TEST")).getCause().getMessage());
	}

}
