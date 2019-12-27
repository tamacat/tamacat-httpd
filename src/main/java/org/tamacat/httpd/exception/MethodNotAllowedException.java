/*
 * Copyright (c) 2019 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.exception;

import org.tamacat.httpd.core.BasicHttpStatus;

public class MethodNotAllowedException extends HttpException {

	private static final long serialVersionUID = 1L;

	public MethodNotAllowedException() {
		super(BasicHttpStatus.SC_METHOD_NOT_ALLOWED);
	}
	
	public MethodNotAllowedException(String message) {
		super(BasicHttpStatus.SC_METHOD_NOT_ALLOWED, message);
	}
    
	public MethodNotAllowedException(Throwable cause) {
        super(BasicHttpStatus.SC_METHOD_NOT_ALLOWED, cause);
    }
    
	public MethodNotAllowedException(String message, Throwable cause) {
	    super(BasicHttpStatus.SC_METHOD_NOT_ALLOWED, message, cause);
	}
}
