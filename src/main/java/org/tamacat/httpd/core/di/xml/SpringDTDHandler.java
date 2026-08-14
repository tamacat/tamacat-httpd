/*
 * Copyright (c) 2008 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core.di.xml;

import org.xml.sax.DTDHandler;
import org.xml.sax.SAXException;

public class SpringDTDHandler implements DTDHandler {
	
	@Override
	public void notationDecl(String name, String publicId, String systemId)
			throws SAXException {
		//LOG.trace("notationDecl()");
	}

	@Override
	public void unparsedEntityDecl(String name, String publicId,
			String systemId, String notationName) throws SAXException {
		//LOG.trace("unparsedEntityDecl()");
	}
}
