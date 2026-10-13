/*
 * Copyright (c) 2007, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core.di.impl;

import java.util.List;
import java.util.Map.Entry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tamacat.httpd.core.di.define.BeanConstructorParam;
import org.tamacat.httpd.core.di.define.BeanDefine;
import org.tamacat.httpd.core.di.define.BeanDefineParam;
import org.tamacat.httpd.core.di.xml.SpringBeanDefineHandler;
import org.slf4j.LoggerFactory;

public class BeanCreatorTest {

	BeanCreator creator = null;

	@BeforeEach
	public void setUp() throws Exception {
		SpringBeanDefineHandler handler = new SpringBeanDefineHandler();
		handler.setLogger(LoggerFactory.getLogger("Test"));
		handler.setConfigurationFile("test.xml");
		creator = new BeanCreator(handler.getBeanDefines());
	}

	@Test
	public void testGetBean() {
		for (Entry<String, BeanDefine> def : creator.getDefines().entrySet()) {
			BeanDefine bean = def.getValue();
			trace("class=" + bean.getType().getName());
			trace("  id=" + bean.getId() + " singleton=" + bean.isSingleton());
			List<BeanConstructorParam> cpList = bean.getConstructorArgs();
			for (BeanConstructorParam p : cpList) {
				trace("    constructor-arg ref=" + p.getRefId());
				trace("    constructor-arg value=" + p.getValue());
				trace("    constructor-arg type=" + p.getType());
			}

			List<BeanDefineParam> pList = bean.getPropertyList();
			for (BeanDefineParam p : pList) {
				if (p.isRef()) {
					trace("    setter ref=" + p.getRefId());
				} else {
					trace("    setter name=" + p.getName() + " value=" + p.getValue());
				}
				//trace("    setter method=" + p.getMethod());
			}
		}
	}

	@Test
	public void testGetInstanceOfType() {
		for (Entry<String, BeanDefine> entry : creator.getDefines().entrySet()) {
			trace(entry.getKey() + "=" + entry.getValue());
		}
	}

	@Test
	public void testRemoveBean() {
		creator.removeBean("");
	}

	@Test
	public void testRemoveBeans() {
		creator.removeBeans(String.class);
	}

	void trace(String m) {
		System.out.println(m.toString());
	}

}
