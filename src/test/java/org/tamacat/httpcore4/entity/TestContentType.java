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

package org.tamacat.httpcore4.entity;

import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;

import org.tamacat.httpcore4.Consts;
import org.tamacat.httpcore4.Header;
import org.tamacat.httpcore4.ParseException;
import org.tamacat.httpcore4.message.BasicHeader;
import org.tamacat.httpcore4.message.BasicNameValuePair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ContentType}.
 *
 */
public class TestContentType {

    @Test
    public void testBasis() throws Exception {
        final ContentType contentType = ContentType.create("text/plain", "US-ASCII");
        Assertions.assertEquals("text/plain", contentType.getMimeType());
        Assertions.assertEquals(Consts.ASCII, contentType.getCharset());
        Assertions.assertEquals("text/plain; charset=US-ASCII", contentType.toString());
    }

    @Test
    public void testWithCharset() throws Exception {
        ContentType contentType = ContentType.create("text/plain", "US-ASCII");
        Assertions.assertEquals("text/plain", contentType.getMimeType());
        Assertions.assertEquals(Consts.ASCII, contentType.getCharset());
        Assertions.assertEquals("text/plain; charset=US-ASCII", contentType.toString());
        contentType = contentType.withCharset(Charset.forName("UTF-8"));
        Assertions.assertEquals("text/plain", contentType.getMimeType());
        Assertions.assertEquals("UTF-8", contentType.getCharset().name());
        Assertions.assertEquals("text/plain; charset=UTF-8", contentType.toString());
    }

    @Test
    public void testWithCharsetString() throws Exception {
        ContentType contentType = ContentType.create("text/plain", "US-ASCII");
        Assertions.assertEquals("text/plain", contentType.getMimeType());
        Assertions.assertEquals(Consts.ASCII, contentType.getCharset());
        Assertions.assertEquals("text/plain; charset=US-ASCII", contentType.toString());
        contentType = contentType.withCharset("UTF-8");
        Assertions.assertEquals("text/plain", contentType.getMimeType());
        Assertions.assertEquals(Consts.UTF_8, contentType.getCharset());
        Assertions.assertEquals("text/plain; charset=UTF-8", contentType.toString());
    }

    @Test
    public void testLowCaseText() throws Exception {
        final ContentType contentType = ContentType.create("Text/Plain", "ascii");
        Assertions.assertEquals("text/plain", contentType.getMimeType());
        Assertions.assertEquals(Consts.ASCII, contentType.getCharset());
    }

    @Test
    public void testCreateInvalidInput() throws Exception {
        try {
            ContentType.create(null, (String) null);
            Assertions.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
        try {
            ContentType.create("  ", (String) null);
            Assertions.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
        try {
            ContentType.create("stuff;", (String) null);
            Assertions.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
        try {
            ContentType.create("text/plain", ",");
            Assertions.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testParse() throws Exception {
        final ContentType contentType = ContentType.parse("text/plain; charset=\"ascii\"");
        Assertions.assertEquals("text/plain", contentType.getMimeType());
        Assertions.assertEquals(Consts.ASCII, contentType.getCharset());
        Assertions.assertEquals("text/plain; charset=ascii", contentType.toString());
    }

    @Test
    public void testParseMultiparam() throws Exception {
        final ContentType contentType = ContentType.parse("text/plain; charset=\"ascii\"; " +
                "p0 ; p1 = \"blah-blah\"  ; p2 = \" yada yada \" ");
        Assertions.assertEquals("text/plain", contentType.getMimeType());
        Assertions.assertEquals(Consts.ASCII, contentType.getCharset());
        Assertions.assertEquals("text/plain; charset=ascii; p0; p1=blah-blah; p2=\" yada yada \"",
                contentType.toString());
        Assertions.assertEquals(null, contentType.getParameter("p0"));
        Assertions.assertEquals("blah-blah", contentType.getParameter("p1"));
        Assertions.assertEquals(" yada yada ", contentType.getParameter("p2"));
        Assertions.assertEquals(null, contentType.getParameter("p3"));
    }

    @Test
    public void testParseEmptyCharset() throws Exception {
        final ContentType contentType = ContentType.parse("text/plain; charset=\" \"");
        Assertions.assertEquals("text/plain", contentType.getMimeType());
        Assertions.assertEquals(null, contentType.getCharset());
    }

    @Test
    public void testParseInvalidInput() throws Exception {
        try {
            ContentType.parse(null);
            Assertions.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
        try {
            ContentType.parse(";");
            Assertions.fail("ParseException should have been thrown");
        } catch (final ParseException ex) {
            // expected
        }
    }

    @Test
    public void testExtractNullInput() throws Exception {
        Assertions.assertNull(ContentType.get(null));
    }

    @Test
    public void testExtractNullContentType() throws Exception {
        final BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContentType((Header)null);
        Assertions.assertNull(ContentType.get(httpentity));
    }

    @Test
    public void testExtract() throws Exception {
        final BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContentType(new BasicHeader("Content-Type", "text/plain; charset = UTF-8"));
        final ContentType contentType = ContentType.get(httpentity);
        Assertions.assertNotNull(contentType);
        Assertions.assertEquals("text/plain", contentType.getMimeType());
        Assertions.assertEquals(Consts.UTF_8, contentType.getCharset());
    }

    @Test
    public void testExtractNoCharset() throws Exception {
        final BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContentType(new BasicHeader("Content-Type", "text/plain; param=yadayada"));
        final ContentType contentType = ContentType.get(httpentity);
        Assertions.assertNotNull(contentType);
        Assertions.assertEquals("text/plain", contentType.getMimeType());
        Assertions.assertNull(contentType.getCharset());
    }

    @Test
    public void testExtractInvalidCharset() throws Exception {
        final BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContentType(new BasicHeader("Content-Type", "text/plain; charset = stuff"));
        Assertions.assertThrows(UnsupportedCharsetException.class, () -> ContentType.get(httpentity));
    }

    @Test
    public void testExtracLenienttNullInput() throws Exception {
        Assertions.assertNull(ContentType.getLenient(null));
    }

    @Test
    public void testExtractLenientNullContentType() throws Exception {
        final BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContentType((Header) null);
        Assertions.assertNull(ContentType.getLenient(httpentity));
    }

    @Test
    public void testLenientExtractInvalidCharset() throws Exception {
        final BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContentType(new BasicHeader("Content-Type", "text/plain; charset = stuff"));
        final ContentType contentType = ContentType.getLenient(httpentity);
        Assertions.assertNotNull(contentType);
        Assertions.assertEquals("text/plain", contentType.getMimeType());
        Assertions.assertEquals(null, contentType.getCharset());
    }

    @Test
    public void testWithParams() throws Exception {
        ContentType contentType = ContentType.create("text/plain",
                new BasicNameValuePair("charset", "UTF-8"),
                new BasicNameValuePair("p", "this"),
                new BasicNameValuePair("p", "that"));
        Assertions.assertEquals("text/plain", contentType.getMimeType());
        Assertions.assertEquals(Consts.UTF_8, contentType.getCharset());
        Assertions.assertEquals("text/plain; charset=UTF-8; p=this; p=that", contentType.toString());

        contentType = contentType.withParameters(
                new BasicNameValuePair("charset", "ascii"),
                new BasicNameValuePair("p", "this and that"));
        Assertions.assertEquals("text/plain", contentType.getMimeType());
        Assertions.assertEquals(Consts.ASCII, contentType.getCharset());
        Assertions.assertEquals("text/plain; charset=ascii; p=\"this and that\"", contentType.toString());

        contentType = ContentType.create("text/blah").withParameters(
                new BasicNameValuePair("p", "blah"));
        Assertions.assertEquals("text/blah", contentType.getMimeType());
        Assertions.assertEquals(null, contentType.getCharset());
        Assertions.assertEquals("text/blah; p=blah", contentType.toString());

        contentType = ContentType.create("text/blah", Consts.ISO_8859_1).withParameters(
                new BasicNameValuePair("p", "blah"));
        Assertions.assertEquals("text/blah", contentType.getMimeType());
        Assertions.assertEquals(Consts.ISO_8859_1, contentType.getCharset());
        Assertions.assertEquals("text/blah; charset=ISO-8859-1; p=blah", contentType.toString());
    }

}
