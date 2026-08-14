/*
 * Copyright (c) 2011 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core.util;

import java.lang.reflect.Method;

public class CloneUtils {

	public static <T> T clone(T obj) throws CloneNotSupportedException {
		if (obj == null) {
			return null;
		}
		if (obj instanceof Cloneable) {
			Class<?> type = obj.getClass();
			Method method = ClassUtils.searchMethod(type, "clone", (Class[]) null);
			if (method == null) {
				throw new NoSuchMethodError("No such clone method");
			}
			@SuppressWarnings("unchecked")
			T clone = (T) ClassUtils.invoke(method, obj, (Object[]) null);
			if (clone != null) {
				return clone;
			}
		}
		throw new CloneNotSupportedException();
	}
}
