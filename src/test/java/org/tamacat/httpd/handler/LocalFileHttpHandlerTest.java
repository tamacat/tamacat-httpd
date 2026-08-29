package org.tamacat.httpd.handler;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.tamacat.httpd.config.ServiceUrl;
import org.tamacat.httpd.exception.HttpException;
import org.tamacat.httpd.exception.NotFoundException;
import org.tamacat.httpd.filter.AccessLogFilter;
import org.tamacat.httpd.mock.HttpObjectFactory;

public class LocalFileHttpHandlerTest {

	@Test
	public void testHandle() {
		LocalFileHttpHandler handler = new LocalFileHttpHandler();
		ClassicHttpRequest request = HttpObjectFactory.createHttpRequest("GET", "/");
		ClassicHttpResponse response = HttpObjectFactory.createHttpResponse(200, "OK");
		HttpContext context = HttpObjectFactory.createHttpContext();

		ServiceUrl serviceUrl = new ServiceUrl();
		serviceUrl.setPath("/");
		handler.setServiceUrl(serviceUrl);
		handler.setListings(true);
		handler.setDocsRoot("./src/test/resources/htdocs/root/");

		handler.setHttpFilter(new AccessLogFilter());
		handler.handle(request, response, context);
	}

	@Test
	public void testDoRequest() throws Exception {
		ClassicHttpRequest request = HttpObjectFactory.createHttpRequest("GET", "/");
		ClassicHttpResponse response = HttpObjectFactory.createHttpResponse(200, "OK");
		HttpContext context = HttpObjectFactory.createHttpContext();

		LocalFileHttpHandler handler = new LocalFileHttpHandler();
		ServiceUrl serviceUrl = new ServiceUrl();
		serviceUrl.setPath("/");
		handler.setServiceUrl(serviceUrl);
		handler.setListings(true);
		handler.setDocsRoot("./src/test/resources/htdocs/root/");
		handler.doRequest(request, response, context);
	}

	@Test
	public void testSetWelcomeFile() {
		LocalFileHttpHandler handler = new LocalFileHttpHandler();
		assertNotNull(handler.welcomeFile);
		assertEquals("index.html", handler.welcomeFile);

		handler.setWelcomeFile("top.html");
		assertEquals("top.html", handler.welcomeFile);

		handler.setListings(false);
		assertEquals("top.html", handler.welcomeFile);

		handler.setListings(true);
		assertNull(handler.welcomeFile);
	}

	@Test
	public void testSetListingPages() {
		LocalFileHttpHandler handler = new LocalFileHttpHandler();
		handler.setServiceUrl(new ServiceUrl());
		assertNotNull(handler.listingPage);

		handler.setListingsPage("");
		assertNotNull(handler.listingPage);

	}

	@Test
	public void testSetEncoding() {
		LocalFileHttpHandler handler = new LocalFileHttpHandler();
		assertEquals("UTF-8", handler.encoding);

		handler.setEncoding("Windows-31J");
		assertEquals("Windows-31J", handler.encoding);
	}

	@Test
	public void testGetDecodeUri() {
		LocalFileHttpHandler handler = new LocalFileHttpHandler();
		assertEquals("/", handler.getDecodeUri("/"));
		assertEquals("/test/index.html", handler.getDecodeUri("/test/index.html"));
		assertEquals("///", handler.getDecodeUri("///"));

		assertEquals("/\\index.html", handler.getDecodeUri("/\\index.html"));

		assertEquals("/ index.html", handler.getDecodeUri("/%20index.html"));
		assertEquals("/.", handler.getDecodeUri("/%2e"));
		assertEquals("/./index.html", handler.getDecodeUri("/%2e/index.html"));
		assertEquals("///index.html", handler.getDecodeUri("/%2f/index.html"));

		try {
			handler.getDecodeUri("/%2e%2e/index.html");
			fail();
		} catch (NotFoundException e) {
			assertTrue(true);
		}
		try {
			handler.getDecodeUri("/%2e%2e%2findex.html");
			fail();
		} catch (NotFoundException e) {
			assertTrue(true);
		}
		try {
			handler.getDecodeUri("/../");
			fail();
		} catch (NotFoundException e) {
			assertTrue(true);
		}
		try {
			handler.getDecodeUri("../");
			fail();
		} catch (NotFoundException e) {
			assertTrue(true);
		}

		try {
			handler.getDecodeUri("/..");
			fail();
		} catch (NotFoundException e) {
			assertTrue(true);
		}
		try {
			handler.getDecodeUri("/%2e%2e");
			fail();
		} catch (NotFoundException e) {
			assertTrue(true);
		}
		try {
			handler.getDecodeUri("..\\index.html");
			fail();
		} catch (NotFoundException e) {
			assertTrue(true);
		}

		//FR-3: "none" is not a supported encoding name, so URLDecoder.decode()
		//throws UnsupportedEncodingException. This used to be swallowed by an
		//empty catch, silently degrading the check to a raw-string ".." test and
		//letting an otherwise-valid-looking request through undecoded. It is now
		//fail-closed: any decode failure throws NotFoundException, regardless of
		//whether the raw (still-encoded) string itself contains "..".
		handler.setEncoding("none");
		try {
			handler.getDecodeUri("/index.html");
			fail();
		} catch (NotFoundException e) {
			assertTrue(true);
		}
		try {
			handler.getDecodeUri("/%20index.html");
			fail();
		} catch (NotFoundException e) {
			assertTrue(true);
		}
		try {
			handler.getDecodeUri("/../index.html");
			fail();
		} catch (NotFoundException e) {
			assertTrue(true);
		}
	}

	/**
	 * NFRQ-2 / AC-2: a symlink inside docsRoot that points outside docsRoot
	 * contains no ".." in its name, so the pre-existing contains("..")
	 * blocklist check cannot reject it - only FR-1's canonicalization
	 * containment (getCanonicalPath() resolves the symlink target) can. Skips
	 * (rather than fails) on environments that cannot create symlinks (e.g.
	 * Windows without Developer Mode / SeCreateSymbolicLinkPrivilege).
	 */
	@Test
	public void testGetDecodeUriRejectsSymlinkEscapingDocsRoot() throws Exception {
		Path docsRootDir = Files.createTempDirectory("tc11-docsroot-");
		Path outsideDir = Files.createTempDirectory("tc11-outside-");
		Path secretFile = outsideDir.resolve("secret.txt");
		Files.write(secretFile, "secret".getBytes("UTF-8"));
		Path link = docsRootDir.resolve("escape.txt");
		try {
			Files.createSymbolicLink(link, secretFile);
		} catch (Exception e) {
			Files.deleteIfExists(secretFile);
			Files.deleteIfExists(outsideDir);
			Files.deleteIfExists(docsRootDir);
			Assumptions.assumeTrue(false,
				"Skipping symlink escape test: cannot create symlinks in this environment" + ": " + e);
			return;
		}
		try {
			LocalFileHttpHandler handler = new LocalFileHttpHandler();
			handler.setDocsRoot(docsRootDir.toString());
			try {
				handler.getDecodeUri("/escape.txt");
				fail("a symlink pointing outside docsRoot must be rejected");
			} catch (NotFoundException e) {
				assertTrue(true);
			}
		} finally {
			Files.deleteIfExists(link);
			Files.deleteIfExists(secretFile);
			Files.deleteIfExists(outsideDir);
			Files.deleteIfExists(docsRootDir);
		}
	}

	/**
	 * NFR-REL-3: when docsRoot itself cannot be canonicalized (misconfiguration),
	 * AbstractHttpHandler#resolveCanonicalDocsRoot's IOException catch must not
	 * propagate or crash the server - it caches null, which makes
	 * isWithinDocsRoot fail closed. A NUL byte in the path reliably makes
	 * File#getCanonicalPath() throw IOException("Invalid file path") on the
	 * JVMs this project targets, so it is used here instead of trying to
	 * construct an actually-unreadable directory (which is unreliable to set
	 * up portably, e.g. root always has read access on many CI runners).
	 */
	@Test
	public void testSetDocsRootWithUnresolvableCanonicalPathDoesNotThrow() {
		LocalFileHttpHandler handler = new LocalFileHttpHandler();
		//Must not throw - the IOException from getCanonicalPath() is caught
		//and logged, not propagated (NFR-REL-3). The u0000 escape sequence in
		//the docsRoot string below is what actually makes getCanonicalPath() throw IOException("Invalid file path").
		handler.setDocsRoot("docroot\u0000evil");
		try {
			handler.getDecodeUri("/index.html");
			fail("docsRoot with an unresolvable canonical path must fail closed");
		} catch (NotFoundException e) {
			assertTrue(true);
		}
	}

	/**
	 * NFR-REL-3: when the per-request file (not docsRoot itself) cannot be
	 * canonicalized, isWithinDocsRoot's own IOException catch must reject the
	 * request rather than propagate the exception or default to "contained".
	 * A NUL byte in the decoded request path triggers this specific catch
	 * (as opposed to the previous test, which triggers docsRoot's own catch).
	 */
	@Test
	public void testGetDecodeUriRejectsUnresolvableRequestPath() throws Exception {
		Path docsRootDir = Files.createTempDirectory("tc11-docsroot-ioexception-");
		try {
			LocalFileHttpHandler handler = new LocalFileHttpHandler();
			handler.setDocsRoot(docsRootDir.toString());
			try {
				handler.getDecodeUri("/evil\u0000.txt");
				fail("a request path whose canonical form cannot be resolved must be rejected");
			} catch (NotFoundException e) {
				assertTrue(true);
			}
		} finally {
			Files.deleteIfExists(docsRootDir);
		}
	}

	@Test
	public void testGetFileEntity() {
		LocalFileHttpHandler handler = new LocalFileHttpHandler();
		assertNotNull(handler.getFileEntity(new File("./src/test/resources/htdocs/index.html")));
	}

	@Test
	public void testSetClassLoader() {
		LocalFileHttpHandler handler = new LocalFileHttpHandler();
		assertEquals(getClass().getClassLoader(), handler.getClassLoader());

		handler.setClassLoader(Thread.currentThread().getContextClassLoader());
		assertEquals(Thread.currentThread().getContextClassLoader(), handler.getClassLoader());
	}

	@Test
	public void testDefaultAllowMethods() {
		LocalFileHttpHandler handler = new LocalFileHttpHandler();
		assertEquals("GET,HEAD,POST,OPTIONS", handler.allowMethodValue);

		ClassicHttpRequest request = HttpObjectFactory.createHttpRequest("OPTIONS", "/");
		ClassicHttpResponse response = HttpObjectFactory.createHttpResponse(200, "OK");
		HttpContext context = HttpObjectFactory.createHttpContext();
		handler.handle(request, response, context);
		assertEquals("GET,HEAD,POST,OPTIONS", response.getFirstHeader("Allow").getValue());
	}

	@Test
	public void testSetAllowMethods() {
		LocalFileHttpHandler handler = new LocalFileHttpHandler();
		handler.setAllowMethods("GET,HEAD,POST");
		assertEquals("GET,HEAD,POST", handler.allowMethodValue);

		ClassicHttpRequest request = HttpObjectFactory.createHttpRequest("OPTIONS", "/");
		ClassicHttpResponse response = HttpObjectFactory.createHttpResponse(200, "OK");
		HttpContext context = HttpObjectFactory.createHttpContext();
		try {
			handler.handle(request, response, context);
			fail();
		} catch (HttpException e) {
			assertTrue(e.getHttpStatus().getStatusCode() == 405);
		}
	}

	@Test
	public void testSetAllowMethodsNull() {
		LocalFileHttpHandler handler = new LocalFileHttpHandler();
		ServiceUrl serviceUrl = new ServiceUrl();
		serviceUrl.setPath("/");
		handler.setServiceUrl(serviceUrl);
		handler.setListings(true);
		handler.setDocsRoot("./src/test/resources/htdocs/root/");

		handler.setAllowMethods(null);
		assertNull(handler.allowMethodValue);

		ClassicHttpRequest request = HttpObjectFactory.createHttpRequest("OPTIONS", "/");
		ClassicHttpResponse response = HttpObjectFactory.createHttpResponse(200, "OK");
		HttpContext context = HttpObjectFactory.createHttpContext();
		try {
			handler.handle(request, response, context);
			assertTrue(response.getCode() == 200);
		} catch (HttpException e) {
			fail();
		}
	}
}
