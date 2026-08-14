/*
 * Copyright (c) 2009 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core.util;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.lang.reflect.Method;
import java.net.Socket;
import java.net.URL;

/**
 * Class of Utilities for I/O
 */
public class IOUtils {

	/**
	 * Get the InputStream from Resource in CLASSPATH.
	 * 
	 * @param path
	 * @return
	 */
	public static InputStream getInputStream(String path) {
		return getInputStream(path, ClassUtils.getDefaultClassLoader());
	}

	/**
	 * Get the InputStream from Resource in CLASSPATH.
	 * 
	 * @param path
	 *            File path in CLASSPATH
	 * @return InputStream
	 * @since 0.7
	 */
	public static InputStream getInputStream(String path, ClassLoader loader) {
		URL url = ClassUtils.getURL(getClassPathToResourcePath(path), loader);
		InputStream in = null;
		try {
			in = url.openStream();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} catch (NullPointerException e) {
			throw new ResourceNotFoundException(path + " is not found.");
		}
		return in;
	}

	/**
	 * Convert the format of CLASSPATH('.' seperator) to Resource path('/'
	 * separator)
	 * 
	 * @param path
	 * @return
	 */
	public static String getClassPathToResourcePath(String path) {
		if (path == null || path.indexOf('/') >= 0)
			return path;
		int idx = path.lastIndexOf(".");
		if (idx >= 0) {
			String name = path.substring(0, idx);
			String ext = path.substring(idx, path.length());
			return name.replace('.', '/') + ext;
		} else {
			return path;
		}
	}

	/**
	 * It performs, when the "close()" method is implemented.
	 * 
	 * @param target
	 */
	static public void close(Object target) {
		if (target != null) {
			if (target instanceof Closeable) {
				close((Closeable) target);
			} else {
				try {
					Method closable = ClassUtils.searchMethod(target.getClass(), "close");
					if (closable != null)
						closable.invoke(target);
				} catch (Exception e) {
					Throwable cause = e.getCause();
					if (cause != null && cause instanceof IOException) {
						throw new UncheckedIOException(new IOException(e));
					}
				}
			}
		}
	}

	/**
	 * When an Exception occurs, UncheckedIOException will be given up if it is
	 * OutputStream or Writer.
	 * 
	 * @param AutoCloseable
	 */
	static public void close(AutoCloseable closeable) {
		try {
			if (closeable != null) {
				closeable.close();
			}
		} catch (Exception e) {
			if (closeable instanceof OutputStream || closeable instanceof Writer) {
				throw new UncheckedIOException(new IOException(e));
			}
		}
	}

	/**
	 * It ignores, even if an exception occurs.
	 * 
	 * @param socket
	 */
	static public void close(Socket socket) {
		try {
			if (socket != null) {
				socket.close();
			}
		} catch (IOException e) {
			// ignore
		}
	}
}
