/*
 * Copyright (c) 2007 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core.util;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Locale;
import java.util.regex.Pattern;

public abstract class StringUtils {

	private static final String EMPTY = "";

	public static boolean isNotEmpty(Object value) {
		return value != null && !EMPTY.equals(value);
	}

	public static boolean isEmpty(Object value) {
		return value == null || EMPTY.equals(value);
	}

	public static boolean exists(String value, String str) {
		return value != null && value.indexOf(str) >= 0;
	}

	public static String toLowerCase(String value) {
		return value != null ? value.toLowerCase() : value;
	}

	public static String toUpperCase(String value) {
		return value != null ? value.toUpperCase() : value;
	}

	public static String trim(String value) {
		return value != null ? value.trim() : value;
	}

	public static String[] toStringArray(Collection<String> collection) {
		return collection != null ? collection.toArray(new String[collection.size()]) : new String[0];
	}

	public static String getStackTrace(Throwable e) {
		StackTraceElement[] elements = e.getStackTrace();
		StringBuilder builder = new StringBuilder();
		for (StackTraceElement element : elements) {
			if (builder.length() > 0) {
				builder.append("\t");
			}
			builder.append(element).append("\n");
		}
		return builder.toString();
	}

	/**
	 * <p>
	 * Returns value of type. when data is {@code null}, returns default value.
	 */
	@SuppressWarnings("unchecked")
	public static <T> T parse(String data, T defaultValue) {
		if (data == null)
			return defaultValue;
		try {
			if (ClassUtils.isTypeOf(defaultValue.getClass(), String.class)) {
				return (T) data;
			} else if (ClassUtils.isTypeOf(defaultValue.getClass(), Integer.class)) {
				return (T) Integer.valueOf(data);
			} else if (ClassUtils.isTypeOf(defaultValue.getClass(), Long.class)) {
				return (T) Long.valueOf(data);
			} else if (ClassUtils.isTypeOf(defaultValue.getClass(), Float.class)) {
				return (T) Float.valueOf(data);
			} else if (ClassUtils.isTypeOf(defaultValue.getClass(), Double.class)) {
				return (T) Double.valueOf(data);
			} else if (ClassUtils.isTypeOf(defaultValue.getClass(), BigDecimal.class)) {
				return (T) new BigDecimal(data);
			}
		} catch (Exception e) {
		}
		return defaultValue;
	}

	public static String decode(String str, String encoding) {
		if (str == null || str.length() == 0)
			return str;
		try {
			return new String(str.getBytes("iso-8859-1"), encoding);
		} catch (UnsupportedEncodingException e) {
			return str;
		}
	}

	public static String encode(String str, String encoding) {
		if (str == null || str.length() == 0)
			return str;
		try {
			return new String(str.getBytes(encoding), "iso-8859-1");
		} catch (UnsupportedEncodingException e) {
			return str;
		}
	}

	public static String dump(byte[] str) {
		if (str == null) {
			return null;
		}
		StringBuilder data = new StringBuilder();
		for (byte b : str) {
			String hex = Integer.toHexString((int) b);
			data.append(hex); // System.out.print(hex);
		}
		return data.toString();
	}

	public static String cut(String str, int length) {
		if (str != null && str.length() > length && length >= 0) {
			return str.substring(0, length);
		} else {
			return str;
		}
	}

	/**
	 * split and trim to String array.
	 * 
	 * @param value
	 * @param sep ex) "," ,"\t"
	 * @return String[]
	 * @since 1.2-20150417
	 */
	public static String[] split(String value, String sep) {
		String val = value != null ? value.trim() : "";
		if (val.indexOf(sep) >= 0) {
			return val.split("\\s*" + Pattern.quote(sep) + "\\s*");
		} else {
			if (val.length() > 0) {
				return new String[] { val };
			} else {
				return new String[0];
			}
		}
	}

	public static Locale getLocale(String str) {
		if (str == null || str.trim().length() == 0) {
			return null;
		}
		if (str.indexOf("-") >= 0) {
			str = str.replace("-", "_");
		}
		if (str.indexOf("_") >= 0) {
			String[] locales = str.trim().split("_");
			String language = locales[0];
			String country = "";
			String variant = "";
			if (locales.length >= 2) {
				country = locales[1];
			}
			if (locales.length >= 3) {
				variant = locales[2];
			}
			return Locale.of(language, country, variant);
		} else {
			return Locale.of(str.trim());
		}
	}

	/**
	 * <p>RFC3986 URL Encoding.</p>
	 * java.net.URLEncoder.encode() and replace string.
	 *  <li> "*" -> "%2A"</li>
	 *  <li> "-" -> "%2D"</li>
	 *  <li> "+" -> "%20"</li>
	 *  <li> "%7E" -> "~"</li>  
	 * @param value
	 * @param charset
	 * @return URL Encoded string (RFC3986)
	 * @since 1.4
	 */
	public static String urlencode(String value, Charset charset) {
		try {
			return URLEncoder.encode(value, charset.name())
					.replace("*", "%2A").replace("-", "%2D").replace("+", "%20").replace("%7E", "~");
		} catch (UnsupportedEncodingException e) {
		}
		return "";
	}
	
	/**
	 * <p>RFC3986 URL Encoding (UTF-8)</p>
	 * java.net.URLEncoder.encode() and replace string.
	 *  <li> "+" -> "%20"</li>
	 *  <li> "*" -> "%2A"</li>
	 *  <li> "%7E" -> "~"</li>
	 * @param value
	 * @return URL Encoded string (RFC3986)
	 * @since 1.4
	 */
	public static String urlencode(String value) {
		return urlencode(value, StandardCharsets.UTF_8);
	}
	
	/**
	 * <p>RFC3986 URL Decoding.</p>
	 * java.net.URLDecoder.decode() and replace string.
	 *  <li> "%2A" -> "*"</li>
	 *  <li> "%2D" -> "-"</li>
	 *  <li> "%20" -> "+"</li>
	 * @param value
	 * @param charset
	 * @return URL Decoded string (RFC3986)
	 * @since 1.4
	 */
	public static String urldecode(String value, Charset charset) {
		try {
			return URLDecoder.decode(
				value.replace("%2A","*").replace("%2D","-").replace("%20","+"),
				charset.name());
		} catch (UnsupportedEncodingException e) {
		}
		return "";
	}
	
	/**
	 * <p>RFC3986 URL Decoding (UTF-8)</p>
	 * java.net.URLDecoder.decode() and replace string.
	 *  <li> "%2A" -> "*"</li>
	 *  <li> "%2D" -> "-"</li>
	 *  <li> "%20" -> "+"</li>
	 * @param value
	 * @return URL Decoded string (RFC3986)
	 * @since 1.4
	 */
	public static String urldecode(String value) {
		return urldecode(value, StandardCharsets.UTF_8);
	}
	
	/**
	 * <p>Escapes the 5 characters that are significant in an HTML context
	 * ({@code &}, {@code <}, {@code >}, {@code "}, {@code '}) so that
	 * request-derived values can be safely interpolated into HTML template
	 * output (NFR-SEC-2). Hand-written on purpose - no HTML-escaping library
	 * is a dependency of this project (tech-stack-decisions.md). {@code &} is
	 * escaped first so the escape sequences produced for the other four
	 * characters are not themselves re-escaped.
	 * @param value
	 * @return HTML-escaped string, or {@code value} unchanged if null/empty
	 * @since 2.0-tc11
	 */
	public static String escapeHtml(String value) {
		if (isEmpty(value)) {
			return value;
		}
		return value
			.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;")
			.replace("\"", "&quot;")
			.replace("'", "&#39;");
	}

	/**
	 * Replace a environment variable from setter injection value.
	 * ex. ${USER_HOME} -> /home/testuser
	 * @param value
	 * @since 1.4-20190213
	 */
	public static String envValue(String value) {
		if (StringUtils.isNotEmpty(value) && value.startsWith("${") && value.endsWith("}")) {
			String key = value.substring(2, value.length()-1);
			return System.getenv(key);
		}
		return value;
	}
}
