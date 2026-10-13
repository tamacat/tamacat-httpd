/*
 * Copyright (c) 2008 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core.di.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class PropertyValueHandlerTest {

	@Test
	public void testReplaceEnvironmentVariable() {
		System.setProperty("LOCAL_SERVER", "localhost");

		assertEquals(
			"localhost", 
			PropertyValueHandler.replaceEnvironmentVariable("${LOCAL_SERVER}")
		);
	}
	
	@Test
	public void testStringConverter() {
		System.setProperty("LOCAL_SERVER", "localhost");
		
		assertEquals(
			"localhost", 
			new PropertyValueHandler.StringConverter().convert("${LOCAL_SERVER}")
		);
	}
	
	@Test
	public void testStringArrayConverter() {
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
		System.setProperty("PORT", "8080");
		
		assertEquals(
			8080, 
			new PropertyValueHandler.IntegerConverter().convert("${PORT}").intValue()
		);
	}
	
	@Test
	public void testLongConverter() {
		System.setProperty("LIMIT", String.valueOf(Long.MAX_VALUE));

		assertEquals(
			Long.MAX_VALUE, 
			new PropertyValueHandler.LongConverter().convert("${LIMIT}").longValue()
		);
	}
	
	@Test
	public void testFloatConverter() {
		System.setProperty("LIMIT", String.valueOf(Float.MAX_VALUE));
		
		assertEquals(
			String.valueOf(Float.MAX_VALUE), 
			String.valueOf(new PropertyValueHandler.FloatConverter().convert("${LIMIT}").floatValue())
		);
	}
	
	@Test
	public void testDoubleConverter() {
		System.setProperty("LIMIT", String.valueOf(Double.MAX_VALUE));
		
		assertEquals(
			String.valueOf(Double.MAX_VALUE), 
			String.valueOf(new PropertyValueHandler.DoubleConverter().convert("${LIMIT}").doubleValue())
		);
	}
	
	@Test
	public void testBooleanConverter() {
		System.setProperty("TEST", "true");

		assertTrue(new PropertyValueHandler.BooleanConverter().convert("${TEST}").booleanValue());
		
		System.setProperty("TEST", "false");
		assertFalse(new PropertyValueHandler.BooleanConverter().convert("${TEST}").booleanValue());
	}
	
	@Test
	public void testClassConverter() {
		System.setProperty("CLASS", "java.lang.String");
		
		assertEquals(
			String.class, 
			new PropertyValueHandler.ClassConverter().convert("${CLASS}")
		);
	}
}
