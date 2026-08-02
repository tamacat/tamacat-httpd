/*
 * Copyright (c) 2009 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.util;

import java.net.HttpCookie;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpMessage;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.tamacat.log.Log;
import org.tamacat.log.LogFactory;
import org.tamacat.util.StringUtils;

/**
 * <p>The utility class for HTTP request and response Headers.
 */
public final class HeaderUtils {

	static final Log LOG = LogFactory.getLog(HeaderUtils.class);

	static final String CONTENT_TYPE_FORM_URLENCODED = "application/x-www-form-urlencoded";

	/** Cannot instantiate. */
	HeaderUtils() {}

	/**
	 * <p>Get the first header value.
	 * @see {@link org.apache.hc.core5.http.MessageHeaders#getFirstHeader}
	 * @param message
	 * @param name
	 * @return first header value.
	 */
	public static String getHeader(
			HttpMessage message, String name) {
		Header header = message.getFirstHeader(name);
		return header != null ? header.getValue() : null;
	}

	/**
	 * <p>Get the first header value.
	 * When header is null, returns default value.
	 * @see {@link org.apache.hc.core5.http.MessageHeaders#getFirstHeader}
	 * @param message
	 * @param name
	 * @param defaultValue
	 * @return first header value.
	 */
	public static String getHeader(
			HttpMessage message, String name, String defaultValue) {
		Header header = message.getFirstHeader(name);
		return header != null ? header.getValue() : defaultValue;
	}

	/**
	 * <p>when each other's header names are equal returns true.
	 * The header name does not distinguish a capital letter, a small letter.
	 * @param target target header.
	 * @param other other one.
	 * @return true, header names are equals.
	 */
	public static boolean equalsName(Header target, Header other) {
		if (target == null || other == null) {
			return false;
		} else {
			return target.getName().equalsIgnoreCase(other.getName());
		}
	}

	/**
	 * <p>Get the Cookie value from Cookie header line.
	 *
	 * <p><strong>Malformed cookies are skipped.</strong> {@link java.net.HttpCookie}
	 * rejects a name that is not an RFC 2616 token and a name starting with {@code $}
	 * (for example the RFC 2965 {@code $Version} attribute), which the httpclient
	 * {@code BasicClientCookie} removed in 2.0 used to accept. Such a cookie is dropped
	 * and logged at DEBUG; the remaining cookies of the header line are still returned,
	 * and no {@code IllegalArgumentException} reaches the caller. This method parses a
	 * client-supplied {@code Cookie} request header, so one bad entry must not fail the
	 * whole header.
	 *
	 * @param cookie header line.
	 * @return the well-formed cookies contained in the header line. Never {@code null}.
	 */
	public static List<HttpCookie> getCookies(String cookie) {
		List<HttpCookie> cookies = new ArrayList<HttpCookie>();
		if (StringUtils.isEmpty(cookie)) return cookies;
		StringTokenizer token = new StringTokenizer(cookie, ";");
		if (token != null) {
			while (token.hasMoreTokens()) {
				String line = token.nextToken();
				String[] nameValue = line.split("=");
				if (nameValue != null && nameValue.length > 0) {
					String key = nameValue[0].trim();
					StringBuilder sb = new StringBuilder();
					for (int i=1; i<nameValue.length; i++) {
						if (sb.length()>0) {
							sb.append("=");
						}
						sb.append(nameValue[i]);
					}
					String value = sb.toString().replaceAll("^\"|\"$", "").trim();
					try {
						cookies.add(new HttpCookie(key, value));
					} catch (IllegalArgumentException e) {
						//java.net.HttpCookie rejects names that BasicClientCookie accepted
						//(non-token names, and names starting with '$' such as $Version).
						//Skip the entry and keep the rest of the header line. Not swallowed
						//silently: the skipped name is logged.
						LOG.debug("skipped a malformed cookie: name=[" + key + "] " + e.getMessage());
					}
				}
			}
		}
		return cookies;
	}

	public static String getCookieValue(ClassicHttpRequest request, String name) {
		return getCookieValue(getHeader(request, "Cookie", ""), name);
	}

	/**
	 * <p>Get the Cookie value from Cookie header line.
	 * @param cookie header line.
	 * @param name Cookie name
	 * @return value of Cookie name in header line.
	 */
	public static String getCookieValue(String cookie, String name) {
		if (StringUtils.isEmpty(cookie)) return null;
		StringTokenizer token = new StringTokenizer(cookie, ";");
		if (token != null) {
			while (token.hasMoreTokens()) {
				String line = token.nextToken();
				String[] nameValue = line.split("=");
				String key = nameValue[0].trim();
				if (name.equalsIgnoreCase(key)) {
					StringBuilder sb = new StringBuilder();
					for (int i=1; i<nameValue.length; i++) {
						if (sb.length()>0) {
							sb.append("=");
						}
						sb.append(nameValue[i]);
					}
					return sb.toString().replaceAll("^\"|\"$", "").trim();
				}
			}
		}
		return null;
	}
	
	/**
	 * <p>Build a {@code Set-Cookie} header value. The {@code HttpOnly} and
	 * {@code Secure} attributes are driven by the two flags, not by the
	 * cookie's own {@code httpOnly}/{@code secure} state.
	 * @param cookie
	 * @param isHttpOnlyCookie add the {@code HttpOnly} attribute.
	 * @param isSecureCookie add the {@code Secure} attribute.
	 * @return {@code Set-Cookie} header value.
	 */
	public static String getSetCookieValue(HttpCookie cookie, boolean isHttpOnlyCookie, boolean isSecureCookie) {
		return buildSetCookieValue(cookie, isHttpOnlyCookie, isSecureCookie);
	}

	/**
	 * <p>Build a {@code Set-Cookie} header value from the cookie's own
	 * {@code httpOnly}/{@code secure} state.
	 * @param cookie
	 * @return {@code Set-Cookie} header value.
	 */
	public static String getSetCookieValue(HttpCookie cookie) {
		return buildSetCookieValue(cookie, cookie.isHttpOnly(), cookie.getSecure());
	}

	/**
	 * <p>Shared {@code Set-Cookie} serialization for both overloads.
	 * The attribute order is {@code Path}, {@code Domain}, {@code HttpOnly},
	 * {@code Secure}, {@code Max-Age} - unchanged from before the migration.
	 * <p>Expiry is emitted as {@code Max-Age} because {@link java.net.HttpCookie}
	 * has no absolute-expiry representation; the removed httpclient
	 * {@code Cookie.getExpiryDate()} was emitted as {@code Expires}.
	 */
	private static String buildSetCookieValue(HttpCookie cookie, boolean isHttpOnlyCookie, boolean isSecureCookie) {
		StringBuilder value = new StringBuilder();
		value.append(cookie.getName()+"="+cookie.getValue());
		String path = cookie.getPath();
		if (StringUtils.isNotEmpty(path)) {
			value.append("; Path="+cookie.getPath());
		}
		String domain = cookie.getDomain();
		if (StringUtils.isNotEmpty(domain)) {
			value.append("; Domain="+cookie.getDomain());
		}
		if (isHttpOnlyCookie) {
			value.append("; HttpOnly");
		}
		if (isSecureCookie) {
			value.append("; Secure");
		}
		long maxAge = cookie.getMaxAge();
		if (maxAge >= 0) {
			value.append("; Max-Age="+maxAge);
		}
		return value.toString();
	}

	/**
	 * <p>Check for use link convert.
	 * @param contentType
	 * @return true use link convert.
	 */
	public static boolean inContentType(Set<String> contentTypes, Header contentType) {
		if (contentType == null) return false;
		String type = contentType.getValue();
		if (contentTypes.contains(type)) {
			return true;
		} else {
			//Get the content sub type. (text/html; charset=UTF-8 -> html)
			String[] types = type != null ? type.split(";")[0].split("/") : new String[0];
			if (types.length >= 2 && contentTypes.contains(types[1])) {
				return true;
			} else {
				return false;
			}
		}
	}

	public static boolean isFormUrlEncoded(String line) {
		return line != null && line.toLowerCase().startsWith(CONTENT_TYPE_FORM_URLENCODED);

	}
	
	public static boolean isMultipart(String line) {
		return line != null && line.toLowerCase().startsWith("multipart/");
	}
}
