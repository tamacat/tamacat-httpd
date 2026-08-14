/*
 * Copyright (c) 2007, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core.di;

import static org.junit.Assert.*;

import org.junit.Test;

public class DIContainerExceptionTest {

    @Test
    public void testDIContainerExceptionString() {
        DIContainerException e = new DIContainerException("Test Message");
        assertEquals("Test Message", e.getMessage());
    }

    @Test
    public void testDIContainerExceptionThrowable() {
        Exception cause = new Exception("Test Message");
        DIContainerException e = new DIContainerException(cause);
        assertEquals("Test Message", e.getCause().getMessage());
    }

    @Test
    public void testDIContainerExceptionStringThrowable() {
        Exception cause = new Exception("Test Message1");
        DIContainerException e = new DIContainerException("Test Message2", cause);
        assertEquals("Test Message2", e.getMessage());
        assertEquals("Test Message1", e.getCause().getMessage());
    }
}
