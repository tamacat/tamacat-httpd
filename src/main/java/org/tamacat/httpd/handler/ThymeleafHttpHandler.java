/*
 * Copyright 2019 tamacat.org
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package org.tamacat.httpd.handler;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.protocol.HttpContext;
import org.tamacat.httpd.core.BasicHttpStatus;
import org.tamacat.httpd.core.RequestParameters;
import org.tamacat.httpd.exception.HttpException;
import org.tamacat.httpd.exception.NotFoundException;
import org.tamacat.httpd.handler.page.ThymeleafListingsPage;
import org.tamacat.httpd.handler.page.ThymeleafPage;
import org.tamacat.httpd.util.RequestUtils;
import org.tamacat.util.StringUtils;
import org.thymeleaf.context.Context;

/**
 * <p>
 * It is implements of {@link HttpHandler} that uses {@code Thymeleaf}.
 */
public class ThymeleafHttpHandler extends AbstractHttpHandler {

	public static final String CONTENT_TYPE = "ResponseHeader__ContentType__";
	protected String welcomeFile = "index";
	protected boolean listings;

	protected ThymeleafListingsPage listingPage;
	protected ThymeleafPage page;
	protected final Set<String> urlPatterns = new HashSet<>();

	public void setUrlPatterns(String patterns) {
		for (String pattern : patterns.split(",")) {
			urlPatterns.add(pattern.trim());
		}
	}

	public boolean isMatchUrlPattern(String path) {
		if (urlPatterns.size() > 0) {
			for (String pattern : urlPatterns) {
				if (pattern.endsWith("/") && path.matches(pattern)) {
					return true;
				} else if (path.lastIndexOf(pattern) >= 0) {
					return true;
				}
			}
		} else if (path.lastIndexOf(".html") >= 0) {
			return true;
		}
		return false;
	}

	@Override
	public void setDocsRoot(String docsRoot) {
		super.setDocsRoot(docsRoot);
		Properties props = getErrorPage().getProperties();
		
        listingPage = new ThymeleafListingsPage(props);
        page = new ThymeleafPage(props, this.docsRoot);
	}

	/**
	 * <p>
	 * Set the welcome file. This method use after {@link #setListings}.
	 * 
	 * @param welcomeFile
	 */
	public void setWelcomeFile(String welcomeFile) {
		this.welcomeFile = welcomeFile;
	}

	/**
	 * <p>
	 * Should directory listings be produced if there is no welcome file in this
	 * directory.
	 * </p>
	 *
	 * <p>
	 * The welcome file becomes unestablished when I set true.<br>
	 * When I set the welcome file, please set it after having carried out this
	 * method.
	 * </p>
	 *
	 * @param listings
	 *            true: directory listings be produced (if welcomeFile is null).
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
		if (listings) {
			return true;
		} else {
			return false;
		}
	}

	@Override
	protected void doRequest(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException, IOException {
		try {
			Context ctx = (Context) context.getAttribute(Context.class.getName());
			if (ctx == null) {
				ctx = new Context();
			}
			String path = RequestUtils.getPath(request);
			if (StringUtils.isEmpty(path) || path.contains("..")) {
				throw new NotFoundException();
			}
			
			RequestParameters params = RequestUtils.parseParameters(request, context, encoding);
			if (params != null) {
				ctx.setVariable("param", params.getParameterMap());
			}
			
			ctx.setVariable("contextRoot", serviceUrl.getPath().replaceFirst("/$", ""));
			if (isMatchUrlPattern(path)) {
				// delete the extention of file name. (index.html -> index)
				String file = path.contains(".") ? path.split("\\.")[0] : path;
				setEntity(request, response, ctx, file);
			} else if (path.endsWith("/")) {
				// directory -> index page.
				if (welcomeFile == null) {
					welcomeFile = "index";
				}
				File file = new File(docsRoot + getDecodeUri(path + welcomeFile));
				if (useDirectoryListings() && file.canRead() == false) {
					file = new File(docsRoot + getDecodeUri(path));
					setListFileEntity(request, response, file);
				} else {
					setEntity(request, response, ctx, path + "index");
				}
			} else {
				// get the file in this server.
				setFileEntity(request, response, path);
			}
		} catch (HttpException e) {
			throw e;
		} catch (Exception e) {
			throw new NotFoundException(e);
		}
	}

	protected void setListFileEntity(HttpRequest request, HttpResponse response, File file) {
		try {
			String html = listingPage.getListingsPage(request, response, file);
			if (!"HEAD".equals(request.getRequestLine().getMethod())) {
				response.setEntity(getEntity(html));
			}
			response.setStatusCode(BasicHttpStatus.SC_OK.getStatusCode());
			response.setReasonPhrase(BasicHttpStatus.SC_OK.getReasonPhrase());
		} catch (Exception e) {
			throw new NotFoundException(e);
		}
	}

	protected void setEntity(HttpRequest request, HttpResponse response, Context ctx, String path) {
		// Do not set an entity when it already exists.
		if (response.getEntity() == null) {
			String html = page.getPage(request, response, ctx, path);
			Object contentType = ctx.getVariable(CONTENT_TYPE);
			if (!"HEAD".equals(request.getRequestLine().getMethod())) {
				if (contentType != null && contentType instanceof String) {
					response.setEntity(getEntity(html, (String) contentType));
				} else {
					response.setEntity(getEntity(html));
				}
			}
		}
	}

	protected void setFileEntity(HttpRequest request, HttpResponse response, String path) {
		// Do not set an entity when it already exists.
		if (response.getEntity() == null) {
			if (StringUtils.isEmpty(path) || path.contains("..")) {
				throw new NotFoundException();
			}
			try {
				File file = new File(docsRoot + getDecodeUri(path));// r.toURI());
				if (file.isDirectory() || !file.exists() || !file.canRead()) {
					throw new NotFoundException(path + " is not found this server.");
				}
				if (!"HEAD".equals(request.getRequestLine().getMethod())) {
					response.setEntity(getFileEntity(file));
				}
			} catch (HttpException e) {
				throw e;
			} catch (Exception e) {
				throw new NotFoundException(e);
			}
		}
	}

	protected HttpEntity getEntity(String html, String contentType) {
		try {
			StringEntity entity = new StringEntity(html, encoding);
			entity.setContentType(contentType);
			return entity;
		} catch (Exception e1) {
			return null;
		}
	}

	@Override
	protected HttpEntity getEntity(String html) {
		try {
			StringEntity entity = new StringEntity(html, encoding);
			entity.setContentType(DEFAULT_CONTENT_TYPE);
			return entity;
		} catch (Exception e1) {
			return null;
		}
	}

	protected HttpEntity getFileEntity(File file, String contentType) {
		ContentType type = ContentType.DEFAULT_TEXT;
		try {
			type = ContentType.create(contentType, encoding);
		} catch (Exception e) {
		}
		return new FileEntity(file, type);
	}

	@Override
	protected HttpEntity getFileEntity(File file) {
		ContentType type = ContentType.DEFAULT_TEXT;
		try {
			type = ContentType.create(getContentType(file));
		} catch (Exception e) {
		}
		return new FileEntity(file, type);
	}
}
