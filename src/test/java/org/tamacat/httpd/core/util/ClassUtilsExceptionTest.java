/*
 * Copyright (c) 2007, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class ClassUtilsExceptionTest {

    @Test
    public void testClassUtilsExceptionString() {
        ClassUtilsException e = new ClassUtilsException("Test Message");
        assertEquals("Test Message", e.getMessage());
    }

    @Test
    public void testClassUtilsExceptionThrowable() {
        Exception cause = new Exception("Test Message");
        ClassUtilsException e = new ClassUtilsException(cause);
        assertEquals("Test Message", e.getCause().getMessage());
    }

    @Test
    public void testClassUtilsExceptionStringThrowable() {
        Exception cause = new Exception("Test Message1");
        ClassUtilsException e = new ClassUtilsException("Test Message2", cause);
        assertEquals("Test Message2", e.getMessage());
        assertEquals("Test Message1", e.getCause().getMessage());
    }
}
