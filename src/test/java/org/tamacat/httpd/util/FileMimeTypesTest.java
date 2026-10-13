/*
 * Copyright (c) 2009, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.util;

import java.io.File;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tamacat.httpd.core.util.PropertyUtils;

public class FileMimeTypesTest {

	static final Properties mimeTypes;
    static {
    	mimeTypes = PropertyUtils.getProperties("org/tamacat/httpd/mime-types.properties");
    }
    
	@BeforeEach
	public void setUp() throws Exception {
	}

	@AfterEach
	public void tearDown() throws Exception {
	}
	
    protected String getContentType(File file) {
    	String fileName = file != null ? file.getName() : "";
    	String ext = fileName.substring(fileName.lastIndexOf('.')+1, fileName.length());
    	return mimeTypes.getProperty(ext.toLowerCase(), "text/html");
    }
    
	
    @Test
	public void testType() {
    	String fileName = "test.woff2";
    	String ext = fileName.substring(fileName.lastIndexOf('.')+1, fileName.length());
    	System.out.println(mimeTypes.getProperty(ext.toLowerCase(), "text/html"));
	}

}
