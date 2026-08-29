/*
 * Copyright 2009 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.security.Principal;
import java.util.Base64;
import java.util.Set;

import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpEntityContainer;
import org.apache.hc.core5.http.HttpMessage;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.io.entity.BufferedHttpEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.tamacat.httpd.config.ServiceUrl;
import org.tamacat.httpd.core.BasicHttpStatus;
import org.tamacat.httpd.core.RequestParameters;
import org.tamacat.httpd.core.ServerHttpConnection;
import org.tamacat.httpd.exception.BadRequestException;
import org.tamacat.httpd.exception.HttpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tamacat.httpd.core.util.StringUtils;

public class RequestUtils {
	
	private static final Logger LOG = LoggerFactory.getLogger(RequestUtils.class);
	
	static final String HTTP_REQUEST_PARAMETERS = "http.request.parameters";

	static final String TLS_CLIENT_AUTH_PRINCIPAL_CONTEXT_KEY = "SSLSession.getPeerPrincipal";

	public static final String X_FORWARDED_FOR = "X-Forwarded-For";
	public static final String REMOTE_ADDRESS = "remote_address";

	static final String CONTENT_TYPE_FORM_URLENCODED = "application/x-www-form-urlencoded";

	/**
	 * <p>Returns the protocol version of the message.
	 *
	 * <p>httpcore 4.4's {@code HttpMessage#getProtocolVersion()} is
	 * {@code HttpMessage#getVersion()} in HttpComponents Core 5.x, and unlike the 4.4
	 * accessor it may return {@code null} when no version was set on the message.
	 * {@code HTTP/1.1} is substituted in that case, which is the same default core5
	 * itself applies in {@code org.apache.hc.core5.http.message.RequestLine}.
	 *
	 * @param message
	 * @return the protocol version, never {@code null}.
	 * @since 2.0
	 */
	public static ProtocolVersion getVersion(HttpMessage message) {
		ProtocolVersion version = message != null ? message.getVersion() : null;
		return version != null ? version : HttpVersion.HTTP_1_1;
	}

	public static String getRequestLine(ClassicHttpRequest request) {
		return request.getMethod() + " "
			+ request.getRequestUri() + " "
			+ getVersion(request);
	}

	/**
	 * Get request absolute URI to Path (With Query)
	 * @param uri
	 */
	public static String getRequestPathWithQuery(final String uri) {
		try {
			if (uri.indexOf("http")==0 && uri.indexOf("://")>0) {
				int idx = uri.indexOf("://");
				String path = uri.substring(idx+3, uri.length());
				if (path.indexOf("/")>0) {
					return path.substring(path.indexOf("/"), path.length());
				}
			}
		} catch (RuntimeException e) {
			LOG.warn(e.getMessage());
		}
		return uri;
	}

	public static String getPath(String uri) {
		int index = uri.indexOf('?');
		if (index != -1) {
			uri = uri.substring(0, index);
		} else {
			index = uri.indexOf('#');
			if (index != -1) {
				uri = uri.substring(0, index);
			}
		}
		return uri;
	}
	
	public static String getPath(ClassicHttpRequest request) {
		return getPath(request.getRequestUri());
	}

	public static RequestParameters parseParameters(ClassicHttpRequest request, HttpContext context, String encoding) {
		synchronized (context) {
			RequestParameters parameters = (RequestParameters) context.getAttribute(HTTP_REQUEST_PARAMETERS);
			if (parameters == null) {
				try {
					parameters = parseParameters(request, encoding);
					context.setAttribute(HTTP_REQUEST_PARAMETERS, parameters);
				} catch (BadRequestException e) {
					throw e;
				} catch (Exception e) {
					throw new BadRequestException(e);
				}
			}
			return parameters;
		}
	}
	
	public static RequestParameters parseParameters(ClassicHttpRequest request, String encoding) {
		RequestParameters parameters = new RequestParameters();
		String path = request.getRequestUri();
		if (path.indexOf('?') >= 0) {
			String[] requestParams = StringUtils.split(path, "?");
			//set request parameters for Custom ClassicHttpRequest.
			if (requestParams.length >= 2) {
				String params = requestParams[1];
				String[] param = StringUtils.split(params, "&");
				for (String kv : param) {
					String[] p = StringUtils.split(kv, "=");
					if (p.length >=2) {
						try {
							parameters.setParameter(p[0], URLDecoder.decode(p[1], encoding));
						} catch (Exception e) {
						}
					} else if (p.length == 1) {
						parameters.setParameter(p[0], "");
					}
				}
			}
		}
		if (isEntityEnclosingRequest(request) && RequestUtils.isFormUrlEncoded(request)) {
			HttpEntity entity = getEntity(request);
			if (entity != null) {
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(entity.getContent()))) {
					String s;
					StringBuilder sb = new StringBuilder();
					while ((s = reader.readLine()) != null) {
						sb.append(s);
					}
					String requestBody = sb.toString();
					//for Reuse handler.
					//httpcore 4.4's StringEntity(String, String charset) produced
					//"text/plain; charset=<encoding>". core5 has no such constructor, so
					//the same content type is rebuilt explicitly to keep the re-set entity
					//equivalent to 1.5.
					request.setEntity(new StringEntity(requestBody,
						ContentType.create(ContentType.TEXT_PLAIN.getMimeType(), encoding)));
					
					String[] params = StringUtils.split(requestBody, "&");
					for (String param : params) {
						String[] keyValue = StringUtils.split(param, "=");
						if (keyValue.length >= 2) {
							try {
								parameters.setParameter(keyValue[0],
									URLDecoder.decode(keyValue[1], encoding));
							} catch (Exception e) {
							}
						} else if (keyValue.length==1) {
							parameters.setParameter(keyValue[0], "");
						}
					}
				} catch (IOException e) {
					throw new BadRequestException(e);
				}
			}
		}
		return parameters;
	}
	
	public static void setParameter(HttpContext context, String name, String... values) {
		RequestParameters parameters = getParameters(context);
		parameters.setParameter(name, values);
	}

	public static void setParameters(HttpContext context, RequestParameters parameters) {
		context.setAttribute(HTTP_REQUEST_PARAMETERS, parameters);
	}

	/**
	 * Get Request parameters
	 * <p>The parsing body was formerly the deprecated
	 * {@code setParameters(ClassicHttpRequest, HttpContext, String)}, removed as
	 * part of A-1/FR-5 (its only caller was this method); the logic is unchanged,
	 * only inlined.
	 * @since 1.4
	 */
	public static RequestParameters getParameters(ClassicHttpRequest request, HttpContext context, String encoding) {
		if (context.getAttribute(HTTP_REQUEST_PARAMETERS) == null) {
			String path = request.getRequestUri();
			//String path = docsRoot + request.getRequestUri();
			RequestParameters parameters = getParameters(context);

			if (path.indexOf('?') >= 0) {
				String[] requestParams = StringUtils.split(path, "?");
				//path = requestParams[0];
				//set request parameters for Custom ClassicHttpRequest.
				if (requestParams.length >= 2) {
					String params = requestParams[1];
					String[] param = StringUtils.split(params, "&");
					for (String kv : param) {
						String[] p = StringUtils.split(kv, "=");
						if (p.length >=2) {
							try {
								parameters.setParameter(p[0], URLDecoder.decode(p[1], encoding));
							} catch (Exception e) {
							}
						} else if (p.length == 1){
							parameters.setParameter(p[0], "");
						}
					}
				}
			}
			if (isEntityEnclosingRequest(request) && ! RequestUtils.isMultipart(request)) {
				HttpEntity entity = getEntity(request);
				if (entity != null) {
					try (@SuppressWarnings("resource")
					BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedHttpEntity(entity).getContent()))) {
						String s;
						StringBuilder sb = new StringBuilder();
						while ((s = reader.readLine()) != null) {
							sb.append(s);
						}
						String[] params = StringUtils.split(sb.toString(), "&");
						for (String param : params) {
							String[] keyValue = StringUtils.split(param, "=");
							if (keyValue.length >= 2) {
								try {
									parameters.setParameter(keyValue[0],
										URLDecoder.decode(keyValue[1], encoding));
								} catch (Exception e) {
								}
							} else if (keyValue.length == 1) {
								parameters.setParameter(keyValue[0], "");
							}
						}
					} catch (IOException e) {
						throw new HttpException(BasicHttpStatus.SC_BAD_REQUEST, e);
					}
				}
			}
		}
		return getParameters(context);
	}
	
	public static RequestParameters getParameters(HttpContext context) {
		return (RequestParameters) context.getAttribute(HTTP_REQUEST_PARAMETERS);
	}

	public static String getParameter(HttpContext context, String name) {
		RequestParameters params = getParameters(context);
		return params != null ? params.getParameter(name) : null;
	}

	public static String[] getParameters(HttpContext context, String name) {
		RequestParameters params = getParameters(context);
		return params != null ? params.getParameters(name) : null;
	}

	public static Set<String> getParameterNames(HttpContext context) {
		RequestParameters params = getParameters(context);
		return params != null ? params.getParameterNames() : null;
	}

	/**
	 * Get the connection endpoint details that HttpComponents Core 5.x publishes
	 * into the {@code HttpContext}.
	 *
	 * <p>{@code org.apache.hc.core5.http.impl.io.HttpService#handleRequest} calls
	 * {@code HttpCoreContext#setEndpointDetails(...)} for every exchange, so there is
	 * no longer a {@code setRemoteAddress(context, conn)} step to perform. This is the
	 * replacement for {@code org.apache.http.HttpInetConnection}, which core5 removed.
	 *
	 * @param context
	 * @return the endpoint details, or {@code null} when they are not available.
	 * @since 2.0
	 */
	public static EndpointDetails getEndpointDetails(HttpContext context) {
		if (context == null) {
			return null;
		}
		HttpCoreContext coreContext = HttpCoreContext.cast(context);
		return coreContext != null ? coreContext.getEndpointDetails() : null;
	}

	/**
	 * Get the remote {@link InetAddress} of the current exchange.
	 *
	 * <p>The {@link #REMOTE_ADDRESS} context attribute wins when it is set, so that a
	 * caller (or a test) can override the address. Otherwise the address is read from
	 * the core5 {@code EndpointDetails}.
	 *
	 * @param context
	 * @return the remote address, or {@code null} when it is not available.
	 * @since 2.0
	 */
	public static InetAddress getRemoteAddress(HttpContext context) {
		if (context == null) {
			return null;
		}
		Object attribute = context.getAttribute(REMOTE_ADDRESS);
		if (attribute instanceof InetAddress) {
			return (InetAddress) attribute;
		}
		EndpointDetails endpoint = getEndpointDetails(context);
		SocketAddress remote = endpoint != null ? endpoint.getRemoteAddress() : null;
		if (remote instanceof InetSocketAddress) {
			return ((InetSocketAddress) remote).getAddress();
		}
		return null;
	}

	/**
	 * Get the remote IP address in {@code HttpContext} or X-Forwarded-For.
	 * @param request
	 * @param context
	 * @param useXFF Using X-Forwarded-For request header.
	 * @return
	 */
	public static String getRemoteIPAddress(ClassicHttpRequest request, HttpContext context, boolean useXFF) {
		return getRemoteIPAddress(request, context, useXFF, X_FORWARDED_FOR);
	}
	
	/**
	 * Get the remote IP address in {@code HttpContext} or X-Forwarded-For.
	 * @param request
	 * @param context
	 * @param useForwardHeader Using X-Forwarded-For request header.
	 * @param forwardHeader ("X-Forwarded-For")
	 */
	public static String getRemoteIPAddress(ClassicHttpRequest request, HttpContext context, boolean useForwardHeader, String forwardHeader) {
		String ip = null;
		if (useForwardHeader) {
			ip = getForwardedForLastValue(request, forwardHeader);
		}
		if (StringUtils.isEmpty(ip)) {
			ip = getRemoteIPAddress(context);
		}
		return ip != null ? ip : "";
	}
	
	/**
	 * Get a X-ForwardedFor value. (original)
	 * @param request
	 * @param forwardHeader
	 * @since 1.5-20230629
	 */
	public static String getForwardedForValue(ClassicHttpRequest request, String forwardHeader) {
		return HeaderUtils.getHeader(request, StringUtils.isNotEmpty(forwardHeader)? forwardHeader : X_FORWARDED_FOR);
	}

	/**
	 * Get a X-ForwardedFor first value.
	 * @param request
	 * @param forwardHeader
	 * @since 1.5-20230629
	 */
	public static String getForwardedForFirstValue(ClassicHttpRequest request, String forwardHeader) {
		String value = getForwardedForValue(request, forwardHeader);
		if (StringUtils.isNotEmpty(value)) {
			String[] address = StringUtils.split(value, ",");
			if (address.length >= 1) {
				return address[0];
			}
		}
		return value;
	}
	
	/**
	 * Get a X-ForwardedFor last value.
	 * @param request
	 * @param forwardHeader
	 * @since 1.5-20230629
	 */
	public static String getForwardedForLastValue(ClassicHttpRequest request, String forwardHeader) {
		String value = getForwardedForValue(request, forwardHeader);
		if (StringUtils.isNotEmpty(value)) {
			String[] address = StringUtils.split(value, ",");
			if (address.length >= 1) {
				return address[address.length -1];
			}
		}
		return value;
	}
	
	/**
	 * Get the remote IP address in {@code HttpContext}.
	 * @param context
	 * @return
	 */
	public static String getRemoteIPAddress(HttpContext context) {
		InetAddress address = getRemoteAddress(context);
		if (address != null) return address.getHostAddress();
		else return "";
	}

	public static boolean isRemoteIPv6Address(HttpContext context) {
		InetAddress address = getRemoteAddress(context);
		if (address != null && address instanceof Inet6Address) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Get hostname from Host request header.
	 *
	 * <p>This method keeps the plain {@link HttpRequest} parameter rather than
	 * following R-6 to {@code ClassicHttpRequest}: it is called from
	 * {@code HostRequestHandlerMapper#lookup}, and core5's
	 * {@code HttpRequestMapper#resolve(HttpRequest, HttpContext)} contract hands the
	 * routing layer a plain {@code HttpRequest}. Widening the parameter keeps every
	 * {@code ClassicHttpRequest} caller source-compatible, and the body only reads a
	 * header, which {@code HttpMessage} already provides.
	 *
	 * @param request
	 * @param context
	 */
	public static String getRequestHost(HttpRequest request, HttpContext context) {
		Header hostHeader = request.getFirstHeader(HttpHeaders.HOST);
		if (hostHeader != null) {
			String hostName = hostHeader.getValue();
			if (hostName != null && hostName.indexOf(':') >= 0) {
				String[] hostAndPort = StringUtils.split(hostName, ":");
				if (hostAndPort.length >= 2) {
					hostName = hostAndPort[0];
				}
			}
			return hostName;
		}
		return null;
	}

	public static String getRequestHostURL(
			ClassicHttpRequest request, HttpContext context, ServiceUrl url) {
		URL host = getRequestURL(request, context, url);
		return host != null ? host.getProtocol()
				+ "://" + host.getAuthority() : null;
	}

	public static URL getRequestURL(ClassicHttpRequest request, HttpContext context) {
		return getRequestURL(request, context, null);
	}

	public static URL getRequestURL(ClassicHttpRequest request, HttpContext context, ServiceUrl url) {
		String protocol = "http";
		String hostName = null;
		int port = -1;
		Header hostHeader = request.getFirstHeader(HttpHeaders.HOST);
		if (hostHeader != null) {
			hostName = hostHeader.getValue();
			if (hostName != null && hostName.indexOf(':') >= 0) {
				String[] hostAndPort = StringUtils.split(hostName, ":");
				if (hostAndPort.length >= 2) {
					hostName = hostAndPort[0];
					port = StringUtils.parse(hostAndPort[1],-1);
				}
			}
		}
		if (url != null) {
			URL configureHost = url.getHost();
			if (configureHost != null) {
				protocol = configureHost.getProtocol();
				if (hostName == null) {
					hostName = configureHost.getHost();
				}
			}
			if (url.getServerConfig().useHttps()) {
				protocol = "https";
			}
			if (hostName != null && hostName.indexOf(':') >= 0) {
				String[] hostAndPort = StringUtils.split(hostName, ":");
				if (hostAndPort.length >= 2) {
					hostName = hostAndPort[0];
					port = StringUtils.parse(hostAndPort[1],-1);
				}
			} else {
				port = url.getServerConfig().getPort();
			}
			if (context != null) {
				EndpointDetails endpoint = getEndpointDetails(context);
				SocketAddress local = endpoint != null ? endpoint.getLocalAddress() : null;
				if (local instanceof InetSocketAddress) {
					port = ((InetSocketAddress) local).getPort();
					InetAddress addr = ((InetSocketAddress) local).getAddress();
					if (hostName == null && addr != null) {
						hostName = addr.getHostName();
					}
				}
			}
		}
		if (("http".equalsIgnoreCase(protocol) && port == 80)
			|| ("https".equalsIgnoreCase(protocol) && port == 443)){
			port = -1;
		}
		if (hostName != null) {
			try {
				return new URI(protocol + "://" + authority(hostName, port)
					+ request.getRequestUri()).toURL();
			} catch (URISyntaxException | MalformedURLException e) {
			}
		}
		return null;
	}

	/**
	 * <p>Builds an authority component ({@code host} or {@code host:port}) for
	 * {@link URI} construction, reproducing the port-{@code -1}-means-default-port
	 * normalization that {@link URL}'s 4-argument constructor performed internally.
	 * @param host
	 * @param port
	 */
	private static String authority(String host, int port) {
		return port == -1 ? host : host + ":" + port;
	}

	/**
	 * UnsupportedEncodingException -> value returns.
	 * @param value
	 * @param encoding
	 * @return
	 */
	static String decode(String value, String encoding) {
		String decode = null;
		try {
			decode = URLDecoder.decode(value, encoding);
		} catch (UnsupportedEncodingException e) {
			decode = value;
		}
		return decode;
	}

	/**
	 * <p>Returns {@code true} when the request can carry an entity.
	 * <p>HttpComponents Core 5.x has no {@code HttpEntityEnclosingRequest}: the entity
	 * accessors live on {@link HttpEntityContainer}, which {@code ClassicHttpRequest}
	 * extends. A {@code ClassicHttpRequest} therefore always <em>can</em> carry an
	 * entity; whether it actually does is {@code getEntity(request) != null}.
	 */
	public static boolean isEntityEnclosingRequest(ClassicHttpRequest request) {
		return request != null;
	}

	public static HttpEntity getEntity(ClassicHttpRequest request) {
		return request != null ? request.getEntity() : null;
	}

	public static InputStream getInputStream(ClassicHttpRequest request) throws IOException {
		HttpEntity entity = getEntity(request);
		return entity != null? entity.getContent() : null;
	}

	public static boolean isFormUrlEncoded(ClassicHttpRequest request) {
		return HeaderUtils.isFormUrlEncoded(
				HeaderUtils.getHeader(request, HttpHeaders.CONTENT_TYPE));
	}
	
	public static boolean isMultipart(ClassicHttpRequest request) {
		if ("post".equalsIgnoreCase(request.getMethod())) {
			return HeaderUtils.isMultipart(
				HeaderUtils.getHeader(request, HttpHeaders.CONTENT_TYPE));
		}
		return false;
	}

	public static String getPathPrefix(ClassicHttpRequest request) {
		String path = request.getRequestUri();
		int idx = path.lastIndexOf("/");
		if (idx >=0) {
			return path.substring(0, idx) + "/";
		}
		return path;
	}

	/**
	 * Get the current {@link HttpRequest} from the {@link HttpContext}.
	 *
	 * <p>{@code org.apache.hc.core5.http.impl.io.HttpService#handleRequest} calls
	 * {@code HttpCoreContext#setRequest(request)} for every exchange, so the request is
	 * read through {@code HttpCoreContext} rather than through the raw context attribute.
	 * {@code HttpCoreContext.cast} wraps a plain context in a delegate that stores the
	 * request under the same attribute key, so this works for both context flavours.
	 *
	 * <p>The return type stays the plain {@code HttpRequest} that
	 * {@code HttpCoreContext#getRequest()} declares. Callers that need the entity must
	 * check for {@code ClassicHttpRequest} themselves.
	 *
	 * @param context
	 * @since 1.1
	 */
	public static HttpRequest getHttpRequest(HttpContext context) {
		if (context == null) {
			return null;
		}
		HttpCoreContext coreContext = HttpCoreContext.cast(context);
		return coreContext != null ? coreContext.getRequest() : null;
	}
	
	/**
	 * Mutual-TLS Client Principal Name (Common Name)
	 * ex) CN=test@example.com
	 * @param conn
	 * @since 1.5
	 */
	public static String getTlsClientAuthPrincipal(ServerHttpConnection conn) {
		try {
			if (conn.getSocket() instanceof SSLSocket) {
				SSLSocket socket = (SSLSocket) conn.getSocket();
				if (socket.getNeedClientAuth()) {
					SSLSession session = socket.getSession();
					if (session != null) {
						Principal principal = session.getPeerPrincipal();
						if (principal != null) {
							LOG.debug(Base64.getUrlEncoder().encodeToString(session.getId()));
							return principal.getName();
						}
					}
				}
			}
		} catch (Exception e) {
			// ignore
		}
		return null;
	}
	
	/**
	 * Mutual-TLS Client Principal Name (Common Name)
	 * ex) CN=test@example.com
	 * @param context
	 * @since 1.5
	 */
	public static String getTlsClientAuthPrincipal(HttpContext context) {
		return (String) context.getAttribute(TLS_CLIENT_AUTH_PRINCIPAL_CONTEXT_KEY);
	}
	
	static final String HTTP_IN_CONN = org.tamacat.httpd.core.HttpContextKeys.HTTP_IN_CONN;
	
	public static ServerHttpConnection getServerHttpConnection(HttpContext context) {
		return (ServerHttpConnection) context.getAttribute(HTTP_IN_CONN);
	}
	
	public static void setTlsClientAuthPrincipal(HttpContext context) {
		setTlsClientAuthPrincipal(getServerHttpConnection(context), context);
	}
	
	/**
	 * Setter method for Mutual-TLS Client Principal Name (Common Name)
	 * @param connection instanceof ServerHttpConnection
	 * @param contex
	 * @since 1.5
	 */
	public static void setTlsClientAuthPrincipal(ServerHttpConnection conn, HttpContext context) {
		try {
			if (conn.getSocket() instanceof SSLSocket) {
				SSLSocket socket = (SSLSocket) conn.getSocket();
				if (socket.getNeedClientAuth()) {
					SSLSession session = socket.getSession();
					if (session != null) {
						context.setAttribute("javax.net.ssl.SSLSession#getId", Base64.getUrlEncoder().encodeToString(session.getId()));
						context.setAttribute("javax.security.cert.X509Certificate[]", session.getPeerCertificates());
						context.setAttribute("javax.net.ssl.cert.SSLSession#getCipherSuite", session.getCipherSuite());
						
						Principal principal = session.getPeerPrincipal();
						if (principal != null) {
							context.setAttribute("javax.net.ssl.cert.SSLSession#getPeerPrincipal", principal);
							context.setAttribute(TLS_CLIENT_AUTH_PRINCIPAL_CONTEXT_KEY, principal.getName());
						}
					}
				}
			}
		} catch (Exception e) {
			// ignore
		}
	}
}
