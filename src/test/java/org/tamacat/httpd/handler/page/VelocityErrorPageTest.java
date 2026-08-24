package org.tamacat.httpd.handler.page;

import static org.junit.Assert.*;

import java.io.StringWriter;
import java.util.Properties;

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tamacat.httpd.core.BasicHttpStatus;
import org.tamacat.httpd.exception.HttpException;
import org.tamacat.httpd.exception.NotFoundException;
import org.tamacat.httpd.exception.ServiceUnavailableException;
import org.tamacat.httpd.mock.HttpObjectFactory;
import org.tamacat.util.PropertyUtils;

public class VelocityErrorPageTest {
	private Properties props;

	@Before
	public void setUp() throws Exception {
		props = PropertyUtils.getProperties("velocity.properties",
				getClass().getClassLoader());
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testGetErrorPageHttpRequestHttpResponseHttpException() {
		HttpRequest request = HttpObjectFactory.createHttpRequest("GET", "/test/");
		HttpResponse response = HttpObjectFactory.createHttpResponse(200, "OK");
		VelocityErrorPage page = new VelocityErrorPage(props);
		try {
			HttpException exception = new HttpException(
				BasicHttpStatus.SC_INTERNAL_SERVER_ERROR, "Test Error.");
			String html = page.getErrorPage(request, response, exception);
			assertNotNull(html);
		} catch (Exception e) {
			fail();
		}
	}

	@Test
	public void testGetTemplate() {
		VelocityErrorPage page = new VelocityErrorPage(props);
		try {
			StringWriter writer = new StringWriter();
			Template template = page.getTemplate("error500.vm");

			VelocityContext context = new VelocityContext();
			template.merge(context, writer);
			assertNotNull(writer.toString());
		} catch (Exception e) {
			fail();
		}

		try {
			page.setCharset("UTF-8");
			page.getTemplate("listings.vm");
		} catch (Exception e) {
			fail();
		}
	}

	@Test
	public void testGetPrintErrorPage() {
		VelocityErrorPage template = new VelocityErrorPage(props);
		HttpRequest request = new BasicHttpRequest("GET", "http://localhost/test");
		HttpResponse response = new BasicHttpResponse(
				new BasicStatusLine(new ProtocolVersion("HTTP",1,1), 404, "Not Found"));
		HttpException exception = new NotFoundException();
		String page = template.getErrorPage(request, response, exception);
		assertNotNull(page);
	}

	@Test
	public void testGetDefaultErrorHtml() {
		VelocityErrorPage template = new VelocityErrorPage(props);
		template.getDefaultErrorHtml(new ServiceUnavailableException());
	}

	/**
	 * FR-5 / NFR-SEC-2: error405.vm renders ${method} without Velocity's own
	 * escaping. Deliberately built with a minimal Properties set that does
	 * NOT enable the {@code EscapeHtmlReference} event handler present in the
	 * shared test velocity.properties used by the other tests in this class -
	 * that handler would escape the reference on its own and make the test
	 * pass whether or not VelocityErrorPage.getErrorPage escapes method
	 * itself, which would not actually verify this intent's fix.
	 */
	@Test
	public void testGetErrorPageEscapesXssInMethod() {
		Properties bareProps = new Properties();
		bareProps.setProperty("resource.loaders", "error");
		bareProps.setProperty("runtime.log.logsystem.class",
			"org.apache.velocity.runtime.log.NullLogSystem");

		VelocityErrorPage page = new VelocityErrorPage(bareProps);
		HttpRequest request = HttpObjectFactory.createHttpRequest(
			"<script>alert(1)</script>", "/test/");
		HttpResponse response = HttpObjectFactory.createHttpResponse(200, "OK");
		HttpException exception = new HttpException(
			BasicHttpStatus.SC_METHOD_NOT_ALLOWED, "Method Not Allowed");

		String html = page.getErrorPage(request, response, exception);
		assertNotNull(html);
		assertFalse("the raw <script> tag must not appear in the rendered output",
			html.contains("<script>"));
		assertTrue("the escaped form of the method must still be present",
			html.contains("&lt;SCRIPT&gt;ALERT(1)&lt;/SCRIPT&gt;"));
	}
}
