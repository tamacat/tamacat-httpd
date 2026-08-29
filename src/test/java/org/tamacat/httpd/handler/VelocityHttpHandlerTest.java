package org.tamacat.httpd.handler;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.httpd.config.ServiceUrl;
import org.tamacat.httpd.exception.NotFoundException;
import org.tamacat.httpd.mock.HttpObjectFactory;

public class VelocityHttpHandlerTest {

	ClassicHttpRequest request = HttpObjectFactory.createHttpRequest("GET", "/");
	ClassicHttpResponse response = HttpObjectFactory.createHttpResponse(200, "OK");
	HttpContext context = HttpObjectFactory.createHttpContext();
	VelocityHttpHandler handler;

	@BeforeEach
	public void setUp() throws Exception {
		handler = new VelocityHttpHandler();

		ServerConfig config = new ServerConfig();
		ServiceUrl serviceUrl = new ServiceUrl(config);
		serviceUrl.setPath("/");
		handler.setDocsRoot("./src/test/resources/htdocs/root/");
		handler.setListings(true);
		handler.setServiceUrl(serviceUrl);
	}

	@Test
	public void testDoRequest() throws Exception {
		handler.doRequest(request, response, context);
		assertTrue(
			handler.getErrorPage().getErrorPage(request, response, new NotFoundException()
		).indexOf("404 Not Found")>=0);
	}


	@Test
	public void testIsMatchUrlPattern_default() {
		//VelocityHttpHandler handler = new VelocityHttpHandler();
		assertTrue(handler.isMatchUrlPattern("/test.html"));
		assertTrue(handler.isMatchUrlPattern("/ctl/test.html"));
		assertFalse(handler.isMatchUrlPattern("/ctl/"));
	}

	@Test
	public void testIsMatchUrlPattern_single() {
		//VelocityHttpHandler handler = new VelocityHttpHandler();
		handler.setUrlPatterns(".do");
		assertFalse(handler.isMatchUrlPattern("/test.html"));
		assertFalse(handler.isMatchUrlPattern("/ctl/test.html"));
		assertFalse(handler.isMatchUrlPattern("/ctl/"));
		assertTrue(handler.isMatchUrlPattern("/test.do"));
	}

	@Test
	public void testIsMatchUrlPattern_multi() {
		//VelocityHttpHandler handler = new VelocityHttpHandler();
		handler.setUrlPatterns("/ctl/, .do");
		assertFalse(handler.isMatchUrlPattern("/test.html"));
		assertTrue(handler.isMatchUrlPattern("/ctl/test.html"));
		assertTrue(handler.isMatchUrlPattern("/ctl/"));
		assertTrue(handler.isMatchUrlPattern("/test.do"));
	}
	
	@Test
	public void testSetFileEntity() {
		try {
			handler.setFileEntity(request, response, "/docs");
			fail();
		} catch (NotFoundException e) {
			assertEquals("/docs is not found this server.", e.getMessage());
		}
	}

	/**
	 * FR-2 / AC-3: the C6 template branch (isMatchUrlPattern -> setEntity ->
	 * Velocity's file resource loader) used to reach the filesystem with only
	 * the raw contains("..") check - it never went through getDecodeUri, so it
	 * had none of FR-1's canonicalization containment. A literal ".." in the
	 * request path is already rejected by that pre-existing raw check, both
	 * before and after this fix, so it would not distinguish the two. A
	 * symlinked directory inside docsRoot that points outside it contains no
	 * ".." in its name, so it is what actually distinguishes "only the raw
	 * check ran" (pre-fix: reaches the resource loader) from "getDecodeUri's
	 * containment check also ran" (post-fix: NotFoundException). A directory
	 * symlink (rather than a file symlink named e.g. "escape.html") is used
	 * deliberately: Velocity always appends ".vm" to the extension-stripped
	 * request name, so a containment check against a file named to match the
	 * request's own ".html" extension would be checking a different filename
	 * than the one Velocity actually resolves. A directory symlink is
	 * resolved by File#getCanonicalPath() regardless of the terminal
	 * filename/suffix, so the test does not depend on that mismatch.
	 * <p>Skips (rather than fails) on environments that cannot create symlinks
	 * (e.g. Windows without Developer Mode / SeCreateSymbolicLinkPrivilege),
	 * per NFRQ-2.
	 */
	@Test
	public void testDoRequestTemplateBranchRejectsSymlinkEscapingDocsRoot() throws Exception {
		Path docsRootDir = Files.createTempDirectory("tc11-velocity-docsroot-");
		Path outsideDir = Files.createTempDirectory("tc11-velocity-outside-");
		Path secretFile = outsideDir.resolve("secret.vm");
		Files.write(secretFile, "secret".getBytes("UTF-8"));
		Path link = docsRootDir.resolve("linkdir");
		try {
			Files.createSymbolicLink(link, outsideDir);
		} catch (Exception e) {
			Files.deleteIfExists(secretFile);
			Files.deleteIfExists(outsideDir);
			Files.deleteIfExists(docsRootDir);
			Assumptions.assumeTrue(false,
				"Skipping symlink escape test: cannot create symlinks in this environment" + ": " + e);
			return;
		}
		try {
			VelocityHttpHandler h = new VelocityHttpHandler();
			ServerConfig config = new ServerConfig();
			ServiceUrl serviceUrl = new ServiceUrl(config);
			serviceUrl.setPath("/");
			h.setDocsRoot(docsRootDir.toString());
			h.setServiceUrl(serviceUrl);

			ClassicHttpRequest req = HttpObjectFactory.createHttpRequest("GET", "/linkdir/secret.html");
			ClassicHttpResponse resp = HttpObjectFactory.createHttpResponse(200, "OK");
			HttpContext ctx = HttpObjectFactory.createHttpContext();
			try {
				h.doRequest(req, resp, ctx);
				fail("a symlinked directory pointing outside docsRoot must be rejected");
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
}
