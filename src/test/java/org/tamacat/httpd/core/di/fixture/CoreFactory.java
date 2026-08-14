/*
 * Copyright (c) 2007, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core.di.fixture;

public class CoreFactory {

    public static Core createCore() {
        return new DBCore();
    }
}
