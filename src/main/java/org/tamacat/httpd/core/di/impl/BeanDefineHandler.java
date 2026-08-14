/*
 * Copyright (c) 2007 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core.di.impl;

import org.tamacat.httpd.core.di.define.BeanDefineMap;

public interface BeanDefineHandler {

	void setClassLoader(ClassLoader loader);

	void setConfigurationFile(String path);

	BeanDefineMap getBeanDefines();
}
