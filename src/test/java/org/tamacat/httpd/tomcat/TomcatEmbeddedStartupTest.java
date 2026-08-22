/*
 * Copyright (c) 2026, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.tomcat;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.apache.catalina.Context;
import org.apache.catalina.Valve;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.valves.RemoteCIDRValve;
import org.junit.Test;
import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.httpd.config.ServiceUrl;
import org.tamacat.httpd.core.util.PropertyUtils;

/**
 * <p>Startup tests for the embedded Tomcat 11 integration.
 *
 * <p>Until this class existed nothing in the suite started a Tomcat connector and
 * spoke HTTP to it, so "Jasper compiles the bundled JSPs" was an assumption rather
 * than a measurement (requirements FR-7, assumption A-6). These five tests close
 * that gap and pin the behaviours the {@code RemoteAddrValve} to
 * {@code RemoteCIDRValve} migration changed:
 *
 * <ul>
 * <li>T-A - happy path: Tomcat 11 starts, Jasper compiles a bundled JSP and the
 *     rendered body comes back with HTTP 200 (FR-7, BR-16). With
 *     {@code allowRemoteAddrValve} unset no valve is registered at all (BR-1).</li>
 * <li>T-B - error case: a value written in the old regular-expression syntax is
 *     rejected by {@code RemoteCIDRValve} and the failure reaches the caller
 *     instead of being swallowed into a WARN (BR-2). Before the migration this
 *     path left the webapp deployed and unprotected.</li>
 * <li>T-C - edge case: the value both bundled configurations actually ship,
 *     {@code 127.0.0.1}, still registers a working filter and still serves
 *     loopback traffic (BR-6).</li>
 * <li>T-D - edge case: the war-deployment path. {@code deployWarFiles} is a
 *     different shape from {@code deployWebapps} - one try around the whole loop -
 *     so the migration gave it a two-pass structure: deploy every war first,
 *     then apply the valves outside the catch. T-D stages TWO wars and drives that
 *     path with a value the valve rejects, pinning the ordering (BR-4).</li>
 * <li>T-E - edge case: the same path with a value the valve accepts, checking that
 *     EVERY deployed war ends up with a valve rather than only the first (BR-4).</li>
 * </ul>
 *
 * <p>Every test binds a port obtained from {@code ServerSocket(0)} (BR-15, never a
 * fixed port) and stops its Tomcat in a {@code finally} block, because
 * {@link TomcatManager} keeps every instance in a static map for the life of the
 * JVM and a leaked one would disturb whatever test runs next.
 */
public class TomcatEmbeddedStartupTest {

	/** A bundled JSP with scriptlets, so a 200 proves Jasper really compiled it. */
	static final String JSP_PATH = "/examples/request.jsp";

	/** Text that only appears if the JSP was compiled and executed, not served as a file. */
	static final String RENDERED_FRAGMENT = "GET /examples/request.jsp";

	/** Text from the static part of the same JSP. */
	static final String STATIC_FRAGMENT = "List of HTTP Request Headers";

	/**
	 * T-A - happy path (FR-7, BR-16, and BR-1 for the unset case).
	 */
	@Test
	public void testBundledJspIsServedByTomcat11() throws Exception {
		int port = findFreePort();
		TomcatHandler handler = newHandler(port);
		//allowRemoteAddrValve deliberately left unset: BR-1 says no valve is created.
		Tomcat tomcat = null;
		try {
			handler.setServiceUrl(newServiceUrl("/examples/"));
			tomcat = handler.tomcat;
			assertNotNull("deploy() should have obtained a Tomcat instance", tomcat);

			Context ctx = findContext(tomcat, "/examples");
			assertNotNull("the /examples webapp was not deployed; see the WARN logged by "
				+ "deployWebapps for the reason", ctx);
			assertNull("BR-1: no valve may be registered while allowRemoteAddrValve is unset",
				findCidrValve(ctx));

			start(tomcat, port);
			Response res = get(port, JSP_PATH);

			assertEquals(diagnose(port, res), 200, res.status);
			assertTrue("the JSP was served but its static text is missing; body was:\n" + res.body,
				res.body.contains(STATIC_FRAGMENT));
			assertTrue("the JSP was served without being executed - Jasper did not compile it. "
				+ "Body was:\n" + res.body, res.body.contains(RENDERED_FRAGMENT));
		} finally {
			shutdown(port, tomcat);
		}
	}

	/**
	 * T-B - error case (BR-2). {@code 192\.168\..*} is a valid regular expression and
	 * was a legal value for the old {@code RemoteAddrValve}; {@code RemoteCIDRValve}
	 * cannot read it as a netmask. The point of the test is not that it fails but that
	 * the failure escapes {@code setServiceUrl} rather than being logged and forgotten.
	 */
	@Test
	public void testRegexStyleValueFailsTheStartup() throws Exception {
		int port = findFreePort();
		TomcatHandler handler = newHandler(port);
		handler.setAllowRemoteAddrValve("192\\.168\\..*");
		Tomcat tomcat = null;
		try {
			try {
				handler.setServiceUrl(newServiceUrl("/examples/"));
				fail("BR-2: a value RemoteCIDRValve cannot parse must abort the startup, "
					+ "but setServiceUrl returned normally. The webapp would now be "
					+ "serving traffic with no access filter at all.");
			} catch (IllegalArgumentException expected) {
				assertNotNull(expected);
			} finally {
				tomcat = handler.tomcat;
			}

			//the failure must not be a side effect of the webapp failing to deploy:
			//the Context has to exist, which is what makes the fail-open risk real.
			assertNotNull("the exception did not come from the valve - no Tomcat instance "
				+ "was even created", tomcat);
			assertNotNull("the webapp never deployed, so this test did not exercise the "
				+ "valve path it claims to", findContext(tomcat, "/examples"));
		} finally {
			shutdown(port, tomcat);
		}
	}

	/**
	 * T-C - edge case (BR-6). Both {@code src/test/resources/tomcat/components.xml} and
	 * {@code docker/tamacat/conf/tomcat/components.xml} ship {@code 127.0.0.1}. That
	 * value must keep working after the migration, and it must still permit loopback.
	 */
	@Test
	public void testBundledLoopbackValueStillWorks() throws Exception {
		int port = findFreePort();
		TomcatHandler handler = newHandler(port);
		handler.setAllowRemoteAddrValve("127.0.0.1");
		Tomcat tomcat = null;
		try {
			handler.setServiceUrl(newServiceUrl("/examples/"));
			tomcat = handler.tomcat;
			assertNotNull("deploy() should have obtained a Tomcat instance", tomcat);

			Context ctx = findContext(tomcat, "/examples");
			assertNotNull("the /examples webapp was not deployed", ctx);
			assertNotNull("BR-6: 127.0.0.1 must still produce a RemoteCIDRValve on the "
				+ "pipeline", findCidrValve(ctx));

			start(tomcat, port);
			Response res = get(port, JSP_PATH);
			assertEquals("BR-6: loopback must still be allowed through the filter. "
				+ diagnose(port, res), 200, res.status);
		} finally {
			shutdown(port, tomcat);
		}
	}

	/**
	 * T-D (BR-4, criteria 1 and 2) - the war path, which is structurally different
	 * from {@code deployWebapps} and which none of T-A/T-C reaches because they all
	 * set {@code useWarDeploy=false}.
	 *
	 * <p>{@code deployWarFiles} wraps its whole loop in one try/catch, so the
	 * migration could not simply lift the valve call out the way it did for
	 * {@code deployWebapps}. It became two passes: pass 1 deploys every war inside
	 * the existing try, collecting the Contexts; pass 2 runs outside the catch and
	 * applies the valve to each.
	 *
	 * <p><b>Two wars are staged, and that is the whole point.</b> With one war the
	 * test cannot tell the designs apart: {@code addWebapp} registers the Context
	 * before any valve call in either implementation, and nothing unregisters it, so
	 * "the Context still exists" would hold even for a single-pass loop. With two
	 * wars they diverge - a single-pass loop throws while deploying the first and
	 * never reaches the second, so only one Context exists; the two-pass loop
	 * deploys both before any valve is attempted, so both exist. Asserting on the
	 * SECOND war is therefore what pins the ordering.
	 *
	 * <p>The bundled {@code examples.war} cannot be used in place: {@code webapps/}
	 * also holds an extracted {@code examples/} directory, and {@code deployWarFiles}
	 * skips any war whose directory already exists. The wars are staged into a
	 * private directory under {@code target/} with no matching directories beside them.
	 */
	@Test
	public void testWarDeployAppliesValvesAfterEveryWarIsDeployed() throws Exception {
		int port = findFreePort();
		File webapps = stageTwoWarsInPrivateDirectory(port);

		TomcatHandler handler = newHandler(port);
		handler.setWebapps(webapps.getAbsolutePath());
		handler.setUseWarDeploy(true);
		handler.setAllowRemoteAddrValve("192\\.168\\..*");

		Tomcat tomcat = null;
		try {
			try {
				handler.setServiceUrl(newServiceUrl("/" + STAGED_WAR_A + "/"));
				fail("BR-4 criterion 1: the war path must not swallow a valve value "
					+ "RemoteCIDRValve cannot parse. setServiceUrl returned normally, so "
					+ "wars would be serving traffic with no access filter on them.");
			} catch (IllegalArgumentException expected) {
				assertNotNull(expected);
			} finally {
				tomcat = handler.tomcat;
			}

			assertNotNull("the exception did not come from the valve - no Tomcat "
				+ "instance was even created", tomcat);

			//BR-4 criterion 2. The first war proves only that deployment happened at
			//all; a single-pass loop would produce it too. The SECOND war is the
			//discriminating one - a single-pass loop throws on the first war's valve
			//and never gets here.
			assertNotNull("the first staged war never deployed, so this test did not "
				+ "reach the valve path it claims to",
				findContext(tomcat, "/" + STAGED_WAR_A));
			assertNotNull("BR-4 criterion 2: the SECOND war was never deployed. That "
				+ "means the valve was applied inside the deployment loop and aborted it "
				+ "at the first war, rather than in a second pass after every war was "
				+ "deployed. The two-pass ordering in business-logic-model 2.3.1 is not "
				+ "what got implemented.", findContext(tomcat, "/" + STAGED_WAR_B));
		} finally {
			shutdown(port, tomcat);
		}
	}

	/**
	 * T-E (BR-4, criterion 3) - the valid-value half of the war path: every Context
	 * pass 1 collected must come out of pass 2 with a valve on it, not just the first.
	 *
	 * <p>Without this, BR-4 would be covered only for the failing case. Note that
	 * {@code ConfigurationParseTest} does not cover it either: its {@code webapps/}
	 * holds an extracted {@code examples/} directory beside {@code examples.war}, so
	 * {@code deployWarFiles} logs "[skip] war deploy" and pass 2 iterates an empty list.
	 */
	@Test
	public void testWarDeployAppliesTheValveToEveryDeployedWar() throws Exception {
		int port = findFreePort();
		File webapps = stageTwoWarsInPrivateDirectory(port);

		TomcatHandler handler = newHandler(port);
		handler.setWebapps(webapps.getAbsolutePath());
		handler.setUseWarDeploy(true);
		handler.setAllowRemoteAddrValve("127.0.0.1");

		Tomcat tomcat = null;
		try {
			handler.setServiceUrl(newServiceUrl("/" + STAGED_WAR_A + "/"));
			tomcat = handler.tomcat;
			assertNotNull("no Tomcat instance was created", tomcat);

			for (String war : new String[] { STAGED_WAR_A, STAGED_WAR_B }) {
				Context ctx = findContext(tomcat, "/" + war);
				assertNotNull("the staged war /" + war + " was not deployed", ctx);
				assertNotNull("BR-4 criterion 3: /" + war + " was deployed but has no "
					+ "RemoteCIDRValve on its pipeline. Pass 2 must apply the valve to "
					+ "every Context pass 1 collected, not only the first.",
					findCidrValve(ctx));
			}
		} finally {
			shutdown(port, tomcat);
		}
	}

	// ------------------------------------------------------------------ helpers

	/** Context roots of the wars staged by {@link #stageTwoWarsInPrivateDirectory(int)}. */
	static final String STAGED_WAR_A = "wardeploytesta";
	static final String STAGED_WAR_B = "wardeploytestb";

	/**
	 * Copies the bundled war twice, under two names, into a directory of its own so
	 * {@code deployWarFiles} actually deploys both instead of skipping them. Two are
	 * needed to tell a two-pass loop from a single-pass one - see T-D. Returns the
	 * directory.
	 */
	static File stageTwoWarsInPrivateDirectory(int port) throws IOException {
		File dir = new File("target/tomcat-war-" + port).getAbsoluteFile();
		if (!dir.isDirectory() && !dir.mkdirs()) {
			throw new IOException("could not create the staging directory " + dir);
		}
		File source = new File("webapps/examples.war");
		assertTrue("the bundled webapps/examples.war is missing, so the war-deploy "
			+ "tests cannot stage anything to deploy", source.isFile());
		for (String name : new String[] { STAGED_WAR_A, STAGED_WAR_B }) {
			Files.copy(source.toPath(), new File(dir, name + ".war").toPath(),
				StandardCopyOption.REPLACE_EXISTING);
			//deployWarFiles skips a war whose extracted directory already exists, so a
			//rerun in a dirty target/ would silently deploy nothing and pass vacuously.
			File extracted = new File(dir, name);
			assertFalse("a previous run left " + extracted + " behind; deployWarFiles "
				+ "would skip the war and this test would pass without deploying "
				+ "anything. Run mvn clean.", extracted.isDirectory());
		}
		return dir;
	}

	TomcatHandler newHandler(int port) {
		TomcatHandler handler = new TomcatHandler();
		handler.setPort(port);
		handler.setHostname("127.0.0.1");
		handler.setBindAddress("127.0.0.1");
		handler.setUseWarDeploy(false);
		//setWebapps() expands ${server.home} only while work still holds the
		//placeholder, so it has to be called before setWork().
		handler.setWebapps("${server.home}/webapps");
		handler.setWork(new File("target/tomcat-test-" + port).getAbsolutePath());
		//keep startup fast and independent of whatever is on the surefire classpath
		handler.setScanBootstrapClassPath(false);
		handler.setScanClassPath(false);
		handler.setScanManifest(false);
		handler.setScanAllDirectories(false);
		handler.setScanAllFiles(false);
		return handler;
	}

	ServiceUrl newServiceUrl(String path) {
		ServiceUrl serviceUrl = new ServiceUrl(
			new ServerConfig(PropertyUtils.getProperties("server.properties")));
		serviceUrl.setPath(path);
		return serviceUrl;
	}

	static int findFreePort() throws IOException {
		ServerSocket socket = new ServerSocket(0);
		try {
			socket.setReuseAddress(true);
			return socket.getLocalPort();
		} finally {
			socket.close();
		}
	}

	static Context findContext(Tomcat tomcat, String contextPath) {
		return (Context) tomcat.getHost().findChild(contextPath);
	}

	static Valve findCidrValve(Context ctx) {
		for (Valve valve : ctx.getPipeline().getValves()) {
			if (valve instanceof RemoteCIDRValve) {
				return valve;
			}
		}
		return null;
	}

	/**
	 * The port came from {@code ServerSocket(0)} and was released before Tomcat binds
	 * it, so another process can slip in between. Failing here means a port clash, not
	 * a Tomcat 11 problem - say so, or the next reader will chase the wrong bug.
	 */
	static void start(Tomcat tomcat, int port) {
		try {
			tomcat.start();
		} catch (Exception e) {
			throw new AssertionError("Tomcat could not bind 127.0.0.1:" + port
				+ ". This is almost always another process taking the port between "
				+ "ServerSocket(0) releasing it and Tomcat binding it, not a defect in "
				+ "the Tomcat 11 migration. Rerun before investigating.", e);
		}
	}

	static Response get(int port, String path) throws Exception {
		HttpURLConnection con = (HttpURLConnection)
			URI.create("http://127.0.0.1:" + port + path).toURL().openConnection();
		try {
			con.setRequestMethod("GET");
			con.setConnectTimeout(10000);
			con.setReadTimeout(30000); //the first hit pays for the JSP compile
			int status = con.getResponseCode();
			//a Jasper compile failure arrives as 500 with the compiler output in the
			//error stream, which is exactly what the caller needs to see
			InputStream in = (status >= 400) ? con.getErrorStream() : con.getInputStream();
			return new Response(status, read(in));
		} finally {
			con.disconnect();
		}
	}

	static String read(InputStream in) throws IOException {
		if (in == null) {
			return "";
		}
		try {
			ByteArrayOutputStream buf = new ByteArrayOutputStream();
			byte[] chunk = new byte[4096];
			int len;
			while ((len = in.read(chunk)) > 0) {
				buf.write(chunk, 0, len);
			}
			return new String(buf.toByteArray(), StandardCharsets.UTF_8);
		} finally {
			in.close();
		}
	}

	static String diagnose(int port, Response res) {
		if (res.status == 200) {
			return "";
		}
		return "Expected 200 from http://127.0.0.1:" + port + JSP_PATH + " but got "
			+ res.status + ". A 404 means the webapp did not deploy; a 403 means the "
			+ "access filter rejected loopback; a 500 usually carries the Jasper "
			+ "compile error below. Body was:\n" + res.body + "\n";
	}

	/**
	 * Stops and destroys the instance and drops it from {@link TomcatManager}'s static
	 * map, so a later test that happens to draw the same port gets a fresh Tomcat
	 * rather than this stopped one (BR-15).
	 */
	static void shutdown(int port, Tomcat tomcat) {
		try {
			if (tomcat != null) {
				try {
					tomcat.stop();
				} finally {
					tomcat.destroy();
				}
			}
		} catch (Exception e) {
			//never mask the test's own failure, but do not hide this either
			System.err.println("failed to shut down the test Tomcat on port " + port
				+ ": " + e);
		} finally {
			TomcatManager.MANAGER.remove(Integer.valueOf(port));
		}
	}

	static class Response {
		final int status;
		final String body;

		Response(int status, String body) {
			this.status = status;
			this.body = body;
		}
	}
}
