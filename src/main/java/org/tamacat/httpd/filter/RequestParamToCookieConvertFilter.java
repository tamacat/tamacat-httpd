package org.tamacat.httpd.filter;

import java.util.regex.Pattern;

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.protocol.HttpContext;
import org.tamacat.httpd.config.ServiceUrl;
import org.tamacat.httpd.core.RequestParameters;
import org.tamacat.httpd.util.RequestUtils;
import org.tamacat.log.Log;
import org.tamacat.log.LogFactory;
import org.tamacat.util.StringUtils;

/**
 * The filter that sets a cookie when a specific request parameter is sent.
 */
public class RequestParamToCookieConvertFilter implements RequestFilter, ResponseFilter {

	static final Log LOG = LogFactory.getLog(RequestParamToCookieConvertFilter.class);
	static final String CONTEXT_KEY = RequestParamToCookieConvertFilter.class.getName()+"_CONTEXT_KEY";
	
	protected ServiceUrl serviceUrl;
	protected String method;
	
	protected String requestPath;
	protected String requestParamKey;
	protected String cookieKey;
	protected String cookieAttributes = "";
	
	@Override
	public void init(ServiceUrl serviceUrl) {
		this.serviceUrl = serviceUrl;
	}
	
	@Override
	public void doFilter(HttpRequest request, HttpResponse response, HttpContext context) {
		if (StringUtils.isNotEmpty(method) && !request.getRequestLine().getMethod().equalsIgnoreCase(method)) {
			return;
		}
		if (StringUtils.isNotEmpty(requestPath)) {
			if (request.getRequestLine().getUri().contains(requestPath) == false) {
				return;
			}
		}
		if (StringUtils.isNotEmpty(requestParamKey)) {
			RequestParameters params = RequestUtils.parseParameters(request, context, "UTF-8");
			String value = params.getParameter(requestParamKey);
			if (StringUtils.isNotEmpty(value) && validateValue(value)) {
				context.setAttribute(CONTEXT_KEY, value);
			}
		}
	}

	@Override
	public void afterResponse(HttpRequest request, HttpResponse response, HttpContext context) {
		Object value = context.getAttribute(CONTEXT_KEY);
		if (value != null) {
			if (StringUtils.isNotEmpty(cookieAttributes)) {
				response.addHeader("Set-Cookie", cookieKey+"="+value+";"+cookieAttributes);
			} else {
				response.addHeader("Set-Cookie", cookieKey+"="+value);
			}
			context.removeAttribute(CONTEXT_KEY);
		}
	}
	
	static final Pattern VALUE_PATTERN = Pattern.compile("^[a-zA-Z0-9]+$");

	/**
	 * Validate Request parameter value.
	 * @param value default "^[a-zA-Z0-9]+$"
	 */
	protected boolean validateValue(String value) {
		return StringUtils.isNotEmpty(value) && VALUE_PATTERN.matcher(value).matches();
	}
	
	/**
	 * Set a Condition for Request method.
	 * @param method default all methods.
	 */
	public void setMethod(String method) {
		this.method = method;
	}
	
	/**
	 * Set a Condition for Request URL path
	 * @param requestPath default all path.
	 */
	public void setRequestPath(String requestPath) {
		this.requestPath = requestPath;
	}

	/**
	 * Set a Request parameter key. (required)
	 * @param requestParamKey
	 */
	public void setRequestParamKey(String requestParamKey) {
		this.requestParamKey = requestParamKey;
	}

	/**
	 * Set a Set-Cookie key name. (required)
	 * @param cookieKey
	 */
	public void setCookieKey(String cookieKey) {
		this.cookieKey = cookieKey;
	}

	/**
	 * Set a Set-Cookie attribute. ex) Path=/; HttpOnly; SameSite=None; Secure
	 * @param cookieAttributes default empty String ""
	 */
	public void setCookieAttributes(String cookieAttributes) {
		this.cookieAttributes = cookieAttributes;
	}
}
