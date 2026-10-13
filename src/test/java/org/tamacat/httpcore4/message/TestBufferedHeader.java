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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.tamacat.httpcore4.HeaderElement;
import org.tamacat.httpcore4.util.CharArrayBuffer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link BufferedHeader}.
 *
 */
public class TestBufferedHeader {

    @Test
    public void testBasicConstructor() {
        final CharArrayBuffer buf = new CharArrayBuffer(32);
        buf.append("name: value");
        final BufferedHeader header = new BufferedHeader(buf);
        Assertions.assertEquals("name", header.getName());
        Assertions.assertEquals("value", header.getValue());
        Assertions.assertSame(buf, header.getBuffer());
        Assertions.assertEquals(5, header.getValuePos());
    }

    @Test
    public void testInvalidName() {
        try {
            new BufferedHeader(null);
            Assertions.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            //expected
        }
    }

    @Test
    public void testHeaderElements() {
        final CharArrayBuffer buf = new CharArrayBuffer(32);
        buf.append("name: element1 = value1, element2; param1 = value1, element3");
        final BufferedHeader header = new BufferedHeader(buf);
        final HeaderElement[] elements = header.getElements();
        Assertions.assertNotNull(elements);
        Assertions.assertEquals(3, elements.length);
        Assertions.assertEquals("element1", elements[0].getName());
        Assertions.assertEquals("value1", elements[0].getValue());
        Assertions.assertEquals("element2", elements[1].getName());
        Assertions.assertEquals(null, elements[1].getValue());
        Assertions.assertEquals("element3", elements[2].getName());
        Assertions.assertEquals(null, elements[2].getValue());
        Assertions.assertEquals(1, elements[1].getParameters().length);
    }

    @Test
    public void testCloning() throws Exception {
        final CharArrayBuffer buf = new CharArrayBuffer(32);
        buf.append("name: value");
        final BufferedHeader orig = new BufferedHeader(buf);
        final BufferedHeader clone = (BufferedHeader) orig.clone();
        Assertions.assertEquals(orig.getName(), clone.getName());
        Assertions.assertEquals(orig.getValue(), clone.getValue());
    }

    @Test
    public void testSerialization() throws Exception {
        final CharArrayBuffer buf = new CharArrayBuffer(32);
        buf.append("name: value");
        final BufferedHeader orig = new BufferedHeader(buf);
        final ByteArrayOutputStream outbuffer = new ByteArrayOutputStream();
        final ObjectOutputStream outStream = new ObjectOutputStream(outbuffer);
        outStream.writeObject(orig);
        outStream.close();
        final byte[] raw = outbuffer.toByteArray();
        final ByteArrayInputStream inBuffer = new ByteArrayInputStream(raw);
        final ObjectInputStream inStream = new ObjectInputStream(inBuffer);
        final BufferedHeader clone = (BufferedHeader) inStream.readObject();
        Assertions.assertEquals(orig.getName(), clone.getName());
        Assertions.assertEquals(orig.getValue(), clone.getValue());
    }

}

