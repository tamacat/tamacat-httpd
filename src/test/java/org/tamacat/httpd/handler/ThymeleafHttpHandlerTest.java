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
import org.tamacat.httpd.config.ServiceUrl;
import org.tamacat.httpd.exception.NotFoundException;
import org.tamacat.httpd.mock.HttpObjectFactory;

/**
 * New in this intent (`260822-path-traversal-hardening`): no test file for
 * {@link ThymeleafHttpHandler} existed before FR-2/AC-3 required one.
 */
public class ThymeleafHttpHandlerTest {

	ThymeleafHttpHandler handler;

	@BeforeEach
	public void setUp() throws Exception {
		handler = new ThymeleafHttpHandler();
		ServiceUrl serviceUrl = new ServiceUrl();
		serviceUrl.setPath("/");
		handler.setServiceUrl(serviceUrl);
		handler.setDocsRoot("./src/test/resources/htdocs/root/");
	}

	@Test
	public void testIsMatchUrlPattern_default() {
		assertTrue(handler.isMatchUrlPattern("/test.html"));
		assertTrue(handler.isMatchUrlPattern("/ctl/test.html"));
		assertFalse(handler.isMatchUrlPattern("/ctl/"));
	}

	@Test
	public void testSetFileEntity() {
		try {
			handler.setFileEntity(
				HttpObjectFactory.createHttpRequest("GET", "/docs"),
				HttpObjectFactory.createHttpResponse(200, "OK"),
				"/docs");
			fail();
		} catch (NotFoundException e) {
			assertTrue(true);
		}
	}

	/**
	 * FR-2 / AC-3: the C3 template branch (isMatchUrlPattern -> setEntity ->
	 * Thymeleaf's FileTemplateResolver) used to reach the filesystem with only
	 * the raw contains("..") check - it never went through getDecodeUri, so it
	 * had none of FR-1's canonicalization containment. A literal ".." in the
	 * request path is already rejected by that pre-existing raw check, both
	 * before and after this fix, so it would not distinguish the two. A
	 * symlinked directory inside docsRoot that points outside it contains no
	 * ".." in its name, so it is what actually distinguishes "only the raw
	 * check ran" (pre-fix: reaches the template resolver) from "getDecodeUri's
	 * containment check also ran" (post-fix: NotFoundException). A directory
	 * symlink (rather than a file symlink named e.g. "escape.html") is used
	 * deliberately: it is resolved by File#getCanonicalPath() regardless of
	 * the terminal filename/suffix the template engine ultimately builds, so
	 * the test does not depend on Thymeleaf's (configurable) template suffix
	 * happening to match the request's extension.
	 * <p>Skips (rather than fails) on environments that cannot create symlinks
	 * (e.g. Windows without Developer Mode / SeCreateSymbolicLinkPrivilege),
	 * per NFRQ-2.
	 */
	@Test
	public void testDoRequestTemplateBranchRejectsSymlinkEscapingDocsRoot() throws Exception {
		Path docsRootDir = Files.createTempDirectory("tc11-thymeleaf-docsroot-");
		Path outsideDir = Files.createTempDirectory("tc11-thymeleaf-outside-");
		Path secretFile = outsideDir.resolve("secret.html");
		Files.write(secretFile, "<html>secret</html>".getBytes("UTF-8"));
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
			ThymeleafHttpHandler h = new ThymeleafHttpHandler();
			ServiceUrl serviceUrl = new ServiceUrl();
			serviceUrl.setPath("/");
			h.setServiceUrl(serviceUrl);
			h.setDocsRoot(docsRootDir.toString());

			ClassicHttpRequest request = HttpObjectFactory.createHttpRequest("GET", "/linkdir/secret.html");
			ClassicHttpResponse response = HttpObjectFactory.createHttpResponse(200, "OK");
			HttpContext context = HttpObjectFactory.createHttpContext();
			try {
				h.doRequest(request, response, context);
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
