/*
 * Copyright (c) 2019 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.exception;

import org.tamacat.httpd.core.BasicHttpStatus;

public class RequestEntityTooLargeException extends HttpException {

	private static final long serialVersionUID = 1L;
	
	public RequestEntityTooLargeException() {
	    super(BasicHttpStatus.SC_REQUEST_ENTITY_TOO_LARGE);
	}
	
	public RequestEntityTooLargeException(String message) {
	    super(BasicHttpStatus.SC_REQUEST_ENTITY_TOO_LARGE, message);
	}

    public RequestEntityTooLargeException(Throwable cause) {
        super(BasicHttpStatus.SC_REQUEST_ENTITY_TOO_LARGE, cause);
    }
    
	public RequestEntityTooLargeException(String message, Throwable cause) {
	    super(BasicHttpStatus.SC_REQUEST_ENTITY_TOO_LARGE, message, cause);
	}
}
