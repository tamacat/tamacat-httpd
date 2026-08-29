package org.tamacat.httpd.handler.page;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.StringWriter;
import java.util.Properties;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tamacat.httpd.mock.HttpObjectFactory;
import org.tamacat.httpd.core.util.ClassUtils;
import org.tamacat.httpd.core.util.PropertyUtils;

public class VelocityListingsPageTest {

	private Properties props;

	@BeforeEach
	public void setUp() throws Exception {
		props = PropertyUtils.getProperties("velocity.properties", getClass().getClassLoader());
	}

	@AfterEach
	public void tearDown() throws Exception {
	}

	@Test
	public void testGetListingsPageHttpRequestHttpResponseFile() {
		ClassicHttpRequest request = HttpObjectFactory.createHttpRequest("GET", "/test/");
		ClassicHttpResponse response = HttpObjectFactory.createHttpResponse(200, "OK");
		VelocityListingsPage page = new VelocityListingsPage(props);
		try {
			File file = new File(ClassUtils.getURL(".", getClass().getClassLoader()).toURI());
			String html = page.getListingsPage(request, response, file);
			assertNotNull(html);
		} catch (Exception e) {
			fail();
		}
	}

	@Test
	public void testGetTemplate() {
		VelocityListingsPage page = new VelocityListingsPage(props);
		try {
			StringWriter writer = new StringWriter();
			Template template = page.getTemplate("listings.vm");

			VelocityContext context = new VelocityContext();
			template.merge(context, writer);
			assertNotNull(writer.toString());
		} catch (Exception e) {
			e.printStackTrace();
			fail();
		}
		try {
			page.setEncoding("UTF-8");
			page.getTemplate("listings.vm");
		} catch (Exception e) {
			fail();
		}
	}

	@Test
	public void testGetParameter() {
		ClassicHttpRequest request = HttpObjectFactory.createHttpRequest("GET", "/test/test.html?id=123");
		VelocityListingsPage page = new VelocityListingsPage(props);
		String value = page.getParameter(request, "id");
		assertEquals("123", value);
	}

	@Test
	public void testSize() {
		assertEquals("2 KB", String.format("%1$,3d KB", (long)Math.ceil(1025/1024d)).trim());
		assertEquals("1 KB", String.format("%1$,3d KB", (long)Math.ceil(1/1024d)).trim());
		assertEquals("0 KB", String.format("%1$,3d KB", (long)Math.ceil(0/1024d)).trim());
	}

	/**
	 * AC-5 / FR-5 / NFR-SEC-2: listings.vm renders $!{q} without Velocity's
	 * own escaping. Deliberately built with a minimal Properties set that
	 * does NOT enable the {@code EscapeHtmlReference} event handler present
	 * in the shared test velocity.properties (used by every other test in
	 * this class) - that handler would escape the reference on its own and
	 * make the test pass whether or not VelocityListingsPage.getListingsPage
	 * escapes q itself, which would not actually verify this intent's fix.
	 */
	@Test
	public void testGetListingsPageEscapesXssInQueryParameter() throws Exception {
		Properties bareProps = new Properties();
		bareProps.setProperty("resource.loaders", "list");
		bareProps.setProperty("resource.loader.list.class",
			"org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
		bareProps.setProperty("resource.loader.list.cache", "false");
		bareProps.setProperty("list.resource.search", "true");
		bareProps.setProperty("runtime.log.logsystem.class",
			"org.apache.velocity.runtime.log.NullLogSystem");

		VelocityListingsPage page = new VelocityListingsPage(bareProps);
		ClassicHttpRequest request = HttpObjectFactory.createHttpRequest(
			"GET", "/test/?q=%3Cscript%3Ealert(1)%3C%2Fscript%3E");
		ClassicHttpResponse response = HttpObjectFactory.createHttpResponse(200, "OK");
		File file = new File(ClassUtils.getURL(".", getClass().getClassLoader()).toURI());

		String html = page.getListingsPage(request, response, file);
		assertNotNull(html);
		assertFalse(html.contains("<script>"), "the raw <script> tag must not appear in the rendered output");
		assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"), "the escaped form of the query parameter must still be present");
	}
}
