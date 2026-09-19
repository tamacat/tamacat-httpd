/*
 * Copyright (c) 2009, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core.util;

import java.io.Closeable;
import java.io.IOException;
import java.net.Socket;

import org.junit.Test;

public class IOUtilsTest {

	@Test
	public void testGetInputStream() {
		IOUtils.getInputStream("");
	}

	@Test
	public void testgetClassPathToResourcePath() {
		IOUtils.getClassPathToResourcePath("");
	}

	@Test
	public void testCloseObjectNull() {
		ExampleCloseable target = null;
		IOUtils.close(target);
	}

	@Test
	public void testCloseObjectCloseMethod() {
		ExampleNotCloseable target = new ExampleNotCloseable();
		IOUtils.close(target);
	}

	@Test
	public void testCloseObject() {
		String target = "Test";
		IOUtils.close(target);
	}

	@Test
	public void testCloseSocket() {
		Socket socket = new Socket();
		IOUtils.close(socket);
	}

	@Test
	public void testCloseCloseable() {
		ExampleCloseable target = new ExampleCloseable();
		IOUtils.close(target);

		ExampleIOExceptionCloseable target2 = new ExampleIOExceptionCloseable();
		IOUtils.close(target2);
	}

	static class ExampleCloseable implements Closeable {
		public void close() throws IOException {
			System.out.println("ExampleCloseable#close()");
		}
	}

	static class ExampleIOExceptionCloseable implements Closeable {
		public void close() throws IOException {
			System.out.println("ExampleCloseable#close()");
			throw new IOException("IOException test");
		}
	}

	static class ExampleNotCloseable {
		public void close() throws IOException {
			System.out.println("ExampleNotCloseable#close()");
		}
	}
}
