package org.tamacat.httpd.filter;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class HtmlLinkConvertInterceptorMultipleTest {

	private HtmlLinkConvertInterceptor target;
	
	@BeforeEach
	public void setUp() throws Exception {
		target = new HtmlLinkConvertInterceptor();
	}

	@AfterEach
	public void tearDown() throws Exception {
	}

	String pattern = "<[^<]*\\s+(href|src|action|.*[0-9]*;?url)=(?:\'|\")?([^('|\")]*)(?:\'|\")?[^>]*>";

	@Test
	public void testSetLinkPattern() {
		assertEquals(0, target.linkPatterns.size());
		
		target.setLinkPattern(pattern);
		assertEquals(1, target.linkPatterns.size());
	}
}
