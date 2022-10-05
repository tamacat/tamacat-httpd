/*
 * Copyright (c) 2022 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.handler;

import java.io.File;
import java.io.IOException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.tamacat.httpd.core.BasicHttpStatus;
import org.tamacat.httpd.exception.ForbiddenException;
import org.tamacat.httpd.exception.HttpException;
import org.tamacat.httpd.exception.NotFoundException;
import org.tamacat.httpd.util.RequestUtils;
import org.tamacat.httpd.util.ServerUtils;
import org.tamacat.util.StringUtils;

/**
 * FixedLocalFileHttpHandler: always return a fixed file. 
 * 
 * @since 1.5-20221005
 */
public class FixedLocalFileHttpHandler extends AbstractHttpHandler {

	protected String contentType = DEFAULT_CONTENT_TYPE;
	protected String path;
	protected int statusCode = BasicHttpStatus.SC_OK.getStatusCode();

	/**
	 * Set a ContentType. default: "text/html; charset=UTF-8"
	 * @param contentType
	 */
	public void setContentType(String contentType) {
		this.contentType = contentType;
	}

	/**
	 * Set a status code. default 200 (OK)
	 * @param statusCode
	 */
	public void setStatusCode(String statusCode) {
		this.statusCode = StringUtils.parse(statusCode, BasicHttpStatus.SC_OK.getStatusCode());
	}
	
	/**
	 * Set a fix local file path.
	 * @param path
	 */
	public void setPath(String path) {
		this.path = ServerUtils.getServerDocsRoot(path);
	}

	@Override
	public void doRequest(HttpRequest request, HttpResponse response, HttpContext context)
			throws HttpException, IOException {
		File file = new File(path);
		if (LOG.isTraceEnabled()) {
			LOG.trace(">> " + RequestUtils.getRequestLine(request) + "-> " + file);
		}

		///// 404 NOT FOUND /////
		if (!file.exists()) {
			LOG.debug("File " + file.getPath() + " not found");
			throw new NotFoundException();
		}
		///// 403 FORBIDDEN /////
		else if (!file.canRead() || file.isDirectory()) {
			LOG.trace("Cannot read file " + file.getPath());
			throw new ForbiddenException();
		}
		///// 200 OK /////
		else {
			response.setEntity(getFileEntity(file));
			response.setStatusCode(statusCode);
			response.setHeader(HTTP.CONTENT_TYPE, contentType);
		}
	}

	@Override
	protected HttpEntity getEntity(String html) {
		StringEntity body = null;
		try {
			body = new StringEntity(html, encoding);
			body.setContentType(contentType);
		} catch (Exception e) {
		}
		return body;
	}

	@Override
	protected HttpEntity getFileEntity(File file) {
		FileEntity body = new FileEntity(file);
		return body;
	}
}
