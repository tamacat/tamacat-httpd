package org.tamacat.httpd.core;

import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Timeout;
import org.tamacat.httpd.config.ServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BackEndKeepAliveConnReuseStrategy extends KeepAliveConnReuseStrategy {
	private static final Logger LOG = LoggerFactory.getLogger(BackEndKeepAliveConnReuseStrategy.class);

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
		Object value = context.getAttribute(HttpContextKeys.HTTP_OUT_CONN);
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
