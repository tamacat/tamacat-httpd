/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.tamacat.httpcore4.message;

import org.tamacat.httpcore4.HeaderElement;
import org.tamacat.httpcore4.NameValuePair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Simple tests for {@link HeaderElement}.
 */
public class TestHeaderElement {

    @Test
    public void testConstructor3() throws Exception {
        final HeaderElement element = new BasicHeaderElement("name", "value",
                new NameValuePair[] {
                    new BasicNameValuePair("param1", "value1"),
                    new BasicNameValuePair("param2", "value2")
                } );
        Assertions.assertEquals("name", element.getName());
        Assertions.assertEquals("value", element.getValue());
        Assertions.assertEquals(2, element.getParameters().length);
        Assertions.assertEquals("value1", element.getParameterByName("param1").getValue());
        Assertions.assertEquals("value2", element.getParameterByName("param2").getValue());
    }

    @Test
    public void testConstructor2() throws Exception {
        final HeaderElement element = new BasicHeaderElement("name", "value");
        Assertions.assertEquals("name", element.getName());
        Assertions.assertEquals("value", element.getValue());
        Assertions.assertEquals(0, element.getParameters().length);
    }


    @Test
    public void testInvalidName() {
        try {
            new BasicHeaderElement(null, null, null);
            Assertions.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            //expected
        }
    }

    @Test
    public void testParamByName() throws Exception {
        final String s = "name = value; param1 = value1; param2 = value2";
        final HeaderElement element = BasicHeaderValueParser.parseHeaderElement(s, null);
        Assertions.assertEquals("value1", element.getParameterByName("param1").getValue());
        Assertions.assertEquals("value2", element.getParameterByName("param2").getValue());
        Assertions.assertNull(element.getParameterByName("param3"));
        try {
            element.getParameterByName(null);
            Assertions.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            //expected
        }
    }

    @Test
    public void testHashCode() {
        final HeaderElement element1 = new BasicHeaderElement("name", "value",
                new NameValuePair[] {
                    new BasicNameValuePair("param1", "value1"),
                    new BasicNameValuePair("param2", "value2")
                } );
        final HeaderElement element2 = new BasicHeaderElement("name", "value",
                new NameValuePair[] {
                    new BasicNameValuePair("param2", "value2"),
                    new BasicNameValuePair("param1", "value1")
                } );
        final HeaderElement element3 = new BasicHeaderElement("name", "value");
        final HeaderElement element4 = new BasicHeaderElement("name", "value");
        final HeaderElement element5 = new BasicHeaderElement("name", "value",
                new NameValuePair[] {
                    new BasicNameValuePair("param1", "value1"),
                    new BasicNameValuePair("param2", "value2")
                } );
        Assertions.assertTrue(element1.hashCode() != element2.hashCode());
        Assertions.assertTrue(element1.hashCode() != element3.hashCode());
        Assertions.assertTrue(element2.hashCode() != element3.hashCode());
        Assertions.assertTrue(element3.hashCode() == element4.hashCode());
        Assertions.assertTrue(element1.hashCode() == element5.hashCode());
    }

    @Test
    public void testEquals() {
        final HeaderElement element1 = new BasicHeaderElement("name", "value",
                new NameValuePair[] {
                    new BasicNameValuePair("param1", "value1"),
                    new BasicNameValuePair("param2", "value2")
                } );
        final HeaderElement element2 = new BasicHeaderElement("name", "value",
                new NameValuePair[] {
                    new BasicNameValuePair("param2", "value2"),
                    new BasicNameValuePair("param1", "value1")
                } );
        final HeaderElement element3 = new BasicHeaderElement("name", "value");
        final HeaderElement element4 = new BasicHeaderElement("name", "value");
        final HeaderElement element5 = new BasicHeaderElement("name", "value",
                new NameValuePair[] {
                    new BasicNameValuePair("param1", "value1"),
                    new BasicNameValuePair("param2", "value2")
                } );
        Assertions.assertTrue(element1.equals(element1));
        Assertions.assertTrue(!element1.equals(element2));
        Assertions.assertTrue(!element1.equals(element3));
        Assertions.assertTrue(!element2.equals(element3));
        Assertions.assertTrue(element3.equals(element4));
        Assertions.assertTrue(element1.equals(element5));
        Assertions.assertFalse(element1.equals(null));
        Assertions.assertFalse(element1.equals("name = value; param1 = value1; param2 = value2"));
    }

    @Test
    public void testToString() {
        String s = "name=value; param1=value1; param2=value2";
        HeaderElement element = BasicHeaderValueParser.parseHeaderElement(s, null);
        Assertions.assertEquals(s, element.toString());
        s = "name; param1=value1; param2=value2";
        element = BasicHeaderValueParser.parseHeaderElement(s, null);
        Assertions.assertEquals(s, element.toString());
    }

    @Test
    public void testCloning() throws Exception {
        final BasicHeaderElement orig = new BasicHeaderElement("name", "value",
                new NameValuePair[] {
                    new BasicNameValuePair("param1", "value1"),
                    new BasicNameValuePair("param2", "value2")
                } );
        final BasicHeaderElement clone = (BasicHeaderElement) orig.clone();
        Assertions.assertEquals(orig, clone);
    }

}
