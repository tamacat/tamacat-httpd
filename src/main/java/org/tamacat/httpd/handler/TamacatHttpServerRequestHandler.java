/*
 * Copyright (c) 2026, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.handler;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Properties;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequestMapper;
import org.apache.hc.core5.http.HttpResponseFactory;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.io.DefaultClassicHttpResponseFactory;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.HttpServerRequestHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.tamacat.httpd.exception.NotFoundException;
import org.tamacat.httpd.exception.ServiceUnavailableException;
import org.tamacat.httpd.handler.page.ThymeleafErrorPage;
import org.tamacat.log.Log;
import org.tamacat.log.LogFactory;
import org.tamacat.util.ExceptionUtils;
import org.tamacat.util.PropertyUtils;
import org.tamacat.util.ResourceNotFoundException;

/**
 * <p>The tamacat implementation of core5's {@link HttpServerRequestHandler}.
 *
 * <p>This class replaces {@code org.tamacat.httpd.handler.DefaultHttpService},
 * which extended {@code org.apache.http.protocol.HttpService} and overrode
 * {@code doService(...)}. HttpComponents Core 5.x has no {@code doService}
 * extension point: {@code org.apache.hc.core5.http.impl.io.HttpService} takes an
 * {@code HttpServerRequestHandler} by constructor injection instead.
 *
 * <p>core5 ships {@code io.support.BasicHttpServerRequestHandler} for that role,
 * but it is deliberately <b>not</b> used here, because it differs from the
 * tamacat behaviour in two ways that are part of the public contract:
 * <ol>
 *   <li>an unresolved request path yields {@code 501 Not Implemented};
 *       tamacat throws {@link NotFoundException} and renders a <b>404</b> error page,</li>
 *   <li>exceptions thrown by the request handler are not caught at all, which would
 *       disable the {@link ThymeleafErrorPage} mechanism entirely.</li>
 * </ol>
 *
 * @since 2.0
 */
public class TamacatHttpServerRequestHandler implements HttpServerRequestHandler {

	static final Log LOG = LogFactory.getLog(TamacatHttpServerRequestHandler.class);

	static final String DEFAULT_CONTENT_TYPE = "text/html; charset=UTF-8";

	protected final HttpRequestMapper<HttpRequestHandler> handlerMapper;
	protected final HttpResponseFactory<ClassicHttpResponse> responseFactory;

	protected ClassLoader loader;
	protected ThymeleafErrorPage errorPage;
	protected String encoding = "UTF-8";
	protected String contentType = DEFAULT_CONTENT_TYPE;

	public TamacatHttpServerRequestHandler(HttpRequestMapper<HttpRequestHandler> handlerMapper) {
		this(handlerMapper, null);
	}

	public TamacatHttpServerRequestHandler(
			HttpRequestMapper<HttpRequestHandler> handlerMapper,
			HttpResponseFactory<ClassicHttpResponse> responseFactory) {
		this.handlerMapper = handlerMapper;
		this.responseFactory = responseFactory != null
			? responseFactory : DefaultClassicHttpResponseFactory.INSTANCE;
	}

	/**
	 * <p>Resolve the {@link HttpRequestHandler} for the request, run it, and hand
	 * the response over to the {@code responseTrigger}.
	 *
	 * <p>This is the core5 counterpart of {@code DefaultHttpService.doService(...)}.
	 * The resolution / execution / error page structure is kept as it was.
	 */
	@Override
	public void handle(
			ClassicHttpRequest request,
			ResponseTrigger responseTrigger,
			HttpContext context) throws HttpException, IOException {
		ClassicHttpResponse response = responseFactory.newHttpResponse(HttpStatus.SC_OK);
		try {
			LOG.trace("handle() >> " + request.getRequestUri());
			HttpRequestHandler handler = handlerMapper != null
				? handlerMapper.resolve(request, context) : null;
			if (handler != null) {
				handler.handle(request, response, context);
			} else {
				//404 Not Found. (core5's BasicHttpServerRequestHandler would answer 501 here.)
				throw new NotFoundException();
			}
		} catch (Exception e) {
			if (e instanceof org.tamacat.httpd.exception.HttpException) {
				handleException(request, response,
						(org.tamacat.httpd.exception.HttpException) e);
			} else {
				//The cause is not carried over to the error page, but it must not be swallowed.
				if (LOG.isWarnEnabled()) {
					LOG.warn(e.getClass().getName() + ": " + request.getRequestUri());
					LOG.debug(ExceptionUtils.getStackTrace(e, 500));
				}
				handleException(request, response, new ServiceUnavailableException());
			}
		}
		responseTrigger.submitResponse(response);
	}

	/**
	 * <p>Handling the exception for {@link org.tamacat.httpd.exception.HttpException}.<br>
	 * The response of the error page corresponding to the HTTP status cord.
	 * @param request
	 * @param response
	 * @param e
	 */
	protected void handleException(ClassicHttpRequest request, ClassicHttpResponse response,
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
	 * Content-Type is using {@link #DEFAULT_CONTENT_TYPE}.
	 * <p>In core5 the content type of an entity is immutable and set at construction
	 * time, so the MIME type of {@code contentType} and the charset of {@code encoding}
	 * are combined here. With the defaults this yields the same
	 * {@code text/html; charset=UTF-8} the 4.4 implementation produced.
	 * @param html
	 * @return HttpEntity, or {@code null} when the content type or the encoding
	 *   cannot be resolved.
	 */
	protected HttpEntity getEntity(String html) {
		try {
			ContentType type = ContentType.create(
				ContentType.parse(contentType).getMimeType(), Charset.forName(encoding));
			return new StringEntity(html, type);
		} catch (Exception e) {
			LOG.warn("Can not create the error page entity. contentType=" + contentType
				+ ", encoding=" + encoding + " - " + e.getMessage());
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
