/*
 * Copyright (c) 2009, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core.di.impl;

import org.tamacat.httpd.core.di.DIContainer;
import org.tamacat.httpd.core.di.define.BeanDefine;
import org.tamacat.httpd.core.di.define.BeanDefineMap;
import org.tamacat.httpd.core.util.ClassUtils;

public class DIContainer_test {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		ClassLoader loader = ClassUtils.getDefaultClassLoader();//new UnloadableClassLoader();
		BeanDefineMap defines = new BeanDefineMap();
		
		BeanDefine def1 = new BeanDefine();
        def1.setId("super");
        def1.setType("org.tamacat.httpd.core.di.impl.DIContainer_test$Super", loader);
        def1.setSingleton(false);
        defines.put(def1.getId(), def1);
        
		BeanDefine def2 = new BeanDefine();
        def2.setId("sub");
        def2.setType("org.tamacat.httpd.core.di.impl.DIContainer_test$Sub", loader);
        def2.setSingleton(false);
        defines.put(def2.getId(), def2);
        
		DIContainer di = new DefaultDIContainer(defines, loader);
		Super get1 = di.getBean("sub", Super.class);
		System.out.println(get1);
		
		Super get2 = di.getBean("sub", Sub.class);
		System.out.println(get2);
	}

	public static class Super {}
	
	public static class Sub extends Super {}
}
