/*
 * Copyright (c) 2008 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core.di.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
//import org.junit.runner.RunWith;
//import org.powermock.api.mockito.PowerMockito;
//import org.powermock.core.classloader.annotations.PrepareForTest;
//import org.powermock.modules.junit4.PowerMockRunner;

//@RunWith(PowerMockRunner.class)
//@PrepareForTest({System.class, PropertyValueHandler.class})
public class PropertyValueHandlerTest {

	@Test
	public void testReplaceEnvironmentVariable() {
		//PowerMockito.mockStatic(System.class);
		//PowerMockito.when(System.getenv("LOCAL_SERVER")).thenReturn("localhost");
		System.setProperty("LOCAL_SERVER", "localhost");

		assertEquals(
			"localhost", 
			PropertyValueHandler.replaceEnvironmentVariable("${LOCAL_SERVER}")
		);
	}
	
	@Test
	public void testStringConverter() {
		//PowerMockito.mockStatic(System.class);
		//PowerMockito.when(System.getenv("LOCAL_SERVER")).thenReturn("localhost");
		System.setProperty("LOCAL_SERVER", "localhost");
		
		assertEquals(
			"localhost", 
			new PropertyValueHandler.StringConverter().convert("${LOCAL_SERVER}")
		);
	}
	
	@Test
	public void testStringArrayConverter() {
		//PowerMockito.mockStatic(System.class);
		//PowerMockito.when(System.getenv("LOCAL_SERVER")).thenReturn("localhost, example.com");
		System.setProperty("LOCAL_SERVER", "localhost, example.com");

		assertEquals(
			"localhost", 
			new PropertyValueHandler.StringArrayConverter().convert("${LOCAL_SERVER}")[0]
		);
		assertEquals(
			"example.com", 
			new PropertyValueHandler.StringArrayConverter().convert("${LOCAL_SERVER}")[1]
		);
	}
	
	@Test
	public void testIntegerConverter() {
		//PowerMockito.mockStatic(System.class);
		//PowerMockito.when(System.getenv("PORT")).thenReturn("8080");
		System.setProperty("PORT", "8080");
		
		assertEquals(
			8080, 
			new PropertyValueHandler.IntegerConverter().convert("${PORT}").intValue()
		);
	}
	
	@Test
	public void testLongConverter() {
		//PowerMockito.mockStatic(System.class);
		//PowerMockito.when(System.getenv("LIMIT")).thenReturn(String.valueOf(Long.MAX_VALUE));
		System.setProperty("LIMIT", String.valueOf(Long.MAX_VALUE));

		assertEquals(
			Long.MAX_VALUE, 
			new PropertyValueHandler.LongConverter().convert("${LIMIT}").longValue()
		);
	}
	
	@Test
	public void testFloatConverter() {
		//PowerMockito.mockStatic(System.class);
		//PowerMockito.when(System.getenv("LIMIT")).thenReturn(String.valueOf(Float.MAX_VALUE));
		System.setProperty("LIMIT", String.valueOf(Float.MAX_VALUE));
		
		assertEquals(
			String.valueOf(Float.MAX_VALUE), 
			String.valueOf(new PropertyValueHandler.FloatConverter().convert("${LIMIT}").floatValue())
		);
	}
	
	@Test
	public void testDoubleConverter() {
		//PowerMockito.mockStatic(System.class);
		//PowerMockito.when(System.getenv("LIMIT")).thenReturn(String.valueOf(Double.MAX_VALUE));
		System.setProperty("LIMIT", String.valueOf(Double.MAX_VALUE));
		
		assertEquals(
			String.valueOf(Double.MAX_VALUE), 
			String.valueOf(new PropertyValueHandler.DoubleConverter().convert("${LIMIT}").doubleValue())
		);
	}
	
	@Test
	public void testBooleanConverter() {
		//PowerMockito.mockStatic(System.class);
		//PowerMockito.when(System.getenv("TEST")).thenReturn("true");
		System.setProperty("TEST", "true");

		assertTrue(new PropertyValueHandler.BooleanConverter().convert("${TEST}").booleanValue());
		
		//PowerMockito.when(System.getenv("TEST")).thenReturn("false");
		System.setProperty("TEST", "false");
		assertFalse(new PropertyValueHandler.BooleanConverter().convert("${TEST}").booleanValue());
	}
	
	@Test
	public void testClassConverter() {
		//PowerMockito.mockStatic(System.class);
		//PowerMockito.when(System.getenv("CLASS")).thenReturn("java.lang.String");
		System.setProperty("CLASS", "java.lang.String");
		
		assertEquals(
			String.class, 
			new PropertyValueHandler.ClassConverter().convert("${CLASS}")
		);
	}
}
