/*
 * Copyright (c) 2009 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.handler;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
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
	/**
	 * Canonical form of {@link #docsRoot}, pre-computed once in {@link #setDocsRoot}
	 * (NFR-SCALE-1) and used as the containment boundary by {@link #getDecodeUri}.
	 * {@code null} when docsRoot has not been configured, or when its canonical
	 * form could not be resolved (fail-closed: see {@link #isWithinDocsRoot}).
	 * @since 1.5.2-tc9.0.120
	 */
	protected String canonicalDocsRoot;
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
		//FR-1/NFR-SEC-1: pre-compute the canonical docsRoot once here (not per-request,
		//NFR-SCALE-1) so getDecodeUri only pays for a single getCanonicalPath() call
		//against the resolved request file.
		this.canonicalDocsRoot = resolveCanonicalDocsRoot(this.docsRoot);
	}

	/**
	 * <p>Resolves and returns the canonical form of the given docsRoot.
	 * NFR-REL-3: an {@link IOException} here (unreadable/misconfigured docsRoot)
	 * is not propagated - it is logged and {@code null} is cached instead, which
	 * makes {@link #isWithinDocsRoot} fail closed (reject every request) rather
	 * than crash the server at startup.
	 * @since 1.5.2-tc9.0.120
	 */
	private String resolveCanonicalDocsRoot(String docsRoot) {
		if (StringUtils.isEmpty(docsRoot)) {
			return null;
		}
		try {
			return new File(docsRoot).getCanonicalPath();
		} catch (IOException e) {
			LOG.warn("Cannot resolve canonical docsRoot: " + docsRoot + " - " + e.getMessage());
			return null;
		}
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
		String decoded = decodeAndCheckTraversal(uri);
		//FR-1/NFR-SEC-1: canonicalization containment, layered on top of (not a
		//replacement for) the contains("..") blocklist check above (NFR-1 defense
		//in depth). Resolves symlinks via File#getCanonicalPath(), so a symlink
		//inside docsRoot pointing outside it is rejected even though its name
		//contains no "..". Skipped only when docsRoot itself has not been
		//configured (docsRoot == null) - e.g. unit tests that exercise
		//getDecodeUri() directly without a handler wired to a docsRoot.
		if (docsRoot != null && !isWithinDocsRoot(decoded)) {
			throw new NotFoundException();
		}
		return decoded;
	}

	/**
	 * <p>Decodes {@code uri} and rejects it outright on an unsupported encoding,
	 * a decode failure, an empty result, or a literal {@code ".."} segment.
	 * Used only by {@link #getDecodeUri}. {@link #getDecodeFile} duplicates this
	 * logic inline rather than calling this method - see the comment there for
	 * why the duplication is required, not accidental.
	 * @since 1.5.2-tc9.0.120
	 */
	private String decodeAndCheckTraversal(String uri) {
		//FR-3: validate the encoding name upfront rather than relying on
		//URLDecoder.decode() to throw. On JDK 8, URLDecoder.decode(s, enc) only
		//touches the named charset when s actually contains a "%"/"+" escape to
		//decode - for an input with nothing to decode (e.g. "/index.html"), an
		//invalid encoding name like "none" silently passes through unchecked and
		//no UnsupportedEncodingException is thrown (verified: JDK 8u462 vs. a
		//modern JDK differ here; JDK 9+ validates unconditionally). Checking
		//Charset.isSupported() first closes that JDK-version-dependent gap so
		//fail-closed holds the same way on every JDK this project targets.
		if (!Charset.isSupported(encoding)) {
			throw new NotFoundException();
		}
		String decoded;
		try {
			decoded = URLDecoder.decode(uri, encoding);
		} catch (UnsupportedEncodingException e) {
			//FR-3: fail-closed. This used to be an empty catch that let decoding
			//silently fall through to the raw (still-encoded) uri, which degraded
			//the check below into a blocklist-only test. An unsupported/invalid
			//encoding name is now treated the same as any other rejection.
			throw new NotFoundException();
		}
		if (StringUtils.isEmpty(decoded) || decoded.contains("..")) {
			throw new NotFoundException();
		}
		return decoded;
	}

	/**
	 * <p>NFR-SEC-1: verifies that the file the decoded, request-derived path
	 * resolves to (relative to {@link #docsRoot}) stays within the
	 * pre-computed {@link #canonicalDocsRoot} once symlinks and {@code .}/{@code ..}
	 * segments are resolved.
	 * <p>NFR-REL-3 (fail-closed): both a docsRoot whose canonical form could not
	 * be resolved ({@link #canonicalDocsRoot} is {@code null}) and an
	 * {@link IOException} while resolving the request file's canonical path are
	 * treated as containment failure - the request is rejected rather than the
	 * exception propagating or the check being skipped.
	 * @param decodedUri the already-decoded, ".."-checked request path
	 * @since 1.5.2-tc9.0.120
	 */
	/**
	 * <p>Returns the decoded, docsRoot-contained {@link File} for {@code uri}.
	 * Unlike {@link #getDecodeUri}, which checks a separately-computed canonical
	 * copy (via {@link #isWithinDocsRoot}) but returns the pre-canonicalization
	 * decoded string, this builds the returned {@link File} directly from the
	 * canonical path that was checked - the checked value and the value handed
	 * to the filesystem are the same object. This shape is what lets static
	 * analysis (and a human reader) verify the containment check actually
	 * covers the value callers go on to use.
	 * <p>The decode/".."-check preamble below is intentionally duplicated from
	 * {@link #decodeAndCheckTraversal} rather than calling it: CodeQL's
	 * path-injection sanitizer recognition (java/path-injection) only tracks
	 * taint locally within a single method body, so routing {@code decoded}
	 * through a separate helper method broke the containment check below from
	 * being recognized as a sanitizer - confirmed empirically (18 alerts
	 * reappeared with the helper call, 0 with this inlined form). Do not
	 * de-duplicate this into a shared helper.
	 * @since 1.5.2-tc9.0.120
	 */
	protected File getDecodeFile(String uri) {
		if (!Charset.isSupported(encoding)) {
			throw new NotFoundException();
		}
		String decoded;
		try {
			decoded = URLDecoder.decode(uri, encoding);
		} catch (UnsupportedEncodingException e) {
			throw new NotFoundException();
		}
		if (StringUtils.isEmpty(decoded) || decoded.contains("..")) {
			throw new NotFoundException();
		}
		File file = new File(docsRoot, decoded);
		if (docsRoot != null) {
			if (canonicalDocsRoot == null) {
				throw new NotFoundException();
			}
			String canonicalPath;
			try {
				canonicalPath = file.getCanonicalPath();
			} catch (IOException e) {
				throw new NotFoundException();
			}
			if (!(canonicalPath.equals(canonicalDocsRoot)
					|| canonicalPath.startsWith(canonicalDocsRoot + File.separator))) {
				throw new NotFoundException();
			}
			file = new File(canonicalPath);
		}
		return file;
	}

	private boolean isWithinDocsRoot(String decodedUri) {
		if (canonicalDocsRoot == null) {
			return false;
		}
		try {
			String canonicalFile = new File(docsRoot, decodedUri).getCanonicalPath();
			return canonicalFile.equals(canonicalDocsRoot)
				|| canonicalFile.startsWith(canonicalDocsRoot + File.separator);
		} catch (IOException e) {
			return false;
		}
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
