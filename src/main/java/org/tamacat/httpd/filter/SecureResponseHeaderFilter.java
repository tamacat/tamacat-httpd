/*
 * Copyright (c) 2019 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.filter;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.protocol.HttpContext;
import org.tamacat.httpd.config.ServiceUrl;
import org.tamacat.httpd.exception.BadRequestException;
import org.tamacat.httpd.exception.ForbiddenException;
import org.tamacat.httpd.exception.InternalServerErrorException;
import org.tamacat.httpd.exception.NotFoundException;
import org.tamacat.httpd.exception.ServiceUnavailableException;
import org.tamacat.httpd.util.HeaderUtils;
import org.tamacat.httpd.util.MimeUtils;
import org.tamacat.util.StringUtils;

/**
 * Add Secure Response Header.
 * This Filter for security measures to be set when header is not set.
 * 
 * Adding response headers. (default)
 * <pre>
 * X-Frame-Options: DENY
 * X-ContentType-Options: nosniff
 * X-XSS-Protection: 1; mode=block
 * Expires: Thu, 01 Jan 1970 00:00:00 GMT
 * Cache-Control: no-store, no-cache, must-revalidate, post-check=0, pre-check=0 Pragma: no-cache
 * </pre>
 * 
 * When Content-Type header is not set, Content-type is determined from mime-types.properties
 * based on the extension and set in response header. (default: "text/html; charset=UTF-8")
 * 
 * When Content-type is font, header related to Cache-Control is not added. (for IE11)
 */
public class SecureResponseHeaderFilter implements ResponseFilter {
	
	protected static final String DEFAULT_CONTENT_TYPE = "text/html; charset=UTF-8";
	protected String defaultContentType = DEFAULT_CONTENT_TYPE;
	
	protected String frameOptions = "DENY";
	protected String contentTypeOptions = "nosniff";
	protected String xssProtection = "1; mode=block";
	protected String expires = "Thu, 01 Jan 1970 00:00:00 GMT";
	protected String cacheControl = "no-store, no-cache, must-revalidate, post-check=0, pre-check=0";
	protected String pragma = "no-cache";
	
	protected String forceReplaceErrorHeaderName = "X-Override-Error";
	protected String forceReplaceErrorPage = "";
	protected Map<String, String> appendResponseHeaders = new LinkedHashMap<>();

	@Override
	public void init(ServiceUrl serviceUrl) {
	}

	@Override
	public void afterResponse(HttpRequest request, HttpResponse response, HttpContext context) {
		if (response.getStatusLine().getStatusCode() <= 200 && response.getStatusLine().getStatusCode() < 300
		  && response.getEntity() != null
		  && StringUtils.isEmpty(response.getEntity().getContentType())
		  && response.containsHeader(HttpHeaders.CONTENT_TYPE) == false) {
			response.setHeader(HttpHeaders.CONTENT_TYPE, getContentType(request.getRequestLine().getUri()));
		}
		
		if (StringUtils.isNotEmpty(frameOptions) && response.containsHeader("X-Frame-Options") == false) {
			response.setHeader("X-Frame-Options", frameOptions);
		}
		if (StringUtils.isNotEmpty(contentTypeOptions) && response.containsHeader("X-Content-Type-Options") == false) {
			response.setHeader("X-Content-Type-Options", contentTypeOptions);
		}
		if (StringUtils.isNotEmpty(xssProtection) && response.containsHeader("X-XSS-Protection") == false) {
			response.setHeader("X-XSS-Protection", xssProtection);
		}
		if (isAddCacheControlHeaders(response)) {
			if (StringUtils.isNotEmpty(expires) && response.containsHeader(HttpHeaders.EXPIRES) == false) {
				response.setHeader(HttpHeaders.EXPIRES, expires);
			}
			if (StringUtils.isNotEmpty(cacheControl) && response.containsHeader(HttpHeaders.CACHE_CONTROL) == false) {
				response.setHeader(HttpHeaders.CACHE_CONTROL, cacheControl);
			}
			if (StringUtils.isNotEmpty(pragma) && response.containsHeader(HttpHeaders.PRAGMA) == false) {
				response.setHeader(HttpHeaders.PRAGMA, pragma);
			}
		}
		
		//Append Response Headers (DO NOT Override exists headers)
		if (appendResponseHeaders.size() >= 1) {
			for (String name : appendResponseHeaders.keySet()) {
				if (response.containsHeader(name) == false) {
					String value = appendResponseHeaders.get(name);
					response.setHeader(name, value);
				}
			}
		}
		
		//force replace Error Page
		if (isForceReplaceErrorPage(response)) {
			int status = response.getStatusLine().getStatusCode();
			if (400 == status && forceReplaceErrorPage.contains("400")) {
				throw new BadRequestException();
			} else if (403 == status && forceReplaceErrorPage.contains("403")) {
				throw new ForbiddenException();
			} else if (404 == status && forceReplaceErrorPage.contains("404")) {
				throw new NotFoundException();
			} else if (500 == status && forceReplaceErrorPage.contains("500")) {
				throw new InternalServerErrorException();
			} else if (503 == status && forceReplaceErrorPage.contains("503")) {
				throw new ServiceUnavailableException();
			}
		}
		//delete header
		if (response.containsHeader(forceReplaceErrorHeaderName)) {
			response.removeHeaders(forceReplaceErrorHeaderName);
		}
	}
	
	protected boolean isAddCacheControlHeaders(HttpResponse response) {
		String contentType = getContentType(response);
		if (StringUtils.isNotEmpty(contentType)) {
			//for IE11 Web Fonts
			if (contentType.startsWith("font/")) {
				return false;
			}
		}
		return true;
	}
	
	protected String getContentType(HttpResponse response) {
		String contentType = HeaderUtils.getHeader(response, HttpHeaders.CONTENT_TYPE);
		HttpEntity entity = response.getEntity();
		if (StringUtils.isEmpty(contentType) && entity != null) {
			Header h = response.getEntity().getContentType();
			if (h != null) {
				contentType = h.getValue();
			}
		}
		return contentType;
	}

	protected String getContentType(String path) {
		try {
			String contentType = MimeUtils.getContentType(path);
			if (StringUtils.isNotEmpty(contentType)) {
				return contentType.replace("\r","").replace("\n","");
			}
		} catch (Exception e) {
		}
		return defaultContentType;
	}
	
	public void setFrameOptions(String frameOptions) {
		this.frameOptions = frameOptions;
	}

	public void setContentTypeOptions(String contentTypeOptions) {
		this.contentTypeOptions = contentTypeOptions;
	}

	public void setXssProtection(String xssProtection) {
		this.xssProtection = xssProtection;
	}

	public void setExpires(String expires) {
		this.expires = expires;
	}

	public void setCacheControl(String cacheControl) {
		this.cacheControl = cacheControl;
	}

	public void setPragma(String pragma) {
		this.pragma = pragma;
	}
	
	public void setDefaultContentType(String defaultContentType) {
		this.defaultContentType = defaultContentType;
	}
	
	/**
	 * force replace error page.
	 * default "" -> disabled
	 * ex) "400,503"
	 * @param status
	 * @since 1.5
	 */
	public void setForceReplaceErrorPage(String status) {
		this.forceReplaceErrorPage = status;
	}
	
	/**
	 * check force replace error page.
	 * disabled:  response#setHeader("X-Override-Error", "disabled");
	 */
	protected boolean isForceReplaceErrorPage(HttpResponse response) {
		return StringUtils.isNotEmpty(forceReplaceErrorPage)
			&& "disabled".equalsIgnoreCase(HeaderUtils.getHeader(response, forceReplaceErrorHeaderName))==false;
	}
	
	/**
	 * Append Response Headers.
	 * ex) "Strict-Transport-Security: max-age=63072000; includeSubDomains; preload"
	 * @param headerValue
	 * @since 1.5
	 */
	public void setAppendResponseHeader(String headerValue) {
		String[] nameValue = StringUtils.split(headerValue, ":");
		if (nameValue.length >= 2) {
			String name = nameValue[0].trim();
			String value = headerValue.replace(nameValue[0]+":", "").trim();
			if (StringUtils.isNotEmpty(name) && StringUtils.isNotEmpty(value)) {
				appendResponseHeaders.put(name, value);
			}
		}
	}
}
