/*
 * Copyright (c) 2009-2014 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core.ssl;

import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyStore;
import java.security.cert.CRL;
import java.security.cert.CertPathParameters;
import java.security.cert.CertStore;
import java.security.cert.CertificateFactory;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.X509CRL;
import java.security.cert.X509CRLEntry;
import java.security.cert.X509CertSelector;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import javax.net.ssl.CertPathTrustManagerParameters;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.xml.bind.DatatypeConverter;

import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.io.RuntimeIOException;
import org.tamacat.log.Log;
import org.tamacat.log.LogFactory;
import org.tamacat.util.ClassUtils;
import org.tamacat.util.DateUtils;
import org.tamacat.util.StringUtils;

/**
 * <p>
 * The {@link SSLContext} create from {@link ServerConfig} or setter methods.
 */
public class DefaultSSLContextCreator implements SSLContextCreator {

	static final Log LOG = LogFactory.getLog(DefaultSSLContextCreator.class);
	
	protected String keyStoreFile;
	protected char[] keyPassword;

	protected String caKeyStoreFile;
	protected char[] caKeyPassword;
	protected KeyStoreType caKeyStoreType = KeyStoreType.JKS;
	protected String crlFile;

	protected KeyStoreType type = KeyStoreType.JKS;
	protected SSLProtocol protocol = SSLProtocol.TLSv1_2;

	public void setKeyStoreFile(String keyStoreFile) {
		this.keyStoreFile = keyStoreFile;
	}

	public void setKeyPassword(String keyPassword) {
		this.keyPassword = keyPassword.toCharArray();
	}

	public void setCAKeyStoreFile(String caKeyStoreFile) {
		this.caKeyStoreFile = caKeyStoreFile;
	}

	public void setCAKeyPassword(String caKeyPassword) {
		this.caKeyPassword = caKeyPassword.toCharArray();
	}
	
	public void setCrlFile(String crlFile) {
		this.crlFile = crlFile;
	}

	/**
	 * <p>
	 * Default constructor.
	 */
	public DefaultSSLContextCreator() {
	}

	/**
	 * <p>
	 * The constructor of setting values from {@code ServerConfig}.
	 */
	public DefaultSSLContextCreator(ServerConfig serverConfig) {
		setServerConfig(serverConfig);
	}

	/**
	 * @sinse 1.2
	 * @param serverConfig
	 */
	public void setServerConfig(ServerConfig serverConfig) {
		setKeyStoreFile(serverConfig.getParam("https.keyStoreFile", ""));
		setKeyPassword(serverConfig.getParam("https.keyPassword", ""));
		setKeyStoreType(serverConfig.getParam("https.keyStoreType", "JKS"));
		setSSLProtocol(serverConfig.getParam("https.protocol", "TLSv1_2"));
		
		setCAKeyStoreFile(serverConfig.getParam("https.CA.keyStoreFile", ""));
		setCAKeyPassword(serverConfig.getParam("https.CA.keyPassword", ""));
		setCAKeyStoreType(serverConfig.getParam("https.CA.keyStoreType", "JKS"));
		setCrlFile(serverConfig.getParam("https.CRL", ""));
	}

	public void setKeyStoreType(String type) {
		this.type = KeyStoreType.valueOf(type);
	}

	public void setKeyStoreType(KeyStoreType type) {
		this.type = type;
	}

	public void setCAKeyStoreType(String type) {
		this.caKeyStoreType = KeyStoreType.valueOf(type);
	}
	
	public void setSSLProtocol(String protocol) {
		this.protocol = SSLProtocol.valueOf(protocol.replace(".", "_"));
	}

	public void setSSLProtocol(SSLProtocol protocol) {
		this.protocol = protocol;
	}

	public SSLContext getSSLContext() {
		try {
			URL url = getKeyStoreFile();
			KeyStore keystore = KeyStore.getInstance(type.name());
			keystore.load(url.openStream(), keyPassword);
			
			KeyManagerFactory kmfactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			kmfactory.init(keystore, keyPassword);
						
			KeyManager[] keymanagers = kmfactory.getKeyManagers();
			SSLContext sslcontext = SSLContext.getInstance(protocol.getName());
			sslcontext.init(keymanagers, getTrustManager(), null);
			return sslcontext;
		} catch (Exception e) {
			throw new RuntimeIOException(e);
		}
	}
	
	protected TrustManager[] getTrustManager() throws Exception {
		if (StringUtils.isNotEmpty(crlFile)) {
			//CA certs (trustcacerts keystore)
			KeyStore ca = KeyStore.getInstance(caKeyStoreType.name());
			URL caUrl = getCAKeyStoreFile();
			ca.load(caUrl.openStream(), keyPassword);
			TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX", "SunJSSE");
			CertPathParameters pkixParams = new PKIXBuilderParameters(ca, new X509CertSelector());
			((PKIXBuilderParameters) pkixParams).setRevocationEnabled(true);
	
			CertificateFactory factory = CertificateFactory.getInstance("X.509");
			URL crlUrl = getCRLFile();
			X509CRL x509crl = (X509CRL)factory.generateCRL(crlUrl.openStream());
			Collection<CRL> crls = new HashSet<>();
		    crls.add(x509crl);
		    LOG.debug(toStringWithAlgName(x509crl));
		    
		    List<CertStore> certStores =  new ArrayList<>();
			certStores.add(CertStore.getInstance("Collection", new CollectionCertStoreParameters(crls)));
			((PKIXBuilderParameters) pkixParams).setCertStores(certStores);
			
			tmf.init(new CertPathTrustManagerParameters(pkixParams));
			return tmf.getTrustManagers();
		}
		return null;
	}
	
    public String toStringWithAlgName(X509CRL x509crl) {
        StringBuffer sb = new StringBuffer();
        sb.append("X.509 CRL v" + (x509crl.getVersion()) + "\n");
        if (x509crl.getSigAlgOID() != null)
            sb.append("Signature Algorithm: " + x509crl.getSigAlgName() + ", OID=" + (x509crl.getSigAlgOID()) + "\n");
        if (x509crl.getIssuerDN() != null)
            sb.append("Issuer: " + x509crl.getIssuerDN().getName() + "\n");
        if (x509crl.getThisUpdate() != null)
            sb.append("This Update: " + getDateString(x509crl.getThisUpdate()) + "\n");
        if (x509crl.getNextUpdate() != null)
            sb.append("Next Update: " + getDateString(x509crl.getNextUpdate()) + "\n");
        if (x509crl.getRevokedCertificates() == null || x509crl.getRevokedCertificates().isEmpty())
            sb.append("NO certificates have been revoked\n");
        else {
            sb.append("Revoked Certificates: " + x509crl.getRevokedCertificates().size());
            int i = 1;
            for (X509CRLEntry entry: x509crl.getRevokedCertificates()) {
                sb.append("\n[" + i++ + "] Serial: " + DatatypeConverter.printHexBinary(entry.getSerialNumber().toByteArray())
                +", Revocation: "+getDateString(entry.getRevocationDate()));
            }
        }
        return sb.toString();
    }
    
    protected String getDateString(Date date) {
    	return DateUtils.getTime(date, "yyyy-MM-dd HH:mm:ss z", Locale.getDefault(), TimeZone.getDefault());
    }
    
	protected URL getKeyStoreFile() {
		URL caUrl = ClassUtils.getURL(keyStoreFile);
		if (caUrl == null) {
			throw new IllegalArgumentException("https.keyStoreFile ["+keyStoreFile+"] file not found.");
		}
		return caUrl;
	}
	
	protected URL getCAKeyStoreFile() {
		URL caUrl = ClassUtils.getURL(caKeyStoreFile);
		if (caUrl == null) {
			throw new IllegalArgumentException("https.CA.keyStoreFile ["+caKeyStoreFile+"] file not found.");
		}
		return caUrl;
	}
	
	protected URL getCRLFile() {
		if (StringUtils.isEmpty(crlFile)) {
			throw new IllegalArgumentException("https.CRL ["+crlFile+"] file not found.");
		}
		if (crlFile.startsWith("http://") || crlFile.startsWith("https://")) {
			try {
				return new URL(crlFile);
			} catch (MalformedURLException e) {
				throw new IllegalArgumentException("https.CRL ["+crlFile+"] file not found.", e);
			}
		} else {
			URL crlUrl = ClassUtils.getURL(crlFile);
			if (crlUrl == null) {
				throw new IllegalArgumentException("https.CRL ["+crlFile+"] file not found.");
			}
			return crlUrl;
		}
	}
}
