/*
 * Copyright (c) 2026 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.util;

import java.util.Date;

/**
 * <p>Minimal Cookie value holder used by {@link HeaderUtils}.
 * <p>Reimplemented on the JDK only (no external dependency), replacing the
 * previously used httpclient-specific {@code org.apache.http.cookie.Cookie} /
 * {@code org.apache.http.impl.cookie.BasicClientCookie} now that the
 * httpclient dependency has been removed from this project (v1.6 EOL-OSS
 * removal). Carries the same fields {@link HeaderUtils#getSetCookieValue}
 * reads: name, value, path, domain, and an absolute expiry date.
 */
public class Cookie {

	private final String name;
	private final String value;
	private String path;
	private String domain;
	private Date expiryDate;

	public Cookie(String name, String value) {
		this.name = name;
		this.value = value;
	}

	public String getName() {
		return name;
	}

	public String getValue() {
		return value;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getDomain() {
		return domain;
	}

	public void setDomain(String domain) {
		this.domain = domain;
	}

	public Date getExpiryDate() {
		return expiryDate;
	}

	public void setExpiryDate(Date expiryDate) {
		this.expiryDate = expiryDate;
	}
}
