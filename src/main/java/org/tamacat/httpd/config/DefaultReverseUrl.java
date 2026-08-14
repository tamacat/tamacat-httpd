/*
 * Copyright (c) 2009, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.config;

import java.net.InetSocketAddress;
import java.net.MalformedURLException;
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
			serviceUrl.setHost(new URL(host.getProtocol(), host.getHost(), host.getPort(), ""));
		} catch (Exception e) {
			// none
		}
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
				return new URL(reverseUrl.getProtocol(), reverseUrl.getHost(), port, distUrl);
			} catch (MalformedURLException e) {
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
