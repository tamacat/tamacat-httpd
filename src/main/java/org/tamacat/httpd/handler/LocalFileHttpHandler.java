/*
 * Copyright (c) 2009 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.handler;

import java.io.File;
import java.io.IOException;

import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.FileEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.tamacat.httpd.config.ServiceUrl;
import org.tamacat.httpd.core.BasicHttpStatus;
import org.tamacat.httpd.exception.ForbiddenException;
import org.tamacat.httpd.exception.HttpException;
import org.tamacat.httpd.exception.NotFoundException;
import org.tamacat.httpd.handler.page.ThymeleafListingsPage;
import org.tamacat.httpd.util.RequestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tamacat.httpd.core.util.StringUtils;

/**
 * <p>The {@link HttpHandler} for local file access.
 */
public class LocalFileHttpHandler extends AbstractHttpHandler {

	private static final Logger LOG = LoggerFactory.getLogger(LocalFileHttpHandler.class);

	protected String welcomeFile = "index.html";
	protected ThymeleafListingsPage listingPage;
	protected boolean listings;

	@Override
	public void setServiceUrl(ServiceUrl serviceUrl) {
		super.setServiceUrl(serviceUrl);
		listingPage = new ThymeleafListingsPage(getErrorPage().getProperties());
	}

	/**
	 * <p>Set the welcome file.
	 * This method use after {@link #setListings}.
	 * @param welcomeFile
	 */
	public void setWelcomeFile(String welcomeFile) {
		this.welcomeFile = welcomeFile;
	}

	/**
	 * <p>Should directory listings be produced
	 * if there is no welcome file in this directory.</p>
	 *
	 * <p>The welcome file becomes unestablished when I set true.<br>
	 * When I set the welcome file, please set it after having
	 * carried out this method.</p>
	 *
	 * @param listings true: directory listings be produced (if welcomeFile is null).
	 */
	public void setListings(boolean listings) {
		this.listings = listings;
		if (listings) {
			this.welcomeFile = null;
		}
	}

	public void setListingsPage(String listingsPage) {
		listingPage.setListingsPage(listingsPage);
	}

	protected boolean useDirectoryListings() {
		if (listings && welcomeFile == null) {
			return true;
		} else {
			return false;
		}
	}

	@Override
	public void doRequest(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context)
			throws HttpException, IOException {
		String path = RequestUtils.getPath(request);
		if (StringUtils.isEmpty(path) || path.contains("..")) {
			throw new NotFoundException();
		}
		if (path.endsWith("/") && useDirectoryListings() == false) {
			path = path + welcomeFile;
		}
		File file = new File(docsRoot, getDecodeUri(path.replace(serviceUrl.getPath(), "/")));
		///// 404 NOT FOUND /////
		if (!file.exists()) {
			LOG.debug("File " + file.getPath() + " not found");
			throw new NotFoundException();
		}
		///// 403 FORBIDDEN /////
		else if (!file.canRead() || file.isDirectory()) {
			if (file.isDirectory() && useDirectoryListings()) {
				String html = listingPage.getListingsPage(
						request, response, file);
				response.setCode(BasicHttpStatus.SC_OK.getStatusCode());
				response.setReasonPhrase(BasicHttpStatus.SC_OK.getReasonPhrase());
				response.setEntity(getEntity(html));
			} else {
				LOG.trace("Cannot read file " + file.getPath());
				throw new ForbiddenException();
			}
		}
		///// 200 OK /////
		else {
			LOG.trace("File " + file.getPath() + " found");
			response.setCode(BasicHttpStatus.SC_OK.getStatusCode());
			response.setReasonPhrase(BasicHttpStatus.SC_OK.getReasonPhrase());
			if (!"HEAD".equals(request.getMethod())) {
				response.setEntity(getFileEntity(file));
			}
			LOG.trace("Serving file " + file.getPath());
		}
	}

	@Override
	protected HttpEntity getEntity(String html) {
		try {
			//core5 entities are immutable: content type is set at construction time.
			return new StringEntity(html,
				ContentType.create(ContentType.parse(DEFAULT_CONTENT_TYPE).getMimeType(), encoding));
		} catch (Exception e) {
			return null;
		}
	}

	@Override
	protected HttpEntity getFileEntity(File file) {
		ContentType contentType = ContentType.DEFAULT_TEXT;
		try {
			contentType = ContentType.create(getContentType(file));
		} catch (Exception e) {
		}
		FileEntity body = new FileEntity(file, contentType);
		return body;
	}
}
