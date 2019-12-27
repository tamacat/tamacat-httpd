/*
 * Copyright (c) 2019 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.exception;

import org.tamacat.httpd.core.BasicHttpStatus;

public class TooManyRequestsException extends HttpException {

	private static final long serialVersionUID = 1L;
	
	public TooManyRequestsException() {
		super(BasicHttpStatus.SC_TOO_MANY_REQUESTS);
	}
	
	public TooManyRequestsException(String message) {
	    super(BasicHttpStatus.SC_TOO_MANY_REQUESTS, message);
	}
	
	public TooManyRequestsException(Throwable cause) {
        super(BasicHttpStatus.SC_TOO_MANY_REQUESTS, cause);
    }

	public TooManyRequestsException(String message, Throwable cause) {
	    super(BasicHttpStatus.SC_TOO_MANY_REQUESTS, message, cause);
	}
}
