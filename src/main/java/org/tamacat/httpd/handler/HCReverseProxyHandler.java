/*
 * Copyright (c) 2015 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.handler;

/**
 * The {@link HttpHandler} for reverse proxy using HttpClient.
 * A proxy and https are being supported in the access course to the backend server from a reverse proxy.
 * 
 * server.roperties (default value)
 * <pre>
 * BackEndBacklogSize=0
 * BackEndSocketBufferSize=8192
 * BackEndSocketTimeout=30000
 * BackEndConnectionTimeout=5000
 * BackEndMaxPerRoute=20
 * BackEndMaxConnectons=100
 * </pre>
 */
@Deprecated
public class HCReverseProxyHandler extends ReverseProxyHandler {

}
