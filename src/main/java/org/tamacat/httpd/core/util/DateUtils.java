/*
 * Copyright (c) 2007 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Utilities of Date.
 */
public abstract class DateUtils {

	static final Locale currentLocale = Locale.getDefault();

	public static String getTime(Date date, String pattern) {
		return getTime(date, pattern, currentLocale);
	}

	public static String getTime(Date date, String pattern, Locale locale) {
		SimpleDateFormat formatter = new SimpleDateFormat(pattern, locale);
		return formatter.format(date);
	}
	
	public static String getTime(Date date, String pattern, Locale locale, TimeZone zone) {
		SimpleDateFormat formatter = new SimpleDateFormat(pattern, locale);
		formatter.setTimeZone(zone);
		return formatter.format(date);
	}

	public static String getTimestamp(String pattern) {
		return getTime(new Date(), pattern, currentLocale);
	}

	public static String getTimestamp(String pattern, Locale locale) {
		return getTime(new Date(), pattern, locale);
	}

	public static Date parse(String date, String pattern) {
		return parse(date, pattern, currentLocale);
	}

	public static Date parse(String date, String pattern, Locale locale) {
		SimpleDateFormat formatter = new SimpleDateFormat(pattern, locale);
		try {
			return formatter.parse(date);
		} catch (ParseException e) {
			return null;
		}
	}
	
	public static Date parse(String date, String pattern, Locale locale, TimeZone zone) {
		SimpleDateFormat formatter = new SimpleDateFormat(pattern, locale);
		formatter.setTimeZone(zone);
		try {
			return formatter.parse(date);
		} catch (ParseException e) {
			return null;
		}
	}
}
