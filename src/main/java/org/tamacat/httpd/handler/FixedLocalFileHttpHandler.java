/*
 * Copyright (c) 2022 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.handler;

import java.io.File;
import java.io.IOException;

import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.FileEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.tamacat.httpd.core.BasicHttpStatus;
import org.tamacat.httpd.exception.ForbiddenException;
import org.tamacat.httpd.exception.HttpException;
import org.tamacat.httpd.exception.NotFoundException;
import org.tamacat.httpd.util.RequestUtils;
import org.tamacat.httpd.util.ServerUtils;
import org.tamacat.httpd.core.util.StringUtils;

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
	public void doRequest(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context)
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
			response.setCode(statusCode);
			response.setHeader(HttpHeaders.CONTENT_TYPE, contentType);
		}
	}

	@Override
	protected HttpEntity getEntity(String html) {
		try {
			//core5 entities are immutable: the content type is fixed at construction
			//time, so the MIME type and the charset are combined here instead of
			//calling the setContentType() that 4.4 offered.
			return new StringEntity(html,
				ContentType.create(ContentType.parse(contentType).getMimeType(), encoding));
		} catch (Exception e) {
			return null;
		}
	}

	@Override
	protected HttpEntity getFileEntity(File file) {
		//core5 has no FileEntity(File) constructor; the content type is mandatory.
		//DEFAULT_BINARY is the closest equivalent of 4.4's "no content type set".
		return new FileEntity(file, ContentType.DEFAULT_BINARY);
	}
}
