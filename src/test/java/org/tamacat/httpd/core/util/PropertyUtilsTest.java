/*
 * Copyright (c) 2007, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core.util;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URL;
import java.util.Properties;

import org.junit.jupiter.api.Test;

public class PropertyUtilsTest {

    static final String path1 = "org/tamacat/di/DIContainer.properties";
    static final String path2 = "org.tamacat.di.DIContainer.properties";
    static final String path3 = "test.xml";

    static final String path4 = "org/tamacat/di/not_found.properties";

//    @Test
//    public void testGetProperties() {
//        Properties props1 = PropertyUtils.getProperties(path1);
//        assertEquals("org.tamacat.di.impl.UnloadableClassLoader",
//                props1.getProperty("DIContainerClassLoader"));
//
//        Properties props2 = PropertyUtils.getProperties(path2);
//        assertEquals("org.tamacat.di.impl.UnloadableClassLoader",
//                props2.getProperty("DIContainerClassLoader"));
//
//        Properties props3 = PropertyUtils.getProperties(path3);
//        assertEquals("org.tamacat.di.impl.UnloadableClassLoader",
//                props3.getProperty("DIContainerClassLoader"));
//    }

    @Test
    public void testGetProperties() {
        try {
            Properties props1 = PropertyUtils.getProperties(path1);
            assertNotNull(props1);
            
            Properties props2 = PropertyUtils.getProperties(path2);
            assertNotNull(props2);

            Properties props3 = PropertyUtils.getProperties(path3);
            assertNotNull(props3);
            
            Properties props4 = PropertyUtils.getProperties("log4j.properties");
            assertNotNull(props4);
        } catch (Exception e) {
        	e.printStackTrace();
        	fail();
        }
    }
    
    @Test
    public void testGetPropertiesURL() {
        try {
        	URL url = ClassUtils.getURL(path1);
            Properties props1 = PropertyUtils.getProperties(url);
            assertNotNull(props1);
        } catch (Exception e) {
        	e.printStackTrace();
        	fail();
        }
    }
    
    @Test
    public void testGetPropertiesNotFound() {
        try {
            Properties props4 = PropertyUtils.getProperties(path4);
            fail("Should be throws ResourceNotFoundException. : " + props4);
        } catch (Exception e) {
            assertEquals(ResourceNotFoundException.class, e.getClass());
        }
    }
    
    @Test
    public void testMarge() {
    	Properties props = PropertyUtils.marge("test1.properties", "test2.properties");
    	//marge
    	assertEquals(props.getProperty("name1"), "1");
    	assertEquals(props.getProperty("name2"), "2");
    	assertEquals(props.getProperty("name3"), "3");

    	//override
    	assertEquals(props.getProperty("name10"), "ABC");
    	
    	//add
    	assertEquals(props.getProperty("name99"), "99");
    	
    	//empty value (-> marge)
    	assertEquals(props.getProperty("test1"), "");
    	assertEquals(props.getProperty("test2"), "");
    	
    	//comment out (-> do not marge)
    	assertEquals(props.getProperty("test3"), null);
    }
    
    @Test
    public void testMargeDefault() {
    	Properties props = PropertyUtils.marge("test1.properties", "no-file.properties");
    	//marge
    	assertEquals(props.getProperty("name1"), "1");
    	assertEquals(props.getProperty("name2"), "2");
    	assertEquals(props.getProperty("name3"), "3");

    	//default only
    	assertEquals(props.getProperty("name10"), "10");
    	assertEquals(props.getProperty("name99"), null);
    }
    
    @Test
    public void testMargeNotDefault() {
    	Properties props = PropertyUtils.marge("no-file.properties", "test2.properties");
    	//marge
    	assertEquals(props.getProperty("name1"), null);
    	assertEquals(props.getProperty("name2"), null);
    	assertEquals(props.getProperty("name3"), null);

    	//default only
    	assertEquals(props.getProperty("name10"), "ABC");
    	assertEquals(props.getProperty("name99"), "99");
    }
    
    @Test
    public void testMargeNotFound() {
    	Properties props1 = PropertyUtils.marge("no-file.properties", "no-file.properties");
    	assertEquals(props1.size(), 0);
    	
    	Properties props2 = PropertyUtils.marge(null, null);
    	assertEquals(props2.size(), 0);
    }
    
    @Test
    public void testDisableResolveSystemProperty() {
    	assertFalse(PropertyUtils.disableResolveSystemProperty(null));

    	Properties props = new Properties();
    	assertFalse(PropertyUtils.disableResolveSystemProperty(props));
    	
    	props.setProperty("PropertyUtils.disableResolveSystemProperty", "true");
    	assertTrue(PropertyUtils.disableResolveSystemProperty(props));
    }
    
    @Test
    public void testResolvSystemProperty() {
    	Properties props1 = PropertyUtils.getProperties("test3.properties");
    	assertEquals("${TEST_ENV_01}", props1.getProperty("key01"));
    	assertEquals("TEST", props1.getProperty("key02"));
    	
    	System.setProperty("TEST_ENV_01", "TEST_01");
    	System.setProperty("TEST_ENV_02", "TEST_02");
    	Properties props2 = PropertyUtils.getProperties("test3.properties");
    	assertEquals("TEST_01", props2.getProperty("key01"));
    	assertEquals("TEST_02", props2.getProperty("key02"));
    }
    
    @Test
    public void testResolvSystemProperty_disabled() {
    	Properties props1 = PropertyUtils.getProperties("test4.properties");
    	assertEquals("${TEST_ENV_01}", props1.getProperty("key01"));
    	assertEquals("${TEST_ENV_02:TEST}", props1.getProperty("key02"));
    	
    	System.setProperty("TEST_ENV_01", "TEST");
    	System.setProperty("TEST_ENV_02", "TEST_02");
    	Properties props2 = PropertyUtils.getProperties("test4.properties");
    	assertEquals("${TEST_ENV_01}", props2.getProperty("key01"));
    	assertEquals("${TEST_ENV_02:TEST}", props2.getProperty("key02"));
    }
}
