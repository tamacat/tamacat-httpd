/*
 * Copyright (c) 2026, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.handler;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.HttpServerRequestHandler.ResponseTrigger;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.Before;
import org.junit.Test;
import org.tamacat.httpd.core.BasicHttpStatus;
import org.tamacat.httpd.exception.ForbiddenException;
import org.tamacat.httpd.exception.NotFoundException;
import org.tamacat.httpd.exception.ServiceUnavailableException;
import org.tamacat.httpd.mock.HttpObjectFactory;

/**
 * <p>Tests for the routing core that replaced {@code DefaultHttpService} in 2.0
 * (code-generation Step 12 / Step 13).
 *
 * <p>{@code DefaultHttpServiceTest} covered the same ground against
 * {@code org.apache.http.protocol.HttpService#doService}, which core5 has no
 * counterpart for. Those tests are absorbed here.
 *
 * <p><b>Every test that produces a response asserts its status code explicitly</b> - ten of the
 * eleven. The exception is {@code testErrorIsNotConvertedToErrorPage}, which asserts that a
 * propagated {@link Error} submits no response at all, so there is no status to assert.
 * The error page is produced by
 * {@link org.tamacat.httpd.handler.page.ThymeleafErrorPage#getErrorPage}, which is also
 * what sets the status on the response. If that setter regressed, the body would still
 * be rendered and no exception would be raised - a 404 would be served as 200. An
 * assertion on the body alone would not catch that.
 *
 * <p>Covers SEC-2.1, SEC-2.3, REL-2.1, REL-2.2, REL-2.3.
 */
public class TamacatHttpServerRequestHandlerTest {

	/** Captures what the handler hands to {@code submitResponse}. */
	static class CapturingResponseTrigger implements ResponseTrigger {
		final List<ClassicHttpResponse> submitted = new ArrayList<>();
		final List<ClassicHttpResponse> informational = new ArrayList<>();

		@Override
		public void sendInformation(ClassicHttpResponse response) {
			informational.add(response);
		}

		@Override
		public void submitResponse(ClassicHttpResponse response) {
			submitted.add(response);
		}

		ClassicHttpResponse only() {
			assertEquals("exactly one final response must be submitted", 1, submitted.size());
			return submitted.get(0);
		}
	}

	/** Records that it ran, and answers 200 with a body. */
	static class RecordingHandler implements HttpRequestHandler {
		boolean called;
		final String body;

		RecordingHandler(String body) {
			this.body = body;
		}

		@Override
		public void handle(ClassicHttpRequest request, ClassicHttpResponse response,
				HttpContext context) throws HttpException, IOException {
			called = true;
			response.setCode(HttpStatus.SC_OK);
			response.setEntity(new org.apache.hc.core5.http.io.entity.StringEntity(
				body, org.apache.hc.core5.http.ContentType.TEXT_HTML));
		}
	}

	UriHttpRequestHandlerMapper mapper;
	TamacatHttpServerRequestHandler target;
	CapturingResponseTrigger trigger;
	HttpContext context;

	@Before
	public void setUp() throws Exception {
		mapper = new UriHttpRequestHandlerMapper();
		target = new TamacatHttpServerRequestHandler(mapper);
		trigger = new CapturingResponseTrigger();
		context = HttpObjectFactory.createHttpContext();
	}

	static String body(ClassicHttpResponse response) throws Exception {
		assertNotNull("an error page entity must be present", response.getEntity());
		return EntityUtils.toString(response.getEntity());
	}

	/**
	 * 13.1 - a path matching no registered pattern yields 404 and an error page.
	 * core5's own BasicHttpServerRequestHandler would answer 501 here. (SEC-2.1, REL-2.1)
	 */
	@Test
	public void testUnresolvedPathIsNotFound() throws Exception {
		mapper.register("/known/*", new RecordingHandler("known"));

		target.handle(HttpObjectFactory.createHttpRequest("GET", "/nowhere/x.html"),
			trigger, context);

		ClassicHttpResponse response = trigger.only();
		assertEquals(HttpStatus.SC_NOT_FOUND, response.getCode());
		assertEquals(BasicHttpStatus.SC_NOT_FOUND.getReasonPhrase(), response.getReasonPhrase());
		assertTrue(body(response).contains("404"));
	}

	/** 13.1 - with no handler registered at all, the same 404 path is taken. */
	@Test
	public void testEmptyMapperIsNotFound() throws Exception {
		target.handle(HttpObjectFactory.createHttpRequest("GET", "/anything"), trigger, context);

		assertEquals(HttpStatus.SC_NOT_FOUND, trigger.only().getCode());
	}

	/** 13.1 - a null mapper must not NPE its way past the 404. */
	@Test
	public void testNullMapperIsNotFound() throws Exception {
		TamacatHttpServerRequestHandler noMapper = new TamacatHttpServerRequestHandler(null);

		noMapper.handle(HttpObjectFactory.createHttpRequest("GET", "/anything"), trigger, context);

		assertEquals(HttpStatus.SC_NOT_FOUND, trigger.only().getCode());
	}

	/**
	 * 13.2 - a tamacat HttpException thrown by the handler keeps its own status.
	 * The status must come from the exception, not from a fixed 404/503. (REL-2.2)
	 */
	@Test
	public void testTamacatHttpExceptionPreservesStatus() throws Exception {
		mapper.register("/deny/*", new HttpRequestHandler() {
			@Override
			public void handle(ClassicHttpRequest request, ClassicHttpResponse response,
					HttpContext ctx) {
				throw new ForbiddenException();
			}
		});

		target.handle(HttpObjectFactory.createHttpRequest("GET", "/deny/x"), trigger, context);

		ClassicHttpResponse response = trigger.only();
		assertEquals(HttpStatus.SC_FORBIDDEN, response.getCode());
		assertEquals(BasicHttpStatus.SC_FORBIDDEN.getReasonPhrase(), response.getReasonPhrase());
		assertTrue(body(response).contains("403"));
	}

	/** 13.2 - a second status, so that the test cannot pass on a hard-coded 403. */
	@Test
	public void testTamacatHttpExceptionPreservesStatus_notFound() throws Exception {
		mapper.register("/gone/*", new HttpRequestHandler() {
			@Override
			public void handle(ClassicHttpRequest request, ClassicHttpResponse response,
					HttpContext ctx) {
				throw new NotFoundException();
			}
		});

		target.handle(HttpObjectFactory.createHttpRequest("GET", "/gone/x"), trigger, context);

		assertEquals(HttpStatus.SC_NOT_FOUND, trigger.only().getCode());
	}

	/**
	 * 13.3 - any other exception becomes a ServiceUnavailableException-equivalent
	 * error page, i.e. 503. (REL-2.3)
	 */
	@Test
	public void testRuntimeExceptionBecomesServiceUnavailable() throws Exception {
		mapper.register("/boom/*", new HttpRequestHandler() {
			@Override
			public void handle(ClassicHttpRequest request, ClassicHttpResponse response,
					HttpContext ctx) {
				throw new IllegalStateException("boom");
			}
		});

		target.handle(HttpObjectFactory.createHttpRequest("GET", "/boom/x"), trigger, context);

		ClassicHttpResponse response = trigger.only();
		assertEquals(new ServiceUnavailableException().getHttpStatus().getStatusCode(),
			response.getCode());
		assertEquals(HttpStatus.SC_SERVICE_UNAVAILABLE, response.getCode());
		assertTrue(body(response).contains("503"));
	}

	/** 13.3 - a checked exception takes the same branch as an unchecked one. */
	@Test
	public void testCheckedExceptionBecomesServiceUnavailable() throws Exception {
		mapper.register("/io/*", new HttpRequestHandler() {
			@Override
			public void handle(ClassicHttpRequest request, ClassicHttpResponse response,
					HttpContext ctx) throws IOException {
				throw new IOException("backend down");
			}
		});

		target.handle(HttpObjectFactory.createHttpRequest("GET", "/io/x"), trigger, context);

		assertEquals(HttpStatus.SC_SERVICE_UNAVAILABLE, trigger.only().getCode());
	}

	/**
	 * 13.3 - an Error is not an Exception. {@code catch (Exception e)} does not catch it,
	 * so it propagates to the caller rather than being turned into a 503. This pins the
	 * boundary of the catch rather than asserting it swallows everything.
	 */
	@Test
	public void testErrorIsNotConvertedToErrorPage() throws Exception {
		mapper.register("/err/*", new HttpRequestHandler() {
			@Override
			public void handle(ClassicHttpRequest request, ClassicHttpResponse response,
					HttpContext ctx) {
				throw new StackOverflowError("deliberate");
			}
		});

		try {
			target.handle(HttpObjectFactory.createHttpRequest("GET", "/err/x"), trigger, context);
			fail("an Error must not be converted into an error page");
		} catch (StackOverflowError expected) {
			assertEquals("deliberate", expected.getMessage());
		}
		assertEquals(0, trigger.submitted.size());
	}

	/**
	 * 13.5 - path-pattern resolution still picks the handler it did before the
	 * migration, and the successful path is left at 200 with the handler's own body.
	 */
	@Test
	public void testPathPatternResolution() throws Exception {
		RecordingHandler root = new RecordingHandler("root");
		RecordingHandler examples = new RecordingHandler("examples");
		mapper.register("/*", root);
		mapper.register("/examples/*", examples);

		target.handle(HttpObjectFactory.createHttpRequest("GET", "/examples/index.html"),
			trigger, context);

		ClassicHttpResponse response = trigger.only();
		assertEquals(HttpStatus.SC_OK, response.getCode());
		assertTrue(examples.called);
		assertFalse(root.called);
		assertEquals("examples", body(response));
	}

	/** 13.5 - the shorter pattern still wins when the longer one does not apply. */
	@Test
	public void testPathPatternResolutionFallsBackToRoot() throws Exception {
		RecordingHandler root = new RecordingHandler("root");
		RecordingHandler examples = new RecordingHandler("examples");
		mapper.register("/*", root);
		mapper.register("/examples/*", examples);

		target.handle(HttpObjectFactory.createHttpRequest("GET", "/other/index.html"),
			trigger, context);

		assertEquals(HttpStatus.SC_OK, trigger.only().getCode());
		assertTrue(root.called);
		assertFalse(examples.called);
	}

	/** 13.5 - the query string must not take part in the path match. */
	@Test
	public void testPathPatternResolutionIgnoresQueryString() throws Exception {
		RecordingHandler examples = new RecordingHandler("examples");
		mapper.register("/examples/*", examples);

		target.handle(HttpObjectFactory.createHttpRequest("GET", "/examples/x?a=/other/"),
			trigger, context);

		assertEquals(HttpStatus.SC_OK, trigger.only().getCode());
		assertTrue(examples.called);
	}
}
