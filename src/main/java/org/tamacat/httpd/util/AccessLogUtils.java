/*
 * Copyright (c) 2009, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.util;

import java.util.Locale;

import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.tamacat.httpd.core.HttpContextKeys;
import org.tamacat.log.DiagnosticContext;
import org.tamacat.log.Log;
import org.tamacat.log.LogFactory;
import org.tamacat.util.StringUtils;

/**
 * <p>Access log utility.<br>
 *
 * Log category : Access
 *
 * <p>logging for:
 * <ul>
 *  <li>Remote IP address</li>
 *  <li>Access time</li>
 *  <li>Remote Username</li>
 *  <li>URL</li>
 *  <li>HTTP status code</li>
 *  <li>Content-Length(size)</li>
 *  <li>Response time</li>
 * </ul>
 */
public class AccessLogUtils {

	static final Log ACCESS_LOG = LogFactory.getLog("Access");
	static final DiagnosticContext DC = LogFactory.getDiagnosticContext(ACCESS_LOG);

	/**
	 * Write the access log.
	 * @param context Before set the remote IP address and username.
	 * @param time Response time
	 */
	static
	public void writeAccessLog(
			ClassicHttpRequest request, ClassicHttpResponse response,
			HttpContext context, long time) {
		writeAccessLog(request, response, context, time, null);
	}
	
	/**
	 * Write the access log.
	 * @param context Before set the remote IP address and username.
	 * @param time Response time
	 */
	static
	public void writeAccessLog(
			ClassicHttpRequest request, ClassicHttpResponse response,
			HttpContext context, long time, String forwardHeader) {
		String method = request.getMethod().toUpperCase(Locale.ENGLISH);
		String uri = request.getRequestUri();
		int statusCode = response.getCode();
		String reasonPhrase = response.getReasonPhrase();
		String proto = RequestUtils.getVersion(request).toString();
		String ip = RequestUtils.getRemoteIPAddress(request, context, forwardHeader != null, forwardHeader);
		if (ip == null) ip = "";
		String remoteUser = (String) context.getAttribute(HttpContextKeys.REMOTE_USER);
		if (StringUtils.isEmpty(remoteUser)) remoteUser = "-";
		HttpEntity entity = response.getEntity();
		long size = entity != null ? entity.getContentLength() : 0;
		if (size == -1) {
			String contentLen= HeaderUtils.getHeader(response, HttpHeaders.CONTENT_LENGTH);
			if (StringUtils.isNotEmpty(contentLen)) {
				size = StringUtils.parse(contentLen, -1L);
			}
		}
		DC.setMappedContext("ip", ip);
		DC.setMappedContext("user", remoteUser);
		String message = method + " " + uri + " " + proto +" " + statusCode
		+ " [" + reasonPhrase + "] " + size + " (" + time + "ms)";
		if (statusCode < 500) {
			ACCESS_LOG.info(message);
		} else {
			ACCESS_LOG.error(message);
		}
	}
}
