package org.tamacat.httpd.core.util;

import static org.junit.Assert.*;

import org.junit.Test;

public class ResourceNotFoundExceptionTest {

	@Test
	public void testResourceNotFoundException() {
		ResourceNotFoundException e = new ResourceNotFoundException();
		assertNull(e.getMessage());
	}

	@Test
	public void testResourceNotFoundExceptionString() {
		ResourceNotFoundException e = new ResourceNotFoundException("test");
		assertEquals("test", e.getMessage());
	}

	@Test
	public void testResourceNotFoundExceptionThrowable() {
		ResourceNotFoundException e = new ResourceNotFoundException(new RuntimeException("test"));
		assertEquals("java.lang.RuntimeException: test", e.getMessage());
		assertEquals("test", e.getCause().getMessage());
	}

	@Test
	public void testResourceNotFoundExceptionStringThrowable() {
		ResourceNotFoundException e = new ResourceNotFoundException("test1", new RuntimeException("test2"));
		assertEquals("test1", e.getMessage());
		assertEquals("test2", e.getCause().getMessage());
	}

	@Test
	public void testRuntimeIOException() {

	}

	@Test
	public void testRuntimeIOExceptionString() {

	}

	@Test
	public void testRuntimeIOExceptionThrowable() {

	}

	@Test
	public void testRuntimeIOExceptionStringThrowable() {

	}
}
