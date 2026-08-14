/*
 * Copyright (c) 2008, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core.util;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;

import org.junit.Test;

public class StringUtilsTest {

	@Test
	public void testIsNotEmpty() {
		assertTrue(StringUtils.isNotEmpty("TEST"));
		assertTrue(StringUtils.isNotEmpty(new String("123")));

		assertFalse(StringUtils.isNotEmpty(null));
		assertFalse(StringUtils.isNotEmpty(""));
		assertFalse(StringUtils.isNotEmpty(new String()));
		assertFalse(StringUtils.isNotEmpty(new String("")));
	}

	@Test
	public void testIsEmpty() {
		assertFalse(StringUtils.isEmpty("TEST"));
		assertFalse(StringUtils.isEmpty(new String("123")));

		assertTrue(StringUtils.isEmpty(null));
		assertTrue(StringUtils.isEmpty(""));
		assertTrue(StringUtils.isEmpty(new String()));
		assertTrue(StringUtils.isEmpty(new String("")));
	}

	@Test
	public void testExists() {
		assertTrue(StringUtils.exists("TEST", "T"));

		assertFalse(StringUtils.exists("TEST", "t"));
		assertFalse(StringUtils.exists(null, "T"));
		assertFalse(StringUtils.exists(null, null));
	}

	@Test
	public void testToLowerCase() {
		assertEquals("test", StringUtils.toLowerCase("TEST"));
		assertEquals("test", StringUtils.toLowerCase("test"));

		assertNull(StringUtils.toLowerCase(null));
	}

	@Test
	public void testToUpperCase() {
		assertEquals("TEST", StringUtils.toUpperCase("test"));
		assertEquals("TEST", StringUtils.toUpperCase("TEST"));

		assertNull(StringUtils.toUpperCase(null));
	}

	@Test
	public void testTrim() {
		assertEquals("test", StringUtils.trim(" test"));
		assertEquals("test", StringUtils.trim("test "));
		assertEquals("te st", StringUtils.trim("te st"));

		assertNull(StringUtils.trim(null));
	}

	@Test
	public void testToStringArray() {
		assertTrue(StringUtils.toStringArray(null) instanceof String[]);
		assertTrue(0 == StringUtils.toStringArray(null).length);

		Collection<String> collection = new ArrayList<String>();
		assertTrue(StringUtils.toStringArray(collection) instanceof String[]);
		assertTrue(collection.size() == StringUtils.toStringArray(collection).length);

		collection.add("1");
		collection.add("2");
		collection.add("3");
		assertTrue(StringUtils.toStringArray(collection) instanceof String[]);
		assertTrue(collection.size() == StringUtils.toStringArray(collection).length);
	}

	@Test
	public void testGetStackTrace() {
		RuntimeException e = new RuntimeException("TEST ERROR.");
		assertNotNull(StringUtils.getStackTrace(e));
	}

	@Test
	public void testParse() {
		assertEquals("100", StringUtils.parse("100", "123"));
		assertEquals("123", StringUtils.parse(null, "123"));

		assertTrue(100 == StringUtils.parse("100", 123));
		assertTrue(200L == StringUtils.parse("200", 123L));
		assertTrue(100.00f == StringUtils.parse("100.00", 123.456f));
		assertTrue(200.00d == StringUtils.parse("200.00", 123.456d));

		assertTrue(123 == StringUtils.parse("TEST", 123));
		assertTrue(123L == StringUtils.parse("TEST", 123L));
		assertTrue(123.456f == StringUtils.parse("TEST", 123.456f));
		assertTrue(123.456d == StringUtils.parse("TEST", 123.456d));

		assertTrue(123 == StringUtils.parse("", 123));
		assertTrue(123L == StringUtils.parse("", 123L));
		assertTrue(123.456f == StringUtils.parse("", 123.456f));
		assertTrue(123.456d == StringUtils.parse("", 123.456d));

		assertTrue(123 == StringUtils.parse(null, 123));
		assertTrue(123L == StringUtils.parse(null, 123L));
		assertTrue(123.456f == StringUtils.parse(null, 123.456f));
		assertTrue(1.6777216E7 == StringUtils.parse("1.6777216E7", 0d).doubleValue());
		
		assertNull(StringUtils.parse(null, null));
		assertNull(StringUtils.parse("TEST", null));
		
		//BigDecimal
		assertEquals(new BigDecimal("123.123456789"), StringUtils.parse("123.123456789", new BigDecimal("0")));
		assertEquals(new BigDecimal("0"), StringUtils.parse("ABC", new BigDecimal("0")));
		assertEquals(new BigDecimal("0"), StringUtils.parse(null, new BigDecimal("0")));
		assertEquals(new BigDecimal("0"), StringUtils.parse("", new BigDecimal("0")));
	}

	@Test
	public void testDecode() {
		assertEquals("test", StringUtils.decode("test", ""));
		assertEquals("test", StringUtils.decode("test", "UTF-8"));

		assertEquals("test", StringUtils.decode("test", "abc"));
		assertEquals(null, StringUtils.decode(null, null));
		assertEquals("", StringUtils.decode("", null));
	}

	@Test
	public void testEncode() {
		assertEquals("test", StringUtils.encode("test", ""));
		assertEquals("test", StringUtils.encode("test", "UTF-8"));

		assertEquals("test", StringUtils.encode("test", "abc"));
		assertEquals(null, StringUtils.encode(null, null));
		assertEquals("", StringUtils.encode("", null));
	}

	@Test
	public void testDump() {
		assertEquals("74657374", StringUtils.dump("test".getBytes()));

		assertEquals(null, StringUtils.dump(null));
		assertEquals("", StringUtils.dump("".getBytes()));
	}

	@Test
	public void testConstructor() {
		StringUtils util = new StringUtils() {
		};
		assertNotNull(util);
	}

	@Test
	public void testCut() {
		assertEquals("1234567890", StringUtils.cut("1234567890test", 10));
		assertEquals("1", StringUtils.cut("1234567890test", 1));
		assertEquals("", StringUtils.cut("1234567890test", 0));
		assertEquals("1234567890test", StringUtils.cut("1234567890test", -1));
		assertEquals("1234567890test", StringUtils.cut("1234567890test", 100));
	}

	@Test
	public void testSplit() {
		assertArrayEquals(new String[]{"1","2","3","4","5","6","7","8","9","0"}, StringUtils.split("1,2,3,4,5,6,7,8,9,0", ","));
		assertArrayEquals(new String[]{"1","2","3","4","5"}, StringUtils.split(" 1 , 2, 3 , 4 , 5 ", ","));
		assertArrayEquals(new String[]{"1","2","3","4","5"}, StringUtils.split(" 1 ,   2,    3 , 4    ,   5 ", ","));
		assertArrayEquals(new String[]{"1","2","3","4","5"}, StringUtils.split(" 1\t2\t3\t\t4\t\t5\t", "\t"));
		
		assertArrayEquals(new String[]{"1","2"}, StringUtils.split("1\t2", "\t"));
		assertArrayEquals(new String[]{"1"}, StringUtils.split("1\t", "\t"));
		assertArrayEquals(new String[]{"1"}, StringUtils.split("1", ","));
		assertArrayEquals(new String[]{}, StringUtils.split("", ","));
		assertArrayEquals(new String[]{}, StringUtils.split(" ", ","));
		assertArrayEquals(new String[]{}, StringUtils.split(null, ","));
		
		assertArrayEquals(new String[]{"1","2","3","4","5"}, StringUtils.split("1/2/3/4/5", "/"));
		
		//¥ * + . ? { } ( ) [ ] ^ $ - |
		assertArrayEquals(new String[]{"1","2","3","4","5","6","7","8","9","0"}, StringUtils.split("1\\2\\3\\4\\5\\6\\7\\8\\9\\0", "\\"));
		assertArrayEquals(new String[]{"1","2","3","4","5","6","7","8","9","0"}, StringUtils.split("1*2*3*4*5*6*7*8*9*0", "*"));
		assertArrayEquals(new String[]{"1","2","3","4","5","6","7","8","9","0"}, StringUtils.split("1+2+3+4+5+6+7+8+9+0", "+"));
		assertArrayEquals(new String[]{"1","2","3","4","5","6","7","8","9","0"}, StringUtils.split("1.2.3.4.5.6.7.8.9.0", "."));
		assertArrayEquals(new String[]{"1","2","3","4","5","6","7","8","9","0"}, StringUtils.split("1?2?3?4?5?6?7?8?9?0", "?"));
		assertArrayEquals(new String[]{"1","2","3","4","5","6","7","8","9","0"}, StringUtils.split("1{2{3{4{5{6{7{8{9{0", "{"));
		assertArrayEquals(new String[]{"1","2","3","4","5","6","7","8","9","0"}, StringUtils.split("1}2}3}4}5}6}7}8}9}0", "}"));
		assertArrayEquals(new String[]{"1","2","3","4","5","6","7","8","9","0"}, StringUtils.split("1(2(3(4(5(6(7(8(9(0", "("));
		assertArrayEquals(new String[]{"1","2","3","4","5","6","7","8","9","0"}, StringUtils.split("1)2)3)4)5)6)7)8)9)0", ")"));
		assertArrayEquals(new String[]{"1","2","3","4","5","6","7","8","9","0"}, StringUtils.split("1[2[3[4[5[6[7[8[9[0", "["));
		assertArrayEquals(new String[]{"1","2","3","4","5","6","7","8","9","0"}, StringUtils.split("1^2^3^4^5^6^7^8^9^0", "^"));
		assertArrayEquals(new String[]{"1","2","3","4","5","6","7","8","9","0"}, StringUtils.split("1-2-3-4-5-6-7-8-9-0", "-"));
		assertArrayEquals(new String[]{"1","2","3","4","5","6","7","8","9","0"}, StringUtils.split("1|2|3|4|5|6|7|8|9|0", "|"));
	}
	
	@Test
	public void testGetLocale() {
		assertEquals(Locale.ENGLISH, StringUtils.getLocale("en"));
		assertEquals(Locale.US, StringUtils.getLocale("en_US"));
		assertEquals(Locale.US, StringUtils.getLocale("en-US"));

		assertEquals(Locale.US, StringUtils.getLocale("en_US "));
		assertEquals(Locale.US, StringUtils.getLocale("en_US           "));
		assertEquals(Locale.JAPAN, StringUtils.getLocale(" ja_JP "));
		assertEquals(new Locale("ja","JP","YEN"), StringUtils.getLocale("ja_JP_YEN"));
		assertEquals(new Locale("ja","JP","YEN"), StringUtils.getLocale("ja_JP_YEN_TEST"));

		assertEquals(null, StringUtils.getLocale(null));
		assertEquals(null, StringUtils.getLocale(""));
		assertEquals(null, StringUtils.getLocale(" "));
	}
	
	@Test
	public void testUrlencode() {
		assertEquals(StringUtils.urlencode("!\"#$%&'()=-^|\\[]{}`@*:;+<>,/?~_`."),
			"%21%22%23%24%25%26%27%28%29%3D%2D%5E%7C%5C%5B%5D%7B%7D%60%40%2A%3A%3B%2B%3C%3E%2C%2F%3F~_%60.");
		assertEquals("._~", StringUtils.urlencode("._~"));
		
		assertEquals(StringUtils.urlencode("*"),"%2A");
		assertEquals(StringUtils.urlencode("-"),"%2D");
		assertEquals(StringUtils.urlencode("+"),"%2B");
		assertEquals(StringUtils.urlencode(" "),"%20");
	}
	
	@Test
	public void testUrldecode() {
		assertEquals(StringUtils.urldecode("%21%22%23%24%25%26%27%28%29%3D%2D%5E%7C%5C%5B%5D%7B%7D%60%40%2A%3A%3B%2B%3C%3E%2C%2F%3F~_%60."),
			"!\"#$%&'()=-^|\\[]{}`@*:;+<>,/?~_`.");
		assertEquals(StringUtils.urldecode("._~"),"._~");
		
		assertEquals(StringUtils.urldecode("%2A"),"*");
		assertEquals(StringUtils.urldecode("%2D"),"-");
		assertEquals(StringUtils.urldecode("%20")," ");
	}
}
