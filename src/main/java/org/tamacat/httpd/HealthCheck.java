/*
 * Copyright (c) 2009, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd;

import java.net.HttpURLConnection;
import java.net.URI;

/**
 * Standalone healthcheck probe for the container's exec-form Docker
 * HEALTHCHECK. Introduces no dependency on any other tamacat-httpd class
 * and makes no change to existing behavior.
 */
public final class HealthCheck {

	private HealthCheck() {
	}

	public static void main(String[] args) {
		try {
			HttpURLConnection conn = (HttpURLConnection)
				URI.create("http://localhost/check.html").toURL().openConnection();
			conn.setConnectTimeout(3000);
			conn.setReadTimeout(3000);
			conn.setRequestMethod("GET");
			int status = conn.getResponseCode();
			conn.disconnect();
			System.exit(status == 200 ? 0 : 1);
		} catch (Exception e) {
			System.exit(1);
		}
	}
}
