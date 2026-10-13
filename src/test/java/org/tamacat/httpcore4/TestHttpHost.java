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

package org.tamacat.httpcore4;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link HttpHost}.
 *
 */
public class TestHttpHost {

    @Test
    public void testConstructor() {
        final HttpHost host1 = new HttpHost("somehost");
        Assertions.assertEquals("somehost", host1.getHostName());
        Assertions.assertEquals(-1, host1.getPort());
        Assertions.assertEquals("http", host1.getSchemeName());
        final HttpHost host2 = new HttpHost("somehost", 8080);
        Assertions.assertEquals("somehost", host2.getHostName());
        Assertions.assertEquals(8080, host2.getPort());
        Assertions.assertEquals("http", host2.getSchemeName());
        final HttpHost host3 = new HttpHost("somehost", -1);
        Assertions.assertEquals("somehost", host3.getHostName());
        Assertions.assertEquals(-1, host3.getPort());
        Assertions.assertEquals("http", host3.getSchemeName());
        final HttpHost host4 = new HttpHost("somehost", 443, "https");
        Assertions.assertEquals("somehost", host4.getHostName());
        Assertions.assertEquals(443, host4.getPort());
        Assertions.assertEquals("https", host4.getSchemeName());
        try {
            new HttpHost((String) null, -1, null);
            Assertions.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException expected) {
        }
        try {
            new HttpHost("   ", -1, null);
            Assertions.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException expected) {
        }
        try {
            new HttpHost((InetAddress) null, -1, null);
            Assertions.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException expected) {
        }
    }

    @Test
    public void testHashCode() throws Exception {
        final HttpHost host1 = new HttpHost("somehost", 8080, "http");
        final HttpHost host2 = new HttpHost("somehost", 80, "http");
        final HttpHost host3 = new HttpHost("someotherhost", 8080, "http");
        final HttpHost host4 = new HttpHost("somehost", 80, "http");
        final HttpHost host5 = new HttpHost("SomeHost", 80, "http");
        final HttpHost host6 = new HttpHost("SomeHost", 80, "myhttp");
        final HttpHost host7 = new HttpHost(
                InetAddress.getByAddress("127.0.0.1", new byte[] {127,0,0,1}), 80, "http");
        final HttpHost host8 = new HttpHost("127.0.0.1", 80, "http");
        final HttpHost host9 = new HttpHost(
                        InetAddress.getByAddress("somehost",new byte[] {127,0,0,1}), 80, "http");
        final HttpHost host10 = new HttpHost(
                        InetAddress.getByAddress(new byte[] {127,0,0,1}), "somehost", 80, "http");
        final HttpHost host11 = new HttpHost(
                        InetAddress.getByAddress("someotherhost",new byte[] {127,0,0,1}), 80, "http");

        Assertions.assertTrue(host1.hashCode() == host1.hashCode());
        Assertions.assertTrue(host1.hashCode() != host2.hashCode());
        Assertions.assertTrue(host1.hashCode() != host3.hashCode());
        Assertions.assertTrue(host2.hashCode() == host4.hashCode());
        Assertions.assertTrue(host2.hashCode() == host5.hashCode());
        Assertions.assertTrue(host5.hashCode() != host6.hashCode());
        Assertions.assertTrue(host7.hashCode() != host8.hashCode());
        Assertions.assertTrue(host8.hashCode() != host9.hashCode());
        Assertions.assertTrue(host9.hashCode() == host10.hashCode());
        Assertions.assertTrue(host10.hashCode() != host11.hashCode());
        Assertions.assertTrue(host9.hashCode() != host11.hashCode());
    }

    @Test
    public void testEquals() throws Exception {
        final HttpHost host1 = new HttpHost("somehost", 8080, "http");
        final HttpHost host2 = new HttpHost("somehost", 80, "http");
        final HttpHost host3 = new HttpHost("someotherhost", 8080, "http");
        final HttpHost host4 = new HttpHost("somehost", 80, "http");
        final HttpHost host5 = new HttpHost("SomeHost", 80, "http");
        final HttpHost host6 = new HttpHost("SomeHost", 80, "myhttp");
        final HttpHost host7 = new HttpHost(
                InetAddress.getByAddress("127.0.0.1", new byte[] {127,0,0,1}), 80, "http");
        final HttpHost host8 = new HttpHost("127.0.0.1", 80, "http");
        final HttpHost host9 = new HttpHost(
                        InetAddress.getByAddress("somehost", new byte[] {127,0,0,1}), 80, "http");
        final HttpHost host10 = new HttpHost(
                        InetAddress.getByAddress(new byte[] {127,0,0,1}), "somehost", 80, "http");
        final HttpHost host11 = new HttpHost(
                        InetAddress.getByAddress("someotherhost",new byte[] {127,0,0,1}), 80, "http");

        Assertions.assertTrue(host1.equals(host1));
        Assertions.assertFalse(host1.equals(host2));
        Assertions.assertFalse(host1.equals(host3));
        Assertions.assertTrue(host2.equals(host4));
        Assertions.assertTrue(host2.equals(host5));
        Assertions.assertFalse(host5.equals(host6));
        Assertions.assertFalse(host7.equals(host8));
        Assertions.assertTrue(!host7.equals(host9));
        Assertions.assertFalse(host1.equals(null));
        Assertions.assertFalse(host1.equals("http://somehost"));
        Assertions.assertFalse(host9.equals("http://somehost"));
        Assertions.assertFalse(host8.equals(host9));
        Assertions.assertTrue(host9.equals(host10));
        Assertions.assertFalse(host9.equals(host11));
    }

    @Test
    public void testToString() throws Exception {
        final HttpHost host1 = new HttpHost("somehost");
        Assertions.assertEquals("http://somehost", host1.toString());
        final HttpHost host2 = new HttpHost("somehost", -1);
        Assertions.assertEquals("http://somehost", host2.toString());
        final HttpHost host3 = new HttpHost("somehost", -1);
        Assertions.assertEquals("http://somehost", host3.toString());
        final HttpHost host4 = new HttpHost("somehost", 8888);
        Assertions.assertEquals("http://somehost:8888", host4.toString());
        final HttpHost host5 = new HttpHost("somehost", -1, "myhttp");
        Assertions.assertEquals("myhttp://somehost", host5.toString());
        final HttpHost host6 = new HttpHost("somehost", 80, "myhttp");
        Assertions.assertEquals("myhttp://somehost:80", host6.toString());
        final HttpHost host7 = new HttpHost(
                InetAddress.getByAddress("127.0.0.1", new byte[] {127,0,0,1}), 80, "http");
        Assertions.assertEquals("http://127.0.0.1:80", host7.toString());
        final HttpHost host9 = new HttpHost(
                        InetAddress.getByAddress("somehost", new byte[] {127,0,0,1}), 80, "http");
        Assertions.assertEquals("http://somehost:80", host9.toString());
    }

    @Test
    public void testToHostString() {
        final HttpHost host1 = new HttpHost("somehost");
        Assertions.assertEquals("somehost", host1.toHostString());
        final HttpHost host2 = new HttpHost("somehost");
        Assertions.assertEquals("somehost", host2.toHostString());
        final HttpHost host3 = new HttpHost("somehost", -1);
        Assertions.assertEquals("somehost", host3.toHostString());
        final HttpHost host4 = new HttpHost("somehost", 8888);
        Assertions.assertEquals("somehost:8888", host4.toHostString());
    }

    @Test
    public void testCloning() throws Exception {
        final HttpHost orig = new HttpHost("somehost", 8080, "https");
        final HttpHost clone = (HttpHost) orig.clone();
        Assertions.assertEquals(orig, clone);
    }

    @Test
    public void testSerialization() throws Exception {
        final HttpHost orig = new HttpHost("somehost", 8080, "https");
        final ByteArrayOutputStream outbuffer = new ByteArrayOutputStream();
        final ObjectOutputStream outStream = new ObjectOutputStream(outbuffer);
        outStream.writeObject(orig);
        outStream.close();
        final byte[] raw = outbuffer.toByteArray();
        final ByteArrayInputStream inBuffer = new ByteArrayInputStream(raw);
        final ObjectInputStream inStream = new ObjectInputStream(inBuffer);
        final HttpHost clone = (HttpHost) inStream.readObject();
        Assertions.assertEquals(orig, clone);
    }

    @Test
    public void testCreateFromString() throws Exception {
        Assertions.assertEquals(new HttpHost("somehost", 8080, "https"), HttpHost.create("https://somehost:8080"));
        Assertions.assertEquals(new HttpHost("somehost", 8080, "https"), HttpHost.create("HttpS://SomeHost:8080"));
        Assertions.assertEquals(new HttpHost("somehost", 1234, null), HttpHost.create("somehost:1234"));
        Assertions.assertEquals(new HttpHost("somehost", -1, null), HttpHost.create("somehost"));
    }

    @Test
    public void testCreateFromStringInvalid() throws Exception {
        try {
            HttpHost.create(null);
            Assertions.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException expected) {
        }
        try {
            HttpHost.create(" host ");
            Assertions.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException expected) {
        }
        try {
            HttpHost.create("host :8080");
            Assertions.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException expected) {
        }
        try {
            HttpHost.create("");
            Assertions.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException expected) {
        }
    }

}
