/*
 * Copyright (c) 2023, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.filter;

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.protocol.HttpContext;
import org.tamacat.httpd.config.ServiceUrl;
import org.tamacat.httpd.util.RequestUtils;
import org.tamacat.log.Log;
import org.tamacat.log.LogFactory;
import org.tamacat.util.StringUtils;

/**
 * RequestFilter for rewrite X-Forwarded-For request header.
 * 
 * convertMode: last, first, none (default:last)
 * - last: If there are multiple values ​​separated by commas, overwrite the last value.
 * - first: If there are multiple values ​​separated by commas, overwrite the first value.
 * 
 * processingMode: convert, append, override, preserve, remove (default:convert)
 * - convert: update X-Forwarded-For value using convertMode.
 * - append: Add the X-Reverse-Forwarded-For header set a IP address of a client.
 * - override: Rewrite the X-Forwarded-For header from X-Reverse-Forwarded-For header value.
 * - preserve: Not modified request.
 * - remove: Removes the X-Forwarded-For header in the request.
 */
public class RewriteXFFHeaderFilter implements RequestFilter {

	static final Log LOG = LogFactory.getLog(RewriteXFFHeaderFilter.class);
	
	static final String X_FORWARDED_FOR = "X-Forwarded-For";
	
	protected String reverseForwardedHeader = "X-Reverse-Forwarded-For";
	protected String processingMode = "convert"; //override, append, preserve, remove
	protected String convertMode = "last"; // first, none
	
	protected ServiceUrl serviceUrl;
	
	@Override
	public void init(ServiceUrl serviceUrl) {
		this.serviceUrl = serviceUrl;
	}

	@Override
	public void doFilter(HttpRequest request, HttpResponse response, HttpContext context) {
		if ("preserve".equalsIgnoreCase(processingMode)) {
			//Not modified request
			return;
		} else if ("remove".equalsIgnoreCase(processingMode)) {
			//Removes the X-Forwarded-For header
			request.removeHeaders(X_FORWARDED_FOR);
			LOG.trace("[remove]" + X_FORWARDED_FOR);
			return;
		}
		try {
			if ("override".equalsIgnoreCase(processingMode)) {
				//Rewrite the X-Forwarded-For header from X-Reverse-Forwarded-For header value.
				String value = getForwardedForValue(request, reverseForwardedHeader);
				if (StringUtils.isNotEmpty(value)) {
					request.setHeader(X_FORWARDED_FOR, value);
					LOG.trace("[override] " + X_FORWARDED_FOR + ": " + value);
				}
				return;
			}
			
			String remoteIP = getForwardedForValue(request, X_FORWARDED_FOR);
			if (StringUtils.isEmpty(remoteIP)) {
				return;
			}
			//delete header reverseForwardedHeader
			request.removeHeaders(reverseForwardedHeader);
			
			if ("append".equalsIgnoreCase(processingMode)) {
				//Add the X-Reverse-Forwarded-For header set a IP address of a client.
				request.setHeader(reverseForwardedHeader, remoteIP);
				LOG.trace("[append] " + reverseForwardedHeader + ": " + remoteIP);
			} else { //convert X-Forwarded-For header value.
				request.setHeader(X_FORWARDED_FOR, remoteIP);
				LOG.trace("[convert] " + X_FORWARDED_FOR + ": " + remoteIP);
			}
		} catch (Exception e) {
			LOG.warn(e.getMessage());
		}
	}
	
	protected String getForwardedForValue(HttpRequest request, String headerName) {
		if ("last".equalsIgnoreCase(convertMode)) {
			return RequestUtils.getForwardedForLastValue(request, headerName);
		} else if ("first".equalsIgnoreCase(convertMode)) {
			return RequestUtils.getForwardedForFirstValue(request, headerName);
		} else {
			return RequestUtils.getForwardedForValue(request, headerName);
		}
	}
	
	/**
	 * Set a header name for reverseForwardedHeader. Default: "X-Reverse-Forwarded-For"
	 * Override from reverseForwardedHeader to X-Forwarded-For request header value.
	 * @param reverseForwardedHeader
	 */
	public void setReverseForwardedHeader(String reverseForwardedHeader) {
		this.reverseForwardedHeader = reverseForwardedHeader;
	}
	
	/**
	 * Set a processing mode of X-Forwarded-For request header.
	 * The possible values for this attribute are convert, override, append, preserve and remove.
	 * The default value for this attribute is convert.
	 * @param ProcessingMode
	 */
	public void setProcessingMode(String processingMode) {
		this.processingMode = processingMode;
	}
	
	/**
	 * Set a convert mode of X-Forwarded-For request header.
	 * The possible values for this attribute are last, first and none.
	 * The default value for this attribute is last.
	 * @param convert
	 */
	public void setConvertMode(String convertMode) {
		this.convertMode = convertMode;
	}
}
