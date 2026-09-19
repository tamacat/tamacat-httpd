/*
 * Copyright (c) 2007 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core.util;

public class ClassUtilsException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public ClassUtilsException(String arg0) {
		super(arg0);
	}

	public ClassUtilsException(Throwable arg0) {
		super(arg0);
	}

	public ClassUtilsException(String arg0, Throwable arg1) {
		super(arg0, arg1);
	}

}
