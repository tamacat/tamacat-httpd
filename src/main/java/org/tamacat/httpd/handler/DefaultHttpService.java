/*
 * Copyright (c) 2009, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.handler;

import java.io.IOException;
import java.util.Properties;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseFactory;
import org.apache.http.HttpServerConnection;
import org.apache.http.entity.StringEntity;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpExpectationVerifier;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.HttpRequestHandlerMapper;
import org.apache.http.protocol.HttpService;
import org.tamacat.httpd.core.HttpProcessorBuilder;
import org.tamacat.httpd.exception.NotFoundException;
import org.tamacat.httpd.exception.ServiceUnavailableException;
import org.tamacat.httpd.handler.page.ThymeleafErrorPage;
import org.tamacat.httpd.util.RequestUtils;
import org.tamacat.log.Log;
import org.tamacat.log.LogFactory;
import org.tamacat.util.PropertyUtils;
import org.tamacat.util.ResourceNotFoundException;

/**
 * <p>The default implements of {@link HttpService}.
 */
public class DefaultHttpService extends HttpService {

	static final Log LOG = LogFactory.getLog(DefaultHttpService.class);

	static final String DEFAULT_CONTENT_TYPE = "text/html; charset=UTF-8";

	protected HttpRequestHandlerMapper handleMapper;
	protected HostRequestHandlerMapper hostResolver;
	protected ClassLoader loader;
	protected ThymeleafErrorPage errorPage;
	protected String encoding = "UTF-8";
	protected String contentType = DEFAULT_CONTENT_TYPE;

	public DefaultHttpService(HttpProcessorBuilder procBuilder,
			ConnectionReuseStrategy connStrategy,
			HttpResponseFactory responseFactory,
			HttpRequestHandlerMapper handleMapper,
			HttpExpectationVerifier verifier) {
		super(procBuilder.build(), connStrategy, responseFactory,
			handleMapper, verifier);
	}

	public void setHostHandlerResolver(HostRequestHandlerMapper hostResolver) {
		this.hostResolver = hostResolver;
	}

	@Override
	public final void handleRequest(
			final HttpServerConnection conn,
			final HttpContext context) throws IOException, HttpException {
		RequestUtils.setRemoteAddress(context, conn);
		super.handleRequest(conn, context);
	}

	@Override
	//handleRequest() -> doService() -> service()
	protected void doService(HttpRequest request, HttpResponse response, HttpContext context) {
		try {
			LOG.trace("doService() >> " + request.getRequestLine().getUri());
			HttpRequestHandler handler = null;
			if (handleMapper != null) {
				handler = handleMapper.lookup(request);
			} else if (hostResolver != null) {
				handler = hostResolver.lookup(request, context);
			}
			if (handler != null) {
				handler.handle(request, response, context);
			} else {
				throw new NotFoundException();
			}
		} catch (Exception e) {
			if (e instanceof org.tamacat.httpd.exception.HttpException) {
				handleException(request, response,
						(org.tamacat.httpd.exception.HttpException)e);
			} else {
				handleException(request, response,
					new ServiceUnavailableException());
			}
		}
	}

	/**
	 * <p>Handling the exception for {@link org.tamacat.httpd.exception.HttpException}.<br>
	 * The response of the error page corresponding to the HTTP status cord.
	 * @param request
	 * @param response
	 * @param e
	 */
	protected void handleException(HttpRequest request, HttpResponse response,
			org.tamacat.httpd.exception.HttpException e) {
		String html = getErrorPage().getErrorPage(request, response, e);
		response.setEntity(getEntity(html));
	}

	protected ThymeleafErrorPage getErrorPage() {
		if (errorPage == null) {
			Properties props = new Properties();
			try {
				props = PropertyUtils.getProperties("application.properties", getClassLoader());
			} catch (ResourceNotFoundException e) {
			}
			errorPage = new ThymeleafErrorPage(props);
		}
		return errorPage;
	}

	/**
	 * <p>Returns the {@link HttpEntity}.<br>
	 * Content-Type is using {@link DEFAULT_CONTENT_TYPE}.
	 * @param html
	 * @return HttpEntity
	 */
	protected HttpEntity getEntity(String html) {
		try {
			StringEntity entity = new StringEntity(html, encoding);
			entity.setContentType(contentType);
			return entity;
		} catch (Exception e1) {
			return null;
		}
	}

	public void setEncoding(String encoding) {
		this.encoding = encoding;
	}

	public void setContentType(String contentType) {
		this.contentType = contentType;
	}

	public void setClassLoader(ClassLoader loader) {
		this.loader = loader;
	}

	public ClassLoader getClassLoader() {
		return loader != null ? loader : getClass().getClassLoader();
	}
}
