/*
 * Copyright 2020 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.exception;

import org.tamacat.httpd.core.BasicHttpStatus;

/**
 * <p>Throws 500 Internal Server Error.
 */
public class InternalServerErrorException extends HttpException {

	private static final long serialVersionUID = 1L;

	public InternalServerErrorException() {
		super(BasicHttpStatus.SC_INTERNAL_SERVER_ERROR);
	}

	public InternalServerErrorException(Throwable cause) {
		super(BasicHttpStatus.SC_INTERNAL_SERVER_ERROR, cause);
	}

	public InternalServerErrorException(String message) {
		super(BasicHttpStatus.SC_INTERNAL_SERVER_ERROR, message);
	}

	public InternalServerErrorException(String message, Throwable cause) {
		super(BasicHttpStatus.SC_SERVICE_UNAVAILABLE, message, cause);
	}
}
