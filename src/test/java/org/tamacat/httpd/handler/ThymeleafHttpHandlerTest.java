package org.tamacat.httpd.handler;

import static org.junit.Assert.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.protocol.HttpContext;
import org.junit.Assume;
import org.junit.Test;
import org.tamacat.httpd.config.ServiceUrl;
import org.tamacat.httpd.exception.NotFoundException;
import org.tamacat.httpd.mock.HttpObjectFactory;

public class ThymeleafHttpHandlerTest {

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
	 * containment check also ran" (post-fix: NotFoundException). Mirrors
	 * {@link VelocityHttpHandlerTest#testDoRequestTemplateBranchRejectsSymlinkEscapingDocsRoot}
	 * using a directory symlink (rather than a file symlink) for the same
	 * reason given there: consistency with the general resolution rule, not
	 * a dependency on Thymeleaf's default suffix (".html") happening to match
	 * the request's own extension.
	 * <p>Skips (rather than fails) on environments that cannot create symlinks
	 * (e.g. Windows without Developer Mode / SeCreateSymbolicLinkPrivilege),
	 * per NFRQ-2.
	 */
	@Test
	public void testDoRequestTemplateBranchRejectsSymlinkEscapingDocsRoot() throws Exception {
		Path docsRootDir = Files.createTempDirectory("tc11-thymeleaf-docsroot-");
		Path outsideDir = Files.createTempDirectory("tc11-thymeleaf-outside-");
		Path secretFile = outsideDir.resolve("secret.html");
		Files.write(secretFile, "secret".getBytes("UTF-8"));
		Path link = docsRootDir.resolve("linkdir");
		try {
			Files.createSymbolicLink(link, outsideDir);
		} catch (Exception e) {
			Files.deleteIfExists(secretFile);
			Files.deleteIfExists(outsideDir);
			Files.deleteIfExists(docsRootDir);
			Assume.assumeNoException(
				"Skipping symlink escape test: cannot create symlinks in this environment", e);
			return;
		}
		try {
			ThymeleafHttpHandler h = new ThymeleafHttpHandler();
			ServiceUrl serviceUrl = new ServiceUrl();
			serviceUrl.setPath("/");
			h.setServiceUrl(serviceUrl);
			h.setDocsRoot(docsRootDir.toString());

			HttpRequest req = HttpObjectFactory.createHttpRequest("GET", "/linkdir/secret.html");
			HttpResponse resp = HttpObjectFactory.createHttpResponse(200, "OK");
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
