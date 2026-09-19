/*
 * Copyright (c) 2009 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core.di.define;

import java.util.LinkedHashMap;

public class BeanDefineMap extends LinkedHashMap<String, BeanDefine> {
	private static final long serialVersionUID = 1L;
	
	public void add(BeanDefine define) {
		String id = define.getId();
		if (id != null) {
			super.put(id, define);
		}
	}
}
