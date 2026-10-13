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
package org.tamacat.httpcore4.pool;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.tamacat.httpcore4.HttpConnection;
import org.tamacat.httpcore4.fakes.FakeHttpConnection;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestPoolEntry {

    static class MockPoolEntry extends PoolEntry<String, HttpConnection> {

        public MockPoolEntry(final String route,
                final long timeToLive, final TimeUnit timeUnit) {
            super(null, route, new FakeHttpConnection(), timeToLive, timeUnit);
        }

        public MockPoolEntry(final String route, final HttpConnection conn,
                final long timeToLive, final TimeUnit timeUnit) {
            super(null, route, conn, timeToLive, timeUnit);
        }

        @Override
        public void close() {
            try {
                getConnection().close();
            } catch (final IOException ignore) {
            }
        }

        @Override
        public boolean isClosed() {
            return !getConnection().isOpen();
        }

    }

    @Test
    public void testBasics() throws Exception {
        final MockPoolEntry entry1 = new MockPoolEntry("route1", 10L, TimeUnit.MILLISECONDS);
        final long now = System.currentTimeMillis();
        Assertions.assertEquals("route1", entry1.getRoute());
        Assertions.assertTrue(now >= entry1.getCreated());
        Assertions.assertEquals(entry1.getValidityDeadline(), entry1.getExpiry());
        Assertions.assertEquals(entry1.getCreated() + 10L, entry1.getValidityDeadline());
    }

    @Test
    public void testInvalidConstruction() throws Exception {
        try {
            new MockPoolEntry(null, new FakeHttpConnection(), 0L, TimeUnit.MILLISECONDS);
            Assertions.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException expected) {
        }
        try {
            new MockPoolEntry("stuff", null, 0L, TimeUnit.MILLISECONDS);
            Assertions.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException expected) {
        }
        try {
            new MockPoolEntry("stuff", new FakeHttpConnection(), 0L, null);
            Assertions.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException expected) {
        }
    }

    @Test
    public void testValidInfinitely() throws Exception {
        final MockPoolEntry entry1 = new MockPoolEntry("route1", 0L, TimeUnit.MILLISECONDS);
        Assertions.assertEquals(Long.MAX_VALUE, entry1.getValidityDeadline());
        Assertions.assertEquals(entry1.getValidityDeadline(), entry1.getExpiry());
    }

    @Test
    public void testExpiry() throws Exception {
        final MockPoolEntry entry1 = new MockPoolEntry("route1", 0L, TimeUnit.MILLISECONDS);
        Assertions.assertEquals(Long.MAX_VALUE, entry1.getExpiry());
        entry1.updateExpiry(50L, TimeUnit.MILLISECONDS);
        Assertions.assertEquals(entry1.getUpdated() + 50L, entry1.getExpiry());
        entry1.updateExpiry(0L, TimeUnit.MILLISECONDS);
        Assertions.assertEquals(Long.MAX_VALUE, entry1.getExpiry());

        final MockPoolEntry entry2 = new MockPoolEntry("route1", 100L, TimeUnit.MILLISECONDS);
        Assertions.assertEquals(entry2.getCreated() + 100L, entry2.getExpiry());
        entry2.updateExpiry(150L, TimeUnit.MILLISECONDS);
        Assertions.assertEquals(entry2.getCreated() + 100L, entry2.getExpiry());
        entry2.updateExpiry(50L, TimeUnit.MILLISECONDS);
        Assertions.assertEquals(entry2.getUpdated() + 50L, entry2.getExpiry());
    }

    @Test
    public void testInvalidExpiry() throws Exception {
        final MockPoolEntry entry1 = new MockPoolEntry("route1", 0L, TimeUnit.MILLISECONDS);
        Assertions.assertThrows(IllegalArgumentException.class, () -> entry1.updateExpiry(50L, null));
    }

    @Test
    public void testExpiryDoesNotOverflow() {
        final MockPoolEntry entry = new MockPoolEntry("route1", Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        Assertions.assertEquals(entry.getValidityDeadline(), Long.MAX_VALUE);
    }

}
