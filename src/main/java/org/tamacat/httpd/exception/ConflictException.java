/*
 * Copyright (c) 2019 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.exception;

import org.tamacat.httpd.core.BasicHttpStatus;

public class ConflictException extends HttpException {

	private static final long serialVersionUID = 1L;

	public ConflictException() {
		super(BasicHttpStatus.SC_CONFLICT);
	}
	
	public ConflictException(String message) {
		super(BasicHttpStatus.SC_CONFLICT, message);
	}
	
	public ConflictException(Throwable cause) {
		super(BasicHttpStatus.SC_CONFLICT, cause);
	}

	public ConflictException(String message, Throwable cause) {
		super(BasicHttpStatus.SC_CONFLICT, message, cause);
	}

}
