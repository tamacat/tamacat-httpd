package org.tamacat.httpd.config;

import java.io.IOException;
import java.net.Socket;

import org.tamacat.httpcore4.HttpHost;
import org.tamacat.httpd.core.util.RuntimeIOException;
import org.tamacat.httpd.core.util.StringUtils;

public class HttpProxyConfig {

	protected String proxyHost;
	protected int proxyPort;
	protected String username;
	protected String password;
	protected String nonProxyHosts;

	public boolean isDirect() {
		if (StringUtils.isNotEmpty(proxyHost) && proxyPort > 0) {
			return false;
		} else {
			return true;
		}
	}

	// setProxy(HttpClientBuilder) and tunnel(HttpHost) removed in 1.6.0 —
	// httpclient-dependent forward-proxy CONNECT-tunneling support. Accepted
	// breaking change [BR-6, Q7]; see RELEASE_NOTES.txt.

	public HttpHost getProxyHttpHost() {
		return new HttpHost(proxyHost, proxyPort, "http");
	}

	public Socket createProxySocket() {
		try {
			return new Socket(proxyHost, proxyPort);
		} catch (IOException e) {
			throw new RuntimeIOException(e);
		}
	}

	/**
	 * <p>Proxy authentication credentials (username/password holder).
	 * <p>Reshaped in 1.6.0: previously returned httpclient's
	 * {@code org.apache.http.auth.Credentials}. Now returns this class's own
	 * minimal {@link ProxyCredentials} holder since the only remaining caller
	 * (the retained no-arg {@link #setProxy()}) only ever needed the username
	 * and password strings, not an httpclient {@code Credentials} object.
	 */
	public ProxyCredentials getCredentials() {
		if (StringUtils.isNotEmpty(username)) {
			return new ProxyCredentials(username, password);
		} else {
			return new ProxyCredentials("", "");
		}
	}

	/**
	 * <p>Minimal username/password holder for proxy authentication.
	 * Replaces httpclient's {@code org.apache.http.auth.Credentials} /
	 * {@code UsernamePasswordCredentials} (removed in 1.6.0, see BR-6).
	 */
	public static class ProxyCredentials {
		private final String username;
		private final String password;

		public ProxyCredentials(String username, String password) {
			this.username = username;
			this.password = password;
		}

		public String getUsername() {
			return username;
		}

		public String getPassword() {
			return password;
		}
	}

	public void setProxyHost(String proxyHost) {
		this.proxyHost = proxyHost;
	}
	
	public void setProxyPort(int proxyPort) {
		this.proxyPort = proxyPort;
	}
	
	public void setUsername(String username) {
		this.username = username;
	}
	
	public void setPassword(String password) {
		this.password = password;
	}
	
	public String getNonProxyHosts() {
		return nonProxyHosts;
	}

	/**
	 * "localhost|127.0.0.1"
	 * @param nonProxyHosts
	 */
	public void setNonProxyHosts(String nonProxyHosts) {
		this.nonProxyHosts = nonProxyHosts;
	}
	
	public void setProxy() {
		if (isDirect() == false) {
			HttpHost proxy = getProxyHttpHost();
			System.setProperty("http.proxyHost", proxy.getHostName());
			System.setProperty("http.proxyPort", String.valueOf(proxy.getPort()));
			System.setProperty("http.proxyUser", getCredentials().getUsername());
			System.setProperty("http.proxyPassword", getCredentials().getPassword());
			System.setProperty("https.proxyHost", proxy.getHostName());
			System.setProperty("https.proxyPort", String.valueOf(proxy.getPort()));
			System.setProperty("https.proxyUser", getCredentials().getUsername());
			System.setProperty("https.proxyPassword", getCredentials().getPassword());
			
			String nonProxyHosts = getNonProxyHosts();
			if (nonProxyHosts != null) {
				System.setProperty("http.nonProxyHosts", nonProxyHosts);
				System.setProperty("https.nonProxyHosts", nonProxyHosts);
			}
		}
	}
}
