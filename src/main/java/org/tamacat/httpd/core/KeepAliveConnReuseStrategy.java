/*
 * Copyright (c) 2013 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core;

import java.util.Iterator;

import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
//core5 has no TokenIterator / HeaderIterator interfaces; only the concrete
//BasicTokenIterator / BasicHeaderIterator classes remain (R-4, 15.10).
import org.apache.hc.core5.http.message.BasicTokenIterator;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Timeout;
import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.httpd.util.HeaderUtils;
import org.tamacat.httpd.util.RequestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tamacat.httpd.core.util.StringUtils;

/**
 * The ConnectionReuseStrategy corresponding to keep-alive.
 * <pre>
 * - keepAliveTimeout (default:15000ms)
 * - maxKeepAliveRequests (default:100)
 * </pre>
 * @sinse 1.1
 */
public class KeepAliveConnReuseStrategy extends DefaultConnectionReuseStrategy {
	private static final Logger LOG = LoggerFactory.getLogger(KeepAliveConnReuseStrategy.class);

	protected static final KeepAliveConnReuseStrategy INSTANCE = new KeepAliveConnReuseStrategy();

	protected ServerConfig serverConfig;

	protected boolean disabledKeepAlive;
	protected int keepAliveTimeout = 0;     //15000;
	protected int maxKeepAliveRequests = 0; //100;

	public KeepAliveConnReuseStrategy() {}

	public KeepAliveConnReuseStrategy(ServerConfig serverConfig) {
		this.serverConfig = serverConfig;
		setKeepAliveTimeout(serverConfig.getParam("KeepAliveTimeout", 15000));
		setMaxKeepAliveRequests(serverConfig.getParam("KeepAliveRequests", 100));
	}

	/**
	 * Set the Keep-Alive timeout (millisecond).
	 * (default: 15000 ms)
	 * @param keepAliveTimeout
	 */
	public void setKeepAliveTimeout(int keepAliveTimeout) {
		this.keepAliveTimeout = keepAliveTimeout;
	}

	/**
	 * Set the maximum times of keep-alive requests.
	 * (default: 100 requests)
	 * @param maxKeepAliveRequests
	 */
	public void setMaxKeepAliveRequests(int maxKeepAliveRequests) {
		this.maxKeepAliveRequests = maxKeepAliveRequests;
	}

	/**
	 * Set the true, force disabled Keep-Alive.
	 * (default: false)
	 * @param disabledKeepAlive
	 */
	public void setDisabledKeepAlive(boolean disabledKeepAlive) {
		this.disabledKeepAlive = disabledKeepAlive;
	}

	/**
	 * <pre>
	 * 1) disabledKeepAlive:true -> return false.
	 * 2) return keepAliveCheck(response, context)
	 * </pre>
	 * <p>core5's {@code ConnectionReuseStrategy} passes the request as well
	 * ({@code keepAlive(HttpRequest, HttpResponse, HttpContext)}); 4.4 passed only the
	 * response. The extra argument is accepted and not used, so the decision is
	 * unchanged from 1.5.
	 */
	@Override
	public boolean keepAlive(HttpRequest request, HttpResponse response, HttpContext context) {
		if (disabledKeepAlive) {
			return false;
		} else {
			boolean result = keepAliveCheck(response, context);
			if (result) {
				return !isKeepAliveTimeout(context);
			}
			return false;
		}
	}

	/**
	 * check the Keep-Alive.
	 * @see DefaultConnectionReuseStrategy#keepAlive(HttpRequest, HttpResponse, HttpContext)
	 * @param response
	 * @param context
	 */
	protected boolean keepAliveCheck(HttpResponse response, HttpContext context) {
		Args.notNull(response, "HTTP response");
		Args.notNull(context, "HTTP context");

		// Check for a self-terminating entity. If the end of the entity will
		// be indicated by closing the connection, there is no keep-alive.
		//core5's HttpResponse has no status line object; the version lives on the message
		//and may be null, in which case HTTP/1.1 applies (same default as core5's StatusLine).
		ProtocolVersion ver = RequestUtils.getVersion(response);
		// default since HTTP/1.1 is persistent, before it was non-persistent
		if (ver.greaterEquals(HttpVersion.HTTP_1_1)) {
			//Connection:Close -> keep-Alive:false
			String close = HeaderUtils.getHeader(response, HttpHeaders.CONNECTION);
			if (HeaderElements.CLOSE.equalsIgnoreCase(close)) {
				debug("Keep-Alive:false (Connection:Close)");
				return false;
			} else {
				debug("Keep-Alive:true (" + ver + ")");
				return true;
			}
		} else {
			String te = HeaderUtils.getHeader(response, HttpHeaders.TRANSFER_ENCODING);
			if (StringUtils.isNotEmpty(te)) {
				if (!HeaderElements.CHUNKED_ENCODING.equalsIgnoreCase(te)) {
					debug("Keep-Alive:false (Transfer-Encoding: not chunked)");
					return false;
				}
			} else {
				if (canResponseHaveBody(response)) {
					String cl = HeaderUtils.getHeader(response, HttpHeaders.CONTENT_LENGTH);
					// Do not reuse if not properly content-length delimited
					if (StringUtils.isNotEmpty(cl)) {
						int contentLen = StringUtils.parse(cl, -1);
						if (contentLen < 0) {
							debug("Keep-Alive:false (Content-Length<0 ["+contentLen+"])");
							return false;
						}
					} else {
						debug("Keep-Alive:false (No ontent-Length)");
						return false;
					}
				}
			}

			// Check for the "Connection" header. If that is absent, check for
			// the "Proxy-Connection" header. The latter is an unspecified and
			// broken but unfortunately common extension of HTTP.
			//core5's MessageHeaders#headerIterator returns a plain Iterator<Header>
			//(a BasicHeaderIterator); the 4.4 HeaderIterator interface is gone.
			Iterator<Header> hit = response.headerIterator(HttpHeaders.CONNECTION);
			if (!hit.hasNext()) hit = response.headerIterator(HttpHeaders.PROXY_CONNECTION);
			// Experimental usage of the "Connection" header in HTTP/1.0 is
			// documented in RFC 2068, section 19.7.1. A token "keep-alive" is
			// used to indicate that the connection should be persistent.
			// Note that the final specification of HTTP/1.1 in RFC 2616 does not
			// include this information. Neither is the "Connection" header
			// mentioned in RFC 1945, which informally describes HTTP/1.0.
			//
			// RFC 2616 specifies "close" as the only connection token with a
			// specific meaning: it disables persistent connections.
			//
			// The "Proxy-Connection" header is not formally specified anywhere,
			// but is commonly used to carry one token, "close" or "keep-alive".
			// The "Connection" header, on the other hand, is defined as a
			// sequence of tokens, where each token is a header name, and the
			// token "close" has the above-mentioned additional meaning.
			//
			// To get through this mess, we treat the "Proxy-Connection" header
			// in exactly the same way as the "Connection" header, but only if
			// the latter is missing. We scan the sequence of tokens for both
			// "close" and "keep-alive". As "close" is specified by RFC 2068,
			// it takes precedence and indicates a non-persistent connection.
			// If there is no "close" but a "keep-alive", we take the hint.
			if (hit.hasNext()) {
				//1.5 wrapped this loop in catch(ParseException) -> return false, because
				//4.4's TokenIterator threw ParseException on a malformed Connection header.
				//core5's BasicTokenIterator is lenient: it skips what it cannot tokenize and
				//never throws, so the catch is unreachable (javac rejects it). A malformed
				//header now yields neither "close" nor "keep-alive" and falls through to the
				//default policy below - the same false that the removed catch produced.
				BasicTokenIterator ti = new BasicTokenIterator(hit);
				boolean keepalive = false;
				while (ti.hasNext()) {
					final String token = ti.next();
					if (HeaderElements.CLOSE.equalsIgnoreCase(token)) {
						debug("Keep-Alive:false (Connection:Close)");
						return false;
					} else if (HeaderElements.KEEP_ALIVE.equalsIgnoreCase(token)) {
						// continue the loop, there may be a "close" afterwards
						debug("Keep-Alive:true (Connection:Keep-Alive)");
						keepalive = true;
					}
				}
				if (keepalive) {
					return true;
				}
				// neither "close" nor "keep-alive", use default policy
			}
			debug("Keep-Alive:false (" + ver + ")");
			return false;
		}
	}

	protected void debug(String message) {
		LOG.trace(message);
	}
	
	/**
	 * Check the Keep-Alive timeout.
	 * @param context
	 * @return true -> timeout
	 */
	protected boolean isKeepAliveTimeout(HttpContext context) {
		boolean timeout = false;
		Object value = context.getAttribute(HttpContextKeys.HTTP_IN_CONN);
		if (value != null && value instanceof ServerHttpConnection) {
			@SuppressWarnings("resource")
			ServerHttpConnection conn = (ServerHttpConnection) value;
			long lastAccessInterval = System.currentTimeMillis() - conn.getLastAccessTime();
			if (lastAccessInterval > keepAliveTimeout) { //timeout
				conn.setSocketTimeout(Timeout.ofMilliseconds(1));
				debug("keep-alive timeout[" + lastAccessInterval + " > " + keepAliveTimeout + " msec.] - " + conn);
				timeout = true;
			} else if (maxKeepAliveRequests >= 0 && maxKeepAliveRequests <= getRequestCount(conn)) {
				conn.setSocketTimeout(Timeout.ofMilliseconds(1));
				debug("keep-alive max requests:" + maxKeepAliveRequests + " - " + conn);
				timeout = true;
			} else {
				conn.setSocketTimeout(Timeout.ofMilliseconds(keepAliveTimeout));
			}
		}
		return timeout;
	}

	/**
	 * <p>Returns the number of requests served on the connection.
	 * <p>core5 removed {@code HttpConnection#getMetrics()}; the counter moved to
	 * {@code EndpointDetails}, which is {@code null} until the connection is bound.
	 * {@code -1} is returned in that case, which keeps the caller's
	 * {@code maxKeepAliveRequests <= count} comparison false.
	 */
	protected long getRequestCount(HttpConnection conn) {
		EndpointDetails endpoint = conn != null ? conn.getEndpointDetails() : null;
		return endpoint != null ? endpoint.getRequestCount() : -1;
	}

	protected boolean canResponseHaveBody(final HttpResponse response) {
		int status = response.getCode();
		return status >= HttpStatus.SC_OK
			&& status != HttpStatus.SC_NO_CONTENT
			&& status != HttpStatus.SC_NOT_MODIFIED
			&& status != HttpStatus.SC_RESET_CONTENT;
	}
}
