/*
 * Copyright (c) 2007, tamacat.org
 * All rights reserved.
 */
package org.tamacat.core;

public class CoreFactory {

    public static Core createCore() {
        return new DBCore();
    }
}
