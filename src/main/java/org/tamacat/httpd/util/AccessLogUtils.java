/*
 * Copyright (c) 2009, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.util;

import java.util.Locale;

import org.tamacat.httpcore4.HttpEntity;
import org.tamacat.httpcore4.HttpRequest;
import org.tamacat.httpcore4.HttpResponse;
import org.tamacat.httpcore4.protocol.HTTP;
import org.tamacat.httpcore4.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.tamacat.httpd.core.util.StringUtils;

/**
 * <p>Access log utility.<br>
 *
 * Logger category : Access
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

	static final Logger ACCESS_LOG = LoggerFactory.getLogger("Access");

	/**
	 * Write the access log.
	 * @param context Before set the remote IP address and username.
	 * @param time Response time
	 */
	static
	public void writeAccessLog(
			HttpRequest request, HttpResponse response,
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
			HttpRequest request, HttpResponse response,
			HttpContext context, long time, String forwardHeader) {
		String method = request.getRequestLine().getMethod().toUpperCase(Locale.ENGLISH);
		String uri = request.getRequestLine().getUri();
		int statusCode = response.getStatusLine().getStatusCode();
		String reasonPhrase = response.getStatusLine().getReasonPhrase();
		String proto = request.getProtocolVersion().toString();
		String ip = RequestUtils.getRemoteIPAddress(request, context, forwardHeader != null, forwardHeader);
		if (ip == null) ip = "";
		String remoteUser = (String) context.getAttribute("REMOTE_USER"); //TODO
		if (StringUtils.isEmpty(remoteUser)) remoteUser = "-";
		HttpEntity entity = response.getEntity();
		long size = entity != null ? entity.getContentLength() : 0;
		if (size == -1) {
			String contentLen= HeaderUtils.getHeader(response, HTTP.CONTENT_LEN);
			if (StringUtils.isNotEmpty(contentLen)) {
				size = StringUtils.parse(contentLen, -1L);
			}
		}
		MDC.put("ip", ip);
		MDC.put("user", remoteUser);
		String message = method + " " + uri + " " + proto +" " + statusCode
		+ " [" + reasonPhrase + "] " + size + " (" + time + "ms)";
		if (statusCode < 500) {
			ACCESS_LOG.info(message);
		} else {
			ACCESS_LOG.error(message);
		}
	}
}
