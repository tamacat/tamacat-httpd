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

public class TlsClientAuthControlInterceptor implements HttpRequestInterceptor {

	static final Log LOG = LogFactory.getLog(TlsClientAuthControlInterceptor.class);
	
	String ClientCertHeader = "X-ARR-ClientCert";
	
	@Override
	public void process(HttpRequest request, HttpContext context) throws HttpException, IOException {
		RequestUtils.setTlsClientAuthPrincipal(context);

		Principal principal = (Principal) context.getAttribute("javax.net.ssl.cert.SSLSession#getPeerPrincipal");

		request.removeHeaders(ClientCertHeader);
		
		if (principal != null && StringUtils.isNotEmpty(principal.getName())) {
			request.setHeader(ClientCertHeader, principal.getName());

			LOG.debug("TLS id: "+context.getAttribute("javax.net.ssl.SSLSession#getId"));
			LOG.debug("TLS principal name: "+principal.getName());
			LOG.debug("TLS principal hashcode: "+principal.hashCode());
			LOG.debug("TLS CipherSuite: " + context.getAttribute("javax.net.ssl.cert.SSLSession#getCipherSuite"));

			X509Certificate[] x509 = (X509Certificate[]) context.getAttribute("javax.security.cert.X509Certificate[]");
			if (x509 != null) {
				for (X509Certificate c : x509) {
					LOG.debug("x509 IssuerDN: " + c.getIssuerDN());
					LOG.debug("x509 SigAlgName: " + c.getSigAlgName());;
					LOG.debug("x509 SigAlgOID: " + c.getSigAlgOID());
					LOG.debug("x509 Version: " + c.getVersion());
					LOG.debug("x509 SubjectDN: " + c.getSubjectDN());
					LOG.debug("x509 SerialNumber: " + c.getSerialNumber());
					LOG.debug("x509 NotBefore: " + c.getNotBefore());
					LOG.debug("x509 NotAfter: " + c.getNotAfter());
				}
			}			
		}
	}
}
