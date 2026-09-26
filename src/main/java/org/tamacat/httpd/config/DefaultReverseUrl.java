/*
 * Copyright (c) 2009, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.config;

import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import org.apache.hc.core5.http.HttpHost;
import org.tamacat.httpd.core.util.CloneUtils;

/**
 * <p>
 * The default implements of {@link ReverseUrl}.
 */
public class DefaultReverseUrl implements ReverseUrl, Cloneable {

	private ServiceUrl serviceUrl;
	private URL reverseUrl;
	private InetSocketAddress targetAddress;

	/**
	 * <p>
	 * Constructs with the specified {@link ServiceUrl}.
	 *
	 * @param serviceUrl
	 */
	public DefaultReverseUrl(ServiceUrl serviceUrl) {
		this.serviceUrl = serviceUrl;
	}

	@Override
	public ServiceUrl getServiceUrl() {
		return serviceUrl;
	}

	@Override
	public URL getHost() {
		return serviceUrl.getHost();
	}

	@Override
	public void setHost(URL host) {
		try {
			serviceUrl.setHost(new URI(host.getProtocol() + "://" + authority(host.getHost(), host.getPort())).toURL());
		} catch (Exception e) {
			// none
		}
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
	 * <p>Checks that the URL still points at the configured reverse host and port,
	 * with no user-info.
	 * @param url the URL built from the request path
	 * @param port the configured reverse port (the default port when none is set)
	 */
	private boolean isConfiguredAuthority(URL url, int port) {
		return url.getUserInfo() == null
			&& reverseUrl.getHost().equalsIgnoreCase(url.getHost())
			&& port == url.getPort();
	}

	@Override
	public URL getReverse() {
		return reverseUrl;
	}

	@Override
	public URL getReverseUrl(String path) {
		String p = serviceUrl.getPath();
		if (path != null && p != null && path.startsWith(p)) {
			String distUrl = path.replaceFirst(serviceUrl.getPath(), reverseUrl.getPath());
			try {
				int port = reverseUrl.getPort();
				if (port == -1) {
					port = reverseUrl.getDefaultPort();
				}
				URL url = new URI(reverseUrl.getProtocol() + "://" + authority(reverseUrl.getHost(), port) + distUrl).toURL();
				//distUrl is built from the client's request path and is parsed together
				//with the authority (the 4-argument URL constructor kept them apart).
				//When it does not start with "/" (a service path without a trailing "/"
				//and a reverse URL without a path), "@evil.example" would turn the
				//configured host:port into user-info and change the host.
				if (isConfiguredAuthority(url, port)) {
					return url;
				}
			} catch (URISyntaxException | MalformedURLException e) {
			}
		}
		return null;
	}

	@Override
	public InetSocketAddress getTargetAddress() {
		return targetAddress;
	}

	@Override
	public HttpHost getTargetHost() {
		//core5 reordered the arguments: HttpHost(scheme, hostname, port).
		return new HttpHost(reverseUrl.getProtocol(), targetAddress.getHostName(), targetAddress.getPort());
	}


	@Override
	public void setReverse(URL reverseUrl) {
		this.reverseUrl = reverseUrl;
		int port = reverseUrl.getPort();
		if (port == -1)
			port = reverseUrl.getDefaultPort();
		targetAddress = new InetSocketAddress(reverseUrl.getHost(), port);
	}

	@Override
	/**
	 * path: http://localhost:8080/examples/servlet
	 *   =>  http://localhost/examples2/servlet
	 */
	public String getConvertRequestedUrl(String path) {
		URL host = getHost(); // requested URL (path is deleted)
		if (path != null && host != null) {
			return path.replaceFirst(
				reverseUrl.getProtocol() + "://" + reverseUrl.getAuthority(), host.toString())
					.replace(reverseUrl.getPath(), getServiceUrl().getPath());
		} else {
			return path;
		}
	}

	@Override
	public DefaultReverseUrl clone() throws CloneNotSupportedException {
		DefaultReverseUrl clone = (DefaultReverseUrl) super.clone();
		if (this.serviceUrl != null) {
			clone.serviceUrl = CloneUtils.clone(this.serviceUrl);
		}
		return clone;
	}
}
