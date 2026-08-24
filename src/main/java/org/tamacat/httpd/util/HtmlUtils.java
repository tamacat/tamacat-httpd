/*
 * Copyright (c) 2009, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.Header;
import org.tamacat.util.StringUtils;

public class HtmlUtils {

	public static final Pattern LINK_PATTERN = Pattern.compile(
			"<[^<]*\\s+(href|src|action|background|.*[0-9]*;?url)=(?:\'|\")?([^('|\")]*)(?:\'|\")?[^>]*>",
			Pattern.CASE_INSENSITIVE);

	public static final Pattern CHARSET_PATTERN = Pattern.compile(
			"<meta[^<]*\\s+(content)=(.*);\\s?(charset)=(.*)['|\"][^>]*>",
			Pattern.CASE_INSENSITIVE);

	/**
	 * Get the character set from Content-type header.
	 * @param contentType
	 *    ex) key: "Content-Type", value: "text/html; charset=UTF-8"
	 * @return charset (lower case)
	 */
	public static String getCharSet(Header contentType) {
		if (contentType != null) {
			String value = contentType.getValue();
			if (value.indexOf("=") >= 0) {
				String[] values = value.split("=");
				if (values != null && values.length >= 2) {
					String charset = values[1];
					return charset.toLowerCase().trim();
				}
			}
		}
		return null;
	}

	/**
	 * Get the character set from HTML meta tag.
	 * @param html
	 * @param defaultCharset
	 * @return charset (lower case)
	 */
	public static String getCharSetFromMetaTag(String html, String defaultCharset) {
		if (html != null) {
			Matcher matcher = CHARSET_PATTERN.matcher(html);
			if (matcher.find()) {
				String charset = matcher.group(4);
				return charset != null ? charset.toLowerCase().trim()
						: defaultCharset;
			}
		}
		return defaultCharset;
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
	 * @since 1.5.2-tc9.0.120
	 */
	public static String escapeHtml(String value) {
		if (StringUtils.isEmpty(value)) {
			return value;
		}
		return value
			.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;")
			.replace("\"", "&quot;")
			.replace("'", "&#39;");
	}

	public static String escapeHtmlMetaChars(String uri) {
		if (StringUtils.isEmpty(uri)) return uri;
		char[] chars = uri.toCharArray();
		StringBuilder escaped = new StringBuilder();
		for (int i=0; i<chars.length; i++) {
			char c = chars[i];
			if (c == '<') {
				escaped.append("&lt;");
			} else if (c == '>') {
				escaped.append("&gt;");
			} else if (c == '"') {
				escaped.append("&quat;");
			} else if (c== '\'') {
				escaped.append("&#39");
			} else {
				escaped.append(c);
			}
		}
		return escaped.toString();
	}
}
