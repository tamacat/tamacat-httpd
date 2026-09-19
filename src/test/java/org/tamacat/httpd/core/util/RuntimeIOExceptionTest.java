package org.tamacat.httpd.core.util;

import static org.junit.Assert.*;

import org.junit.Test;

public class RuntimeIOExceptionTest {

	@Test
	public void testRuntimeIOException() {
		RuntimeIOException e = new RuntimeIOException();
		assertNull(e.getMessage());
	}

	@Test
	public void testRuntimeIOExceptionString() {
		RuntimeIOException e = new RuntimeIOException("test");
		assertEquals("test", e.getMessage());
	}

	@Test
	public void testRuntimeIOExceptionThrowable() {
		RuntimeIOException e = new RuntimeIOException(new RuntimeException("test"));
		assertEquals("java.lang.RuntimeException: test", e.getMessage());
		assertEquals("test", e.getCause().getMessage());
	}

	@Test
	public void testRuntimeIOExceptionStringThrowable() {
		RuntimeIOException e = new RuntimeIOException("test1", new RuntimeException("test2"));
		assertEquals("test1", e.getMessage());
		assertEquals("test2", e.getCause().getMessage());
	}
}
