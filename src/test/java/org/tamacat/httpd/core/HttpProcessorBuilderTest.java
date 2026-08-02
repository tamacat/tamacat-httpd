package org.tamacat.httpd.core;

import java.io.IOException;

import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class HttpProcessorBuilderTest {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testAddInterceptorHttpRequestInterceptor() {
		HttpProcessorBuilder builder = new HttpProcessorBuilder();
		builder.addInterceptor(new HttpRequestInterceptor() {
			@Override
			public void process(HttpRequest request, EntityDetails entity, HttpContext context)
					throws HttpException, IOException {				
			}
		});
		builder.build();
	}

	@Test
	public void testAddInterceptorHttpResponseInterceptor() {
		HttpProcessorBuilder builder = new HttpProcessorBuilder();
		builder.addInterceptor(new HttpResponseInterceptor() {
			@Override
			public void process(HttpResponse response, EntityDetails entity, HttpContext context)
					throws HttpException, IOException {
			}
		});
		builder.build();
	}

	@Test
	public void testBuild() {
		HttpProcessorBuilder builder = new HttpProcessorBuilder();
		builder.build();
	}

}
