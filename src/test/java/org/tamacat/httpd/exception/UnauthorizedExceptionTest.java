package org.tamacat.httpd.exception;

import static org.junit.Assert.*;
import static org.tamacat.httpd.core.BasicHttpStatus.*;

import org.junit.Test;

public class UnauthorizedExceptionTest {

	@Test
	public void testUnauthorizedException() {
		UnauthorizedException e = new UnauthorizedException();
		assertEquals(SC_UNAUTHORIZED, e.getHttpStatus());
		assertEquals(null, e.getMessage());
	}

	@Test
	public void testUnauthorizedExceptionString() {
		UnauthorizedException e = new UnauthorizedException("TEST ERROR");
		assertEquals(SC_UNAUTHORIZED, e.getHttpStatus());
		assertEquals("TEST ERROR", e.getMessage());
	}

	@Test
	public void testUnauthorizedExceptionThrowable() {
		assertEquals(401, new UnauthorizedException(new RuntimeException("TEST")).getHttpStatus().getStatusCode());
		assertEquals("java.lang.RuntimeException: TEST", new UnauthorizedException(new RuntimeException("TEST")).getMessage());
		assertEquals("TEST", new UnauthorizedException(new RuntimeException("TEST")).getCause().getMessage());
	}

	@Test
	public void testUnauthorizedExceptionStringThrowable() {
		assertEquals(401, new UnauthorizedException("Unauthorized", new RuntimeException("TEST")).getHttpStatus().getStatusCode());
		assertEquals("Unauthorized", new UnauthorizedException("Unauthorized", new RuntimeException("TEST")).getMessage());
		assertEquals("TEST", new UnauthorizedException("Unauthorized", new RuntimeException("TEST")).getCause().getMessage());
	}
}
