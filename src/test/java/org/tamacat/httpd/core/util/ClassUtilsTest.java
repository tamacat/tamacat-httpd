/*
 * Copyright (c) 2007 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core.util;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.Test;
import org.tamacat.httpd.core.di.fixture.Core;
import org.tamacat.httpd.core.di.fixture.CoreFactory;
import org.tamacat.httpd.core.di.fixture.SampleCore;

import junit.framework.TestCase;

public class ClassUtilsTest extends TestCase {

	protected void setUp() throws Exception {
		super.setUp();
	}

	@Test
	public void testGetDefaultClassLoader() {
		assertNotNull(ClassUtils.getDefaultClassLoader());
		assertEquals(Thread.currentThread().getContextClassLoader(), ClassUtils.getDefaultClassLoader());
	}

	@Test
	public void testGetURL() {
		assertNotNull(ClassUtils.getURL(""));
	}

	@Test
	public void testGetURLAndClassLoader() {
		assertNotNull(ClassUtils.getURL("", Thread.currentThread().getContextClassLoader()));
	}

	@Test
	public void testNewInstance() {
		assertEquals("", ClassUtils.newInstance(String.class));
		assertEquals(null, ClassUtils.newInstance(ClassUtils.class));
	}

	@Test
	public void testNewInstanceClassAndObjectArgs() {
		assertEquals(null, ClassUtils.newInstance(String.class, new Class[] { String.class }, (Object[]) null));
		assertEquals(null, ClassUtils.newInstance(ClassUtils.class, new Class[] { null }, (Object[]) null));

		assertEquals("test", ClassUtils.newInstance(String.class, new Class[] { String.class }, "test"));
		assertEquals(null, ClassUtils.newInstance(String.class, new Class[] { String.class }, new Object()));
	}

	@Test
	public void testNewInstanceObjectArgs() {
		assertEquals("", ClassUtils.newInstance(String.class, (Object[]) null));
		assertEquals(null, ClassUtils.newInstance(ClassUtils.class, (Object[]) null));

		assertEquals("test", ClassUtils.newInstance(String.class, "test"));
		try {
			assertEquals(null, ClassUtils.newInstance(String.class, new Object()));
			fail();
		} catch (ResourceNotFoundException e) {
			assertTrue(true);
		}
	}

	@Test
	public void testForName() {
		assertEquals(String.class, ClassUtils.forName("java.lang.String"));
		assertEquals(null, ClassUtils.forName(null));
	}

	@Test
	public void testForNameClassLoader() {
		assertEquals(String.class,
				ClassUtils.forName("java.lang.String", Thread.currentThread().getContextClassLoader()));
		assertEquals(null, ClassUtils.forName(null, Thread.currentThread().getContextClassLoader()));
		assertEquals(null, ClassUtils.forName(null, null));
	}

	@Test
	public void testFindMethod() {
		Class<?> type = SampleCore.class;
		Method[] m = ClassUtils.findMethods(type, "setCore");
		assertNotNull(m);
		assertEquals("setCore", m[0].getName());

		assertNull(ClassUtils.findMethods(type, null));
		assertNull(ClassUtils.findMethods(type, ""));
		assertNull(ClassUtils.findMethods(null, null));
		assertNull(ClassUtils.findMethods(type, "abc"));
	}

	@Test
	public void testFindDeclaredMethod() {
		Class<?> type = SampleCore.class;
		Method[] m = ClassUtils.findDeclaredMethods(type, "setCore");
		assertNotNull(m);
		assertEquals("setCore", m[0].getName());

		assertNull(ClassUtils.findDeclaredMethods(type, null));
		assertNull(ClassUtils.findDeclaredMethods(type, ""));
		assertNull(ClassUtils.findDeclaredMethods(null, null));
		assertNull(ClassUtils.findDeclaredMethods(type, "abc"));
	}

	@Test
	public void testGetMethod() {
		Class<?> type = SampleCore.class;
		Method m = ClassUtils.getMethod(type, "setCore", Core.class);
		assertNotNull(m);
		assertEquals("setCore", m.getName());

		Method m2 = ClassUtils.getMethod(type, "", Core.class);
		assertNull(m2);
	}

	@Test
	public void testGetStaticMethod() {
		Class<?> type = CoreFactory.class;
		Method m = ClassUtils.getStaticMethod(type, "createCore", (Class[]) null);
		assertNotNull(m);
		assertEquals("createCore", m.getName());

		Method m2 = ClassUtils.getStaticMethod(type, "", Core.class);
		assertNull(m2);
	}

	@Test
	public void testGetDeclaredMethod() {
		Class<?> type = SampleCore.class;
		Method m = ClassUtils.getDeclaredMethod(type, "setCore", Core.class);
		assertNotNull(m);
		assertEquals("setCore", m.getName());

		Method m2 = ClassUtils.getDeclaredMethod(type, "", Core.class);
		assertNull(m2);
	}

	@Test
	public void testInvoke() {
		Class<?> type = SampleCore.class;
		Method method = ClassUtils.getMethod(type, "setCore", Core.class);

		ClassUtils.invoke(method, new SampleCore(), "");
		ClassUtils.invoke(method, new SampleCore(), (Object) null);
	}

	@Test
	public void testSearchMethodForClassArgs() {
		assertNull(ClassUtils.searchMethod((Class<?>) null, "getCore", (Class<?>) null));

		Class<?> type = SampleCore.class;
		Method[] m = ClassUtils.findMethods(type, "getCore");

		assertNotNull(ClassUtils.searchMethod(m, "getCore", (Class<?>) null));
		assertNotNull(ClassUtils.searchMethod(m, "getCore", new Class<?>[] {}));

		Method[] m2 = ClassUtils.findMethods(type, "setCoreName");
		assertNotNull(ClassUtils.searchMethod(m2, "setCoreName", String.class));
	}

	@Test
	public void testSearchMethodForMethodArrayArgs() {
		Class<?> type = SampleCore.class;
		assertNotNull(ClassUtils.searchMethod(type, "setCoreName", String.class));
		assertNotNull(ClassUtils.searchMethod(type, "getCore", (Class<?>) null));

		assertNull(ClassUtils.searchMethod(type, "xxxxxx", String.class));

		assertNotNull(ClassUtils.searchMethod(C.class, "get", Integer.TYPE));
	}

	static interface A extends List<String> {
	}

	@SuppressWarnings("serial")
	static class B extends ArrayList<String> implements A {
	}

	@SuppressWarnings("serial")
	static class C extends B implements A, List<String> {
	}

	@Test
	public void testGetAdderMethodName() {
		assertEquals("addCoreName", ClassUtils.getAdderMethodName("coreName"));
		assertEquals("addName", ClassUtils.getAdderMethodName("name"));

		assertEquals("add", ClassUtils.getAdderMethodName(""));
	}

	@Test
	public void testGetRemoveMethodName() {
		assertEquals("removeCoreName", ClassUtils.getRemoveMethodName("coreName"));
		assertEquals("removeName", ClassUtils.getRemoveMethodName("name"));

		assertEquals("remove", ClassUtils.getRemoveMethodName(""));
	}

	@Test
	public void testGetSetterMethodName() {
		assertEquals("setCoreName", ClassUtils.getSetterMethodName("coreName"));
		assertEquals("setName", ClassUtils.getSetterMethodName("name"));

		assertEquals("set", ClassUtils.getSetterMethodName(""));
	}

	@Test
	public void testGetGetterMethodName() {
		assertEquals("getCoreName", ClassUtils.getGetterMethodName("coreName"));
		assertEquals("getName", ClassUtils.getGetterMethodName("name"));

		assertEquals("get", ClassUtils.getGetterMethodName(""));
	}

	@Test
	public void testGetSetterMethod() {
		assertEquals("setCoreName", ClassUtils.getSetterMethod("coreName", SampleCore.class).getName());
		assertEquals("setName", ClassUtils.getSetterMethod("name", SampleCore.class).getName());
	}

	@Test
	public void testGetGetterMethod() {
		assertEquals("getCoreName", ClassUtils.getGetterMethod("coreName", SampleCore.class).getName());
		assertEquals("getName", ClassUtils.getGetterMethod("name", SampleCore.class).getName());
	}

	@Test
	public void testIsTypeOf() {
		assertTrue(ClassUtils.isTypeOf(String.class, Object.class));
		assertTrue(ClassUtils.isTypeOf(ArrayList.class, List.class));
		assertTrue(ClassUtils.isTypeOf(ArrayList.class, ArrayList.class));
		assertTrue(ClassUtils.isTypeOf(HashMap.class, Serializable.class));
		assertTrue(ClassUtils.isTypeOf(Integer.class, Number.class));

		assertFalse(ClassUtils.isTypeOf(List.class, ArrayList.class));
		assertFalse(ClassUtils.isTypeOf(HashMap.class, List.class));

		assertFalse(ClassUtils.isTypeOf(null, List.class));
		assertFalse(ClassUtils.isTypeOf(HashMap.class, null));
		assertTrue(ClassUtils.isTypeOf(null, null));
	}

	@Test
	public void testGetGenericType() {
		Type[] types = ClassUtils.getGenericType(StringList.class);
		for (Type type : types) {
			// System.out.println(type);
			assertEquals("java.util.ArrayList<java.lang.String>", type.toString());
		}
		assertEquals(0, ClassUtils.getGenericType(Object.class).length);
	}

	@Test
	public void testGetParameterizedType() {
		ParameterizedType type = ClassUtils.getParameterizedType(StringList.class);
		if (type != null) {
			assertEquals(String.class, type.getActualTypeArguments()[0]);
		}
		assertNull(ClassUtils.getParameterizedType(null));
	}

	@Test
	public void testGetParameterizedTypesClass() {
		Type[] types = ClassUtils.getParameterizedTypes(StringList.class);
		assertNotNull(types);
		for (Type type : types) {
			assertEquals(String.class, type);
		}
	}

	@Test
	public void testGetParameterizedTypesE() {
		Type[] types = ClassUtils.getParameterizedTypes(ArrayList.class);
		assertNotNull(types);
		for (Type type : types) {
			assertEquals("E", type.toString());
		}
	}

	@Test
	public void testGetParameterizedTypesNone() {
		Type[] types = ClassUtils.getParameterizedTypes(Object.class);
		assertNotNull(types);
		assertEquals(0, types.length);
	}

	static class StringList extends ArrayList<String> {
		private static final long serialVersionUID = 1L;
	}

	static class StringMap extends HashMap<String, String> {
		private static final long serialVersionUID = 1L;
	}

	@Test
	public void testSetParameters() {
		Target instance = new Target();
		String methodName = "setName";
		String param = "scott";
		ClassUtils.setParameters(instance, methodName, param);
		assertEquals(param, instance.getName());
	}

	static class Target {
		private String name;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}
}
