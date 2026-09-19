/*
 * Copyright (c) 2026 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.util;

import java.io.IOException;
import java.net.Socket;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.tamacat.httpcore4.protocol.HttpContext;

/**
 * <p>Minimal SSL socket factory used by {@link ReverseUtils} to connect to
 * reverse-proxy backend (target) servers over HTTPS.
 * <p>Reimplemented on the JDK only, replacing the previously used
 * httpclient-specific {@code org.apache.http.conn.ssl.SSLConnectionSocketFactory}
 * / {@code NoopHostnameVerifier} now that the httpclient dependency has been
 * removed from this project (v1.6 EOL-OSS removal).
 * <p><b>Security-relevant behavior preserved exactly</b> (NFR-SEC-4): when
 * {@code verifyHostname} is {@code true} (strictHttps), the JDK's built-in
 * HTTPS endpoint identification (RFC 2818 hostname verification,
 * {@link SSLParameters#setEndpointIdentificationAlgorithm(String)}) is
 * enabled on the handshake — equivalent to httpclient's
 * {@code DefaultHostnameVerifier}. When {@code false}, hostname verification
 * is skipped — equivalent to {@code NoopHostnameVerifier.INSTANCE}. Either
 * way, certificate chain trust is controlled separately by the
 * {@code SSLContext}'s own {@code TrustManager} (see
 * {@code ReverseUtils#getSSLContext}), unchanged by this class.
 */
public class SSLLayeredSocketFactory {

	private final SSLContext sslContext;
	private final boolean verifyHostname;

	public SSLLayeredSocketFactory(SSLContext sslContext) {
		this(sslContext, true);
	}

	public SSLLayeredSocketFactory(SSLContext sslContext, boolean verifyHostname) {
		this.sslContext = sslContext;
		this.verifyHostname = verifyHostname;
	}

	/**
	 * <p>Creates a new unconnected plain socket (mirrors
	 * {@code ConnectionSocketFactory#createSocket(HttpContext)}'s contract:
	 * an unconnected {@link Socket}, later connected then layered by
	 * {@link #createLayeredSocket}).
	 */
	public Socket createSocket(HttpContext context) throws IOException {
		return new Socket();
	}

	/**
	 * <p>Layers TLS over an already-connected plain socket and completes the
	 * handshake before returning.
	 * @param socket a connected plain socket to the target host/port.
	 * @param target the target hostname, used for hostname verification when strict.
	 * @param port the target port.
	 * @param context unused; kept for call-site parity with the previous
	 *     httpclient-based factory.
	 */
	public Socket createLayeredSocket(Socket socket, String target, int port, HttpContext context) throws IOException {
		SSLSocketFactory factory = sslContext.getSocketFactory();
		SSLSocket sslSocket = (SSLSocket) factory.createSocket(socket, target, port, true);
		if (verifyHostname) {
			SSLParameters params = sslSocket.getSSLParameters();
			params.setEndpointIdentificationAlgorithm("HTTPS");
			sslSocket.setSSLParameters(params);
		}
		sslSocket.startHandshake();
		return sslSocket;
	}
}
