package org.tamacat.httpd.core.di.xml;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXParseException;

public class SpringBeanDefineHandlerTest {

	SpringBeanDefineHandler handler;
	@BeforeEach
	public void setUp() throws Exception {
		handler = new SpringBeanDefineHandler();
		handler.setLogger(LoggerFactory.getLogger("Test"));
		handler.setConfigurationFile("test.xml");
	}

	@Test
	public void testWarningSAXParseException() {
		handler.warning(new SAXParseException("test", "", "", 1,1));
	}

	@Test
	public void testErrorSAXParseException() {
		handler.error(new SAXParseException("test", "", "", 1,1));
	}

	@Test
	public void testFatalErrorSAXParseException() {
		handler.fatalError(new SAXParseException("test", "", "", 1,1));
	}
}
