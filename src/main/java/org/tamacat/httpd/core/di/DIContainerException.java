/*
 * Copyright (c) 2007 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core.di;

public class DIContainerException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public DIContainerException(String message) {
		super(message);
	}

	public DIContainerException(Throwable cause) {
		super(cause);
	}

	public DIContainerException(String message, Throwable cause) {
		super(message, cause);
	}
}
