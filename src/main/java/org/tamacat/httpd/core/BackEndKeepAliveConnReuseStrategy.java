package org.tamacat.httpd.core;

import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Timeout;
import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.log.Log;
import org.tamacat.log.LogFactory;

public class BackEndKeepAliveConnReuseStrategy extends KeepAliveConnReuseStrategy {
	static final Log LOG = LogFactory.getLog(BackEndKeepAliveConnReuseStrategy.class);
	
	/** @deprecated since 2.0, use {@link HttpContextKeys#HTTP_OUT_CONN}. */
	@Deprecated
	protected static final String HTTP_OUT_CONN = HttpContextKeys.HTTP_OUT_CONN;

	public BackEndKeepAliveConnReuseStrategy() {}
	
	public BackEndKeepAliveConnReuseStrategy(ServerConfig serverConfig) {
		super(serverConfig);
		disabledKeepAlive = !("true".equalsIgnoreCase(serverConfig.getParam("BackEndKeepAlive", "true")));
		setKeepAliveTimeout(serverConfig.getParam("BackEndKeepAliveTimeout", keepAliveTimeout));
		setMaxKeepAliveRequests(serverConfig.getParam("BackEndMaxKeepAliveRequests", maxKeepAliveRequests));
	}
	
	@Override
	protected void debug(String message) {
		LOG.debug(message);
	}
	
	@Override
	protected boolean isKeepAliveTimeout(HttpContext context) {
		boolean timeout = false;
		Object value = context.getAttribute(HTTP_OUT_CONN);
		if (value != null && value instanceof ClientHttpConnection) {
			@SuppressWarnings("resource")
			ClientHttpConnection conn = (ClientHttpConnection) value;
			long lastAccessInterval = System.currentTimeMillis() - conn.getLastAccessTime();
			if (lastAccessInterval > keepAliveTimeout) { //timeout
				conn.setSocketTimeout(Timeout.ofMilliseconds(1));
				debug("backend keep-alive timeout[" + lastAccessInterval + " > " + keepAliveTimeout + " msec.] - " + conn);
				timeout = true;
			} else if (maxKeepAliveRequests >= 0 && maxKeepAliveRequests <= getRequestCount(conn)) {
				conn.setSocketTimeout(Timeout.ofMilliseconds(1));
				debug("backend keep-alive max requests:" + maxKeepAliveRequests + " - " + conn);
				timeout = true;
			} else {
				conn.setSocketTimeout(Timeout.ofMilliseconds(keepAliveTimeout));
			}
		}
		return timeout;
	}
}
