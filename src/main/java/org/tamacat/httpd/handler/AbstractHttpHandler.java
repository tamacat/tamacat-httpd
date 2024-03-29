/*
 * Copyright (c) 2009 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.handler;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.protocol.HttpContext;
import org.tamacat.httpd.config.ServiceUrl;
import org.tamacat.httpd.core.BasicHttpStatus;
import org.tamacat.httpd.core.HttpStatus;
import org.tamacat.httpd.exception.HttpException;
import org.tamacat.httpd.exception.NotFoundException;
import org.tamacat.httpd.exception.ServiceUnavailableException;
import org.tamacat.httpd.filter.HttpFilter;
import org.tamacat.httpd.filter.RequestFilter;
import org.tamacat.httpd.filter.ResponseFilter;
import org.tamacat.httpd.handler.page.ThymeleafErrorPage;
import org.tamacat.httpd.util.MimeUtils;
import org.tamacat.httpd.util.RequestUtils;
import org.tamacat.httpd.util.ServerUtils;
import org.tamacat.log.Log;
import org.tamacat.log.LogFactory;
import org.tamacat.util.ExceptionUtils;
import org.tamacat.util.PropertyUtils;
import org.tamacat.util.ResourceNotFoundException;
import org.tamacat.util.StringUtils;

/**
 * <p>This class is implements of the abstraction of {@link HttpHandler} interface.
 */
public abstract class AbstractHttpHandler implements HttpHandler {

	static final Log LOG = LogFactory.getLog(AbstractHttpHandler.class);
	protected static final String DEFAULT_CONTENT_TYPE = "text/html; charset=UTF-8";

	protected ThymeleafErrorPage errorPage;
	protected String thymeleafPropertyName = "application.properties";
	
	protected ServiceUrl serviceUrl;
	protected String docsRoot;
	protected String encoding = "UTF-8";

	protected List<HttpFilter> filters = new ArrayList<>();
	protected List<RequestFilter> requestFilters = new ArrayList<>();
	protected List<ResponseFilter> responseFilters = new ArrayList<>();
	protected ClassLoader loader;
	
	protected Set<String> allowMethods = new LinkedHashSet<>();
	protected String allowMethodValue;
	
	protected String accessControlAllowOrigin;  //"*"
	protected String accessControlAllowMethods; //"GET,POST,PUT,DELETE,OPTIONS"
	protected String accessControlAllowHeaders; //"Content-Type, Authorization, X-Requested-With"
	
	protected boolean parseRequestParameters = true;
	
	protected AbstractHttpHandler() {
		setAllowMethods("GET,HEAD,POST,OPTIONS");
	}
	
	/**
	 * <p>Set the ServiceUrl and initialized HttpFilters.
	 * @param serviceUrl
	 */
	@Override
	public void setServiceUrl(ServiceUrl serviceUrl) {
		this.serviceUrl = serviceUrl;
		for (HttpFilter filter : filters) {
			filter.init(serviceUrl);
		}
		//v1.5 Velocity -> Thymeleaf
		//errorPage = getErrorPage();
	}
	
	//v1.5 Velocity -> Thymeleaf
	protected ThymeleafErrorPage getErrorPage() {
		if (errorPage == null) {
			Properties props = new Properties();
			try {
				props = PropertyUtils.getProperties(thymeleafPropertyName, getClassLoader());
			} catch (ResourceNotFoundException e) {
			}
			errorPage = new ThymeleafErrorPage(props);
		}
		return errorPage;
	}

	/**
	 * <p>Add the HttpFilter.
	 * @param filter
	 */
	@Override
	public void setHttpFilter(HttpFilter filter) {
		filters.add(filter);
		if (filter instanceof RequestFilter) {
			requestFilters.add((RequestFilter)filter);
		}
		if (filter instanceof ResponseFilter) {
			responseFilters.add((ResponseFilter)filter);
		}
	}

	/**
	 * <p>Set the path of document root.
	 * @param docsRoot
	 */
	public void setDocsRoot(String docsRoot) {
		this.docsRoot = ServerUtils.getServerDocsRoot(docsRoot);
	}

	/**
	 * <p>Set the character encoding. (default UTF-8)
	 * @param encoding
	 */
	public void setEncoding(String encoding) {
		this.encoding = encoding;
	}

	@Override
	public void handle(HttpRequest request, HttpResponse response, HttpContext context) {
		if (isAllowedMethod(request) == false) {
			throw new HttpException(BasicHttpStatus.SC_METHOD_NOT_ALLOWED);
		}
		//OPTIONS request
		if (allowMethodValue != null && "OPTIONS".equals(request.getRequestLine().getMethod())) {
			response.setHeader("Allow", allowMethodValue);
			
			//Add Access-Control response headers. (CORS)
			if (StringUtils.isNotEmpty(accessControlAllowOrigin)) {
				response.setHeader("Access-Control-Allow-Origin", accessControlAllowOrigin);
			}
			if (StringUtils.isNotEmpty(accessControlAllowMethods)) {
				response.setHeader("Access-Control-Allow-Methods", accessControlAllowMethods);
			}
			if (StringUtils.isNotEmpty(accessControlAllowHeaders)) {
				response.setHeader("Access-Control-Allow-Headers", accessControlAllowHeaders);
			}
			return;
		}
		try {
			if (parseRequestParameters) {
				RequestUtils.parseParameters(request, context, encoding);
			}
			for (RequestFilter filter : requestFilters) {
				filter.doFilter(request, response, context);
				if (skipRequestFilter(context)) break;
			}
			if (skipDoRequest(context) == false) {
				doRequest(request, response, context);
			}
		} catch (Exception e) {
			handleException(request, response, e);
		} finally {
			for (ResponseFilter filter : responseFilters) {
				if (skipResponseFilter(context)) break;
				filter.afterResponse(request, response, context);
			}
		}
	}
	
	/**
	 * Parse request parameters in handler method.
	 * @since 1.2 2015-05-05
	 */
	protected void setParseRequestParameters(boolean parseRequestParameters) {
		this.parseRequestParameters = parseRequestParameters;
	}

	/**
	 * Skip process doRequest() method.
	 * @since 1.2 2015-04-06
	 * @param context
	 */
	protected boolean skipDoRequest(HttpContext context) {
		return context.getAttribute(HttpFilter.SKIP_HANDLER_KEY) != null
			|| context.getAttribute(HttpFilter.EXCEPTION_KEY) != null;
	}
	
	/**
	 * Skip request filters.
	 * @since 1.2 2015-04-06
	 * @param context
	 */
	protected boolean skipRequestFilter(HttpContext context) {
		return context.getAttribute(RequestFilter.SKIP_REQUEST_FILTER_KEY) != null;
	}
	
	/**
	 * skip response filters.
	 * @since 1.2 2015-04-06
	 * @param context
	 */
	protected boolean skipResponseFilter(HttpContext context) {
		return context.getAttribute(ResponseFilter.SKIP_RESPONSE_FILTER_KEY) != null;
	}
	
	/**
	 * <p>When the exception is generated by processing {@link handleRequest},
	 *  this method is executed.
	 *
	 * @param request
	 * @param response
	 * @param e
	 */
	protected void handleException(HttpRequest request, HttpResponse response, Exception e) {
		String html = null;
		if (e instanceof HttpException) {
			HttpStatus status = ((HttpException)e).getHttpStatus();
			if (status.isServerError()) {
				LOG.error("Server error: " + status + " - " + e.getMessage());
			}
			if (LOG.isDebugEnabled() && status.isClientError()) {
				LOG.debug("Client error: "+request.getRequestLine()
					+ " " + status.getStatusCode() + " [" + status.getReasonPhrase() + "]");
			}
			html = getErrorPage().getErrorPage(request, response, (HttpException)e);
		} else {
			if (LOG.isWarnEnabled()) {
				LOG.warn(e.getClass().getName()+":"+ request.getRequestLine());
				LOG.warn(ExceptionUtils.getStackTrace(e, 500));
			}
			html = getErrorPage().getErrorPage(request, response,
					new ServiceUnavailableException(e));
		}
		HttpEntity entity = getEntity(html);
		if (!"HEAD".equals(request.getRequestLine().getMethod())) {
			response.setEntity(entity);
		}
	}

	/**
	 * <p>Handling the request, this method is executed after {@link RequestFilter}.
	 * @see {@link executeRequestFilter}
	 * @param request
	 * @param response
	 * @param context
	 * @throws HttpException
	 * @throws IOException
	 */
	protected abstract void doRequest(
				HttpRequest request, HttpResponse response, HttpContext context)
			throws HttpException, IOException;

	/**
	 * <p>The entity is acquired based on the string.
	 * @param html
	 * @return {@link HttpEntity}
	 */
	protected abstract HttpEntity getEntity(String html);

	/**
	 * <p>The entity is acquired based on the file.
	 * @param file
	 * @return {@link HttpEntity}
	 */
	protected abstract HttpEntity getFileEntity(File file);

	/**
	 * <p>The contents type is acquired from the extension. <br>
	 * The correspondence of the extension and the contents type is
	 *  acquired from the {@code mime-types.properties} file. <br>
	 * When there is no file and the extension cannot be acquired,
	 * an {@link DEFAULT_CONTENT_TYPE} is returned.
	 * @param file
	 * @return contents type
	 */
	protected String getContentType(File file) {
		if (file == null) return DEFAULT_CONTENT_TYPE;
		String fileName = file.getName();
		String contentType =  getContentType(fileName);
		return StringUtils.isNotEmpty(contentType)? contentType : DEFAULT_CONTENT_TYPE;
	}

	/**
	 * <p>The contents type is acquired from the extension. <br>
	 * The correspondence of the extension and the contents type is
	 *  acquired from the {@code mime-types.properties} path. <br>
	 * When there is no file and the extension cannot be acquired,
	 * an null is returned.
	 * @param path
	 * @return contents type
	 * @since 1.1
	 */
	protected String getContentType(String path) {
		return MimeUtils.getContentType(path);
	}

	/**
	 * <p>Returns the decoded URI.
	 * When Exception is caught, a throw of the NotFoundException.
	 * @param uri
	 * @return decoded URI default decoding is UTF-8.
	 */
	protected String getDecodeUri(String uri) {
		String decoded = uri;
		try {
			decoded = URLDecoder.decode(uri, encoding);
		} catch (UnsupportedEncodingException e) {
		}
		if (StringUtils.isEmpty(decoded) || decoded.contains("..")) {
			throw new NotFoundException();
		}
		return decoded;
	}

	/**
	 * <p.Set the ClassLoader
	 * @param loader
	 */
	public void setClassLoader(ClassLoader loader) {
		this.loader = loader;
	}

	/**
	 * <p>Get the ClassLoader, default is getClass().getClassLoader().
	 * @return
	 */
	public ClassLoader getClassLoader() {
		return loader != null ? loader : getClass().getClassLoader();
	}
	
	/**
	 * <p>Set the Allow methods.
	 * @param value allowed method (comma separated value)
	 * @since 1.2
	 */
	public void setAllowMethods(String value) {
		allowMethods = new LinkedHashSet<>(); //remake
		if (StringUtils.isEmpty(value)) {
			allowMethodValue = null;
		} else {
			String[] methods = StringUtils.split(value, ",");
			for (String m : methods) {
				String method = m.trim().toUpperCase(Locale.ENGLISH);
				allowMethods.add(method);
			}
			if (allowMethods.size() > 0) {
				allowMethodValue = String.join(",", allowMethods);
			}
		}
	}
	
	/**
	 * <p>Check the request method is allowed.
	 * @param request
	 * @since 1.2
	 */
	public boolean isAllowedMethod(HttpRequest request) {
		//allowMethodValue is null -> allow all methods (don't check this class)
		return allowMethodValue == null || allowMethods.contains(request.getRequestLine().getMethod());
	}
	
	/**
	 * <p>Set Access-Control-Allow-Origin response header. (CORS)
	 * @sinze 1.4-20180904
	 */
	public void setAccessControlAllowOrigin(String accessControlAllowOrigin) {
		this.accessControlAllowOrigin = accessControlAllowOrigin;	
	}
	
	/**
	 * <p>Set Access-Control-Allow-Methods response header. (CORS)
	 * Overrider allowMethods
	 * @param accessControlAllowMethods
	 * @sinze 1.4-20180904
	 */
	public void setAccessControlAllowMethods(String accessControlAllowMethods) {
		this.accessControlAllowMethods = accessControlAllowMethods;
		setAllowMethods(accessControlAllowMethods);
	}
	
	/**
	 * <p>Set Access-Control-Allow-Headers response header. (CORS)
	 * @param accessControlAllowHeaders
	 * @sinze 1.4-20180904
	 */
	public void setAccessControlAllowHeaders(String accessControlAllowHeaders) {
		this.accessControlAllowHeaders = accessControlAllowHeaders;
	}
}
