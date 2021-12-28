package org.tamacat.httpd.util;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.util.PropertyUtils;

public class ReverseUtils_test {

	public static void main(String[] args) throws Exception {
		ServerConfig config = new ServerConfig(PropertyUtils.getProperties("server.properties"));

		//SSLConnectionSocketFactory factory = ReverseUtils.createSSLSocketFactory("TLSv1.2", NoopHostnameVerifier.INSTANCE);
		HttpClientBuilder clientbuilder = HttpClients.custom();
		SSLConnectionSocketFactory factory = ReverseUtils.createSSLSocketFactory(config, false);

		HttpResponse resp = clientbuilder.setSSLSocketFactory(factory).build()
				.execute(new HttpGet("https://localhost/"));
		
		System.out.println(EntityUtils.toString(resp.getEntity()));
	}
}
