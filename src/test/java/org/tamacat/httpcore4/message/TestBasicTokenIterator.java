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

import java.util.NoSuchElementException;

import org.tamacat.httpcore4.Header;
import org.tamacat.httpcore4.HeaderIterator;
import org.tamacat.httpcore4.ParseException;
import org.tamacat.httpcore4.TokenIterator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


/**
 * Tests for {@link BasicTokenIterator}.
 *
 */
public class TestBasicTokenIterator {

    @Test
    public void testSingleHeader() {
        Header[] headers = new Header[]{
            new BasicHeader("Name", "token0,token1, token2 , token3")
        };
        HeaderIterator hit = new BasicHeaderIterator(headers, null);
        TokenIterator  ti  = new BasicTokenIterator(hit);

        Assertions.assertTrue(ti.hasNext());
        Assertions.assertEquals("token0", ti.nextToken(), "token0");
        Assertions.assertTrue(ti.hasNext());
        Assertions.assertEquals("token1", ti.nextToken(), "token1");
        Assertions.assertTrue(ti.hasNext());
        Assertions.assertEquals("token2", ti.nextToken(), "token2");
        Assertions.assertTrue(ti.hasNext());
        Assertions.assertEquals("token3", ti.nextToken(), "token3");
        Assertions.assertFalse(ti.hasNext());


        headers = new Header[]{
            new BasicHeader("Name", "token0")
        };
        hit = new BasicHeaderIterator(headers, null);
        ti  = new BasicTokenIterator(hit);

        Assertions.assertTrue(ti.hasNext());
        Assertions.assertEquals("token0", ti.nextToken(), "token0");
        Assertions.assertFalse(ti.hasNext());
    }


    @Test
    public void testMultiHeader() {
        final Header[] headers = new Header[]{
            new BasicHeader("Name", "token0,token1"),
            new BasicHeader("Name", ""),
            new BasicHeader("Name", "token2"),
            new BasicHeader("Name", " "),
            new BasicHeader("Name", "token3 "),
            new BasicHeader("Name", ","),
            new BasicHeader("Name", "token4"),
        };
        final HeaderIterator hit = new BasicHeaderIterator(headers, null);
        final TokenIterator  ti  = new BasicTokenIterator(hit);

        Assertions.assertTrue(ti.hasNext());
        Assertions.assertEquals("token0", ti.nextToken(), "token0");
        Assertions.assertTrue(ti.hasNext());
        Assertions.assertEquals("token1", ti.nextToken(), "token1");
        Assertions.assertTrue(ti.hasNext());
        Assertions.assertEquals("token2", ti.nextToken(), "token2");
        Assertions.assertTrue(ti.hasNext());
        Assertions.assertEquals("token3", ti.nextToken(), "token3");
        Assertions.assertTrue(ti.hasNext());
        Assertions.assertEquals("token4", ti.nextToken(), "token4");
        Assertions.assertFalse(ti.hasNext());
    }


    @Test
    public void testEmpty() {
        final Header[] headers = new Header[]{
            new BasicHeader("Name", " "),
            new BasicHeader("Name", ""),
            new BasicHeader("Name", ","),
            new BasicHeader("Name", " ,, "),
        };
        HeaderIterator hit = new BasicHeaderIterator(headers, null);
        TokenIterator  ti  = new BasicTokenIterator(hit);

        Assertions.assertFalse(ti.hasNext());


        hit = new BasicHeaderIterator(headers, "empty");
        ti  = new BasicTokenIterator(hit);

        Assertions.assertFalse(ti.hasNext());
    }


    @Test
    public void testValueStart() {
        final Header[] headers = new Header[]{
            new BasicHeader("Name", "token0"),
            new BasicHeader("Name", " token1"),
            new BasicHeader("Name", ",token2"),
            new BasicHeader("Name", " ,token3"),
            new BasicHeader("Name", ", token4"),
            new BasicHeader("Name", " , token5"),
        };
        final HeaderIterator hit = new BasicHeaderIterator(headers, null);
        final TokenIterator  ti  = new BasicTokenIterator(hit);

        Assertions.assertTrue(ti.hasNext());
        Assertions.assertEquals("token0", ti.nextToken(), "token0");
        Assertions.assertTrue(ti.hasNext());
        Assertions.assertEquals("token1", ti.nextToken(), "token1");
        Assertions.assertTrue(ti.hasNext());
        Assertions.assertEquals("token2", ti.nextToken(), "token2");
        Assertions.assertTrue(ti.hasNext());
        Assertions.assertEquals("token3", ti.nextToken(), "token3");
        Assertions.assertTrue(ti.hasNext());
        Assertions.assertEquals("token4", ti.nextToken(), "token4");
        Assertions.assertTrue(ti.hasNext());
        Assertions.assertEquals("token5", ti.nextToken(), "token5");
        Assertions.assertFalse(ti.hasNext());
    }


    @Test
    public void testValueEnd() {
        final Header[] headers = new Header[]{
            new BasicHeader("Name", "token0"),
            new BasicHeader("Name", "token1 "),
            new BasicHeader("Name", "token2,"),
            new BasicHeader("Name", "token3 ,"),
            new BasicHeader("Name", "token4, "),
            new BasicHeader("Name", "token5 , "),
        };
        final HeaderIterator hit = new BasicHeaderIterator(headers, null);
        final TokenIterator  ti  = new BasicTokenIterator(hit);

        Assertions.assertTrue(ti.hasNext());
        Assertions.assertEquals("token0", ti.nextToken(), "token0");
        Assertions.assertTrue(ti.hasNext());
        Assertions.assertEquals("token1", ti.nextToken(), "token1");
        Assertions.assertTrue(ti.hasNext());
        Assertions.assertEquals("token2", ti.nextToken(), "token2");
        Assertions.assertTrue(ti.hasNext());
        Assertions.assertEquals("token3", ti.nextToken(), "token3");
        Assertions.assertTrue(ti.hasNext());
        Assertions.assertEquals("token4", ti.nextToken(), "token4");
        Assertions.assertTrue(ti.hasNext());
        Assertions.assertEquals("token5", ti.nextToken(), "token5");
        Assertions.assertFalse(ti.hasNext());
    }


    @Test
    public void testTokenChar() {
        final Header[] headers = new Header[]{
            new BasicHeader("Name", "token0")
        };
        final HeaderIterator     hit = new BasicHeaderIterator(headers, null);
        final BasicTokenIterator bti = new BasicTokenIterator(hit);

        Assertions.assertTrue (bti.isTokenChar('j'), "letter");
        Assertions.assertFalse(bti.isTokenChar('\b'), "control");
        Assertions.assertFalse(bti.isTokenChar('?'), "separator");
        Assertions.assertTrue (bti.isTokenChar('-'), "other");
    }


    @Test
    public void testInvalid() {
        final Header[] headers = new Header[]{
            new BasicHeader("in", "token0=token1"),
            new BasicHeader("no", "token0 token1"),
            new BasicHeader("pre", "<token0,token1"),
            new BasicHeader("post", "token0,token1="),
        };
        HeaderIterator hit = new BasicHeaderIterator(headers, "in");
        TokenIterator  ti  = new BasicTokenIterator(hit);

        // constructor located token0
        Assertions.assertTrue(ti.hasNext());
        try {
            ti.nextToken();
            Assertions.fail("invalid infix character not detected");
        } catch (final ParseException px) {
            // expected
        }


        // constructor located token0
        hit = new BasicHeaderIterator(headers, "no");
        ti  = new BasicTokenIterator(hit);
        Assertions.assertTrue(ti.hasNext());
        try {
            ti.nextToken();
            Assertions.fail("missing token separator not detected");
        } catch (final ParseException px) {
            // expected
        }


        // constructor seeks for the first token
        hit = new BasicHeaderIterator(headers, "pre");
        try {
            new BasicTokenIterator(hit);
            Assertions.fail("invalid prefix character not detected");
        } catch (final ParseException px) {
            // expected
        }


        hit = new BasicHeaderIterator(headers, "post");
        ti  = new BasicTokenIterator(hit);

        Assertions.assertTrue(ti.hasNext());
        Assertions.assertEquals("token0", ti.nextToken(), "token0");
        Assertions.assertTrue(ti.hasNext());
        // failure after the last must not go unpunished
        try {
            ti.nextToken();
            Assertions.fail("invalid postfix character not detected");
        } catch (final ParseException px) {
            // expected
        }
    }


    @Test
    public void testWrongPublic() {

        try {
            new BasicTokenIterator(null);
            Assertions.fail("null argument not detected");
        } catch (final IllegalArgumentException iax) {
            // expected
        }

        final Header[] headers = new Header[]{
            new BasicHeader("Name", " "),
            new BasicHeader("Name", ""),
            new BasicHeader("Name", ","),
            new BasicHeader("Name", " ,, "),
        };
        final HeaderIterator hit = new BasicHeaderIterator(headers, null);
        final TokenIterator  ti  = new BasicTokenIterator(hit);

        try {
            // call next() instead of nextToken() to get that covered, too
            ti.next();
            Assertions.fail("next after end not detected");
        } catch (final NoSuchElementException nsx) {
            // expected
        }

        try {
            ti.remove();
            Assertions.fail("unsupported remove not detected");
        } catch (final UnsupportedOperationException uox) {
            // expected
        }
    }


    @Test
    public void testWrongProtected() {

        final Header[] headers = new Header[]{
            new BasicHeader("Name", "token1,token2")
        };
        final HeaderIterator     hit = new BasicHeaderIterator(headers, null);
        final BasicTokenIterator bti = new BasicTokenIterator(hit);

        try {
            bti.findTokenStart(-1);
            Assertions.fail("tokenStart: negative index not detected");
        } catch (final IllegalArgumentException iax) {
            // expected
        }

        try {
            bti.findTokenSeparator(-1);
            Assertions.fail("tokenSeparator: negative index not detected");
        } catch (final IllegalArgumentException iax) {
            // expected
        }

        try {
            bti.findTokenEnd(-1);
            Assertions.fail("tokenEnd: negative index not detected");
        } catch (final IllegalArgumentException iax) {
            // expected
        }
    }

}
