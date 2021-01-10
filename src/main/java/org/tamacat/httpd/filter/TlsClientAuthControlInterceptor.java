/*
 * Copyright 2020 tamacat.org
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package org.tamacat.httpd.filter;

import java.io.IOException;
import java.security.Principal;

import javax.security.cert.X509Certificate;

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.protocol.HttpContext;
import org.tamacat.httpd.util.RequestUtils;
import org.tamacat.log.Log;
import org.tamacat.log.LogFactory;
import org.tamacat.util.StringUtils;

/**
 * <p>TlsClientAuthControlInterceptor is HttpRequestInterceptor for Mutual-TLS.
 * This class is used to convey user information during TLS authentication to 
 * the backend web server using request headers.</p>
 * 
 * <p>Default request header name: X-ARR-ClientCert</p>
 * Settings: <br />
 * ex) httpd.xml
 * <pre>
&lt;beans&gt;
  &lt;bean id="tls-client-auth" class="org.tamacat.httpd.filter.TlsClientAuthControlInterceptor" /&gt;
  &lt;bean id="server" class="org.tamacat.httpd.core.HttpEngine"&gt;
    &lt;property name="propertiesName"&gt;
      &lt;value&gt;server.properties&lt;/value&gt;
    &lt;/property&gt;
    &lt;property name="httpInterceptor"&gt;
      &lt;ref bean="tls-client-auth" /&gt;
    &lt;/property&gt;
  &lt;/bean&gt;
&lt;/beans&gt;
</pre>
 */
public class TlsClientAuthControlInterceptor implements HttpRequestInterceptor {

	static final Log LOG = LogFactory.getLog(TlsClientAuthControlInterceptor.class);
	
	String clientCertHeader = "X-ARR-ClientCert";
	
	@Override
	public void process(HttpRequest request, HttpContext context) throws HttpException, IOException {
		RequestUtils.setTlsClientAuthPrincipal(context);

		Principal principal = (Principal) context.getAttribute("javax.net.ssl.cert.SSLSession#getPeerPrincipal");

		request.removeHeaders(clientCertHeader);
		
		if (principal != null && StringUtils.isNotEmpty(principal.getName())) {
			request.setHeader(clientCertHeader, principal.getName());

			LOG.debug("TLS principal name: "+principal.getName());
			if (LOG.isTraceEnabled()) {
				LOG.trace("TLS id: "+context.getAttribute("javax.net.ssl.SSLSession#getId"));
				LOG.trace("TLS principal hashcode: "+principal.hashCode());
				LOG.trace("TLS CipherSuite: " + context.getAttribute("javax.net.ssl.cert.SSLSession#getCipherSuite"));
				
				X509Certificate[] x509 = (X509Certificate[]) context.getAttribute("javax.security.cert.X509Certificate[]");
				if (x509 != null) {
					for (X509Certificate c : x509) {
						LOG.trace("x509 IssuerDN: " + c.getIssuerDN());
						LOG.trace("x509 SigAlgName: " + c.getSigAlgName());;
						LOG.trace("x509 SigAlgOID: " + c.getSigAlgOID());
						LOG.trace("x509 Version: " + c.getVersion());
						LOG.trace("x509 SubjectDN: " + c.getSubjectDN());
						LOG.trace("x509 SerialNumber: " + c.getSerialNumber());
						LOG.trace("x509 NotBefore: " + c.getNotBefore());
						LOG.trace("x509 NotAfter: " + c.getNotAfter());
					}
				}
			}
		}
	}

	public void setClientCertHeader(String clientCertHeader) {
		this.clientCertHeader = clientCertHeader;
	}
}
