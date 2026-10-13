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

import org.tamacat.httpcore4.HttpConnection;
import org.tamacat.httpcore4.fakes.FakeHttpConnection;
import org.tamacat.httpcore4.fakes.RecordingFuture;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestRouteSpecificPool {

    private static final String ROUTE = "whatever";

    static class LocalPoolEntry extends PoolEntry<String, HttpConnection> {

        public LocalPoolEntry(final String route, final HttpConnection conn) {
            super(null, route, conn);
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

    static class LocalRoutePool extends RouteSpecificPool<String, HttpConnection, LocalPoolEntry> {

        public LocalRoutePool() {
            super(ROUTE);
        }

        @Override
        protected LocalPoolEntry createEntry(final HttpConnection conn) {
            return new LocalPoolEntry(getRoute(), conn);
        }

    }

    @Test
    public void testEmptyPool() throws Exception {
        final LocalRoutePool pool = new LocalRoutePool();
        Assertions.assertEquals(ROUTE, pool.getRoute());
        Assertions.assertEquals(0, pool.getAllocatedCount());
        Assertions.assertEquals(0, pool.getAvailableCount());
        Assertions.assertEquals(0, pool.getLeasedCount());
        Assertions.assertEquals(0, pool.getPendingCount());
        Assertions.assertNull(pool.getLastUsed());
        Assertions.assertEquals("[route: whatever][leased: 0][available: 0][pending: 0]", pool.toString());
    }

    @Test
    public void testAdd() throws Exception {
        final LocalRoutePool pool = new LocalRoutePool();
        final FakeHttpConnection conn = new FakeHttpConnection();
        final PoolEntry<String, HttpConnection> entry = pool.add(conn);
        Assertions.assertEquals(1, pool.getAllocatedCount());
        Assertions.assertEquals(0, pool.getAvailableCount());
        Assertions.assertEquals(1, pool.getLeasedCount());
        Assertions.assertEquals(0, pool.getPendingCount());
        Assertions.assertNotNull(entry);
        Assertions.assertSame(conn, entry.getConnection());
    }

    @Test
    public void testLeaseRelease() throws Exception {
        final LocalRoutePool pool = new LocalRoutePool();
        final FakeHttpConnection conn1 = new FakeHttpConnection();
        final LocalPoolEntry entry1 = pool.add(conn1);
        final FakeHttpConnection conn2 = new FakeHttpConnection();
        final LocalPoolEntry entry2 = pool.add(conn2);
        final FakeHttpConnection conn3 = new FakeHttpConnection();
        final LocalPoolEntry entry3 = pool.add(conn3);

        Assertions.assertNotNull(entry1);
        Assertions.assertNotNull(entry2);
        Assertions.assertNotNull(entry3);

        Assertions.assertEquals(3, pool.getAllocatedCount());
        Assertions.assertEquals(0, pool.getAvailableCount());
        Assertions.assertEquals(3, pool.getLeasedCount());
        Assertions.assertEquals(0, pool.getPendingCount());

        pool.free(entry1, true);
        pool.free(entry2, false);
        pool.free(entry3, true);

        Assertions.assertEquals(2, pool.getAllocatedCount());
        Assertions.assertEquals(2, pool.getAvailableCount());
        Assertions.assertEquals(0, pool.getLeasedCount());
        Assertions.assertEquals(0, pool.getPendingCount());

        Assertions.assertSame(entry1, pool.getLastUsed());

        Assertions.assertNotNull(pool.getFree(null));
        Assertions.assertNotNull(pool.getFree(null));
        Assertions.assertNull(pool.getFree(null));

        Assertions.assertEquals(2, pool.getAllocatedCount());
        Assertions.assertEquals(0, pool.getAvailableCount());
        Assertions.assertEquals(2, pool.getLeasedCount());
        Assertions.assertEquals(0, pool.getPendingCount());
    }

    @Test
    public void testLeaseOrder() throws Exception {
        final LocalRoutePool pool = new LocalRoutePool();
        final FakeHttpConnection conn1 = new FakeHttpConnection();
        final LocalPoolEntry entry1 = pool.add(conn1);
        final FakeHttpConnection conn2 = new FakeHttpConnection();
        final LocalPoolEntry entry2 = pool.add(conn2);
        final FakeHttpConnection conn3 = new FakeHttpConnection();
        final LocalPoolEntry entry3 = pool.add(conn3);

        Assertions.assertNotNull(entry1);
        Assertions.assertNotNull(entry2);
        Assertions.assertNotNull(entry3);

        Assertions.assertEquals(3, pool.getAllocatedCount());
        Assertions.assertEquals(0, pool.getAvailableCount());
        Assertions.assertEquals(3, pool.getLeasedCount());
        Assertions.assertEquals(0, pool.getPendingCount());

        pool.free(entry1, true);
        pool.free(entry2, true);
        pool.free(entry3, true);

        Assertions.assertSame(entry1, pool.getLastUsed());

        Assertions.assertSame(entry3, pool.getFree(null));
        Assertions.assertSame(entry2, pool.getFree(null));
        Assertions.assertSame(entry1, pool.getFree(null));
    }

    @Test
    public void testLeaseReleaseStateful() throws Exception {
        final LocalRoutePool pool = new LocalRoutePool();
        final FakeHttpConnection conn1 = new FakeHttpConnection();
        final LocalPoolEntry entry1 = pool.add(conn1);
        final FakeHttpConnection conn2 = new FakeHttpConnection();
        final LocalPoolEntry entry2 = pool.add(conn2);
        final FakeHttpConnection conn3 = new FakeHttpConnection();
        final LocalPoolEntry entry3 = pool.add(conn3);

        Assertions.assertNotNull(entry1);
        Assertions.assertNotNull(entry2);
        Assertions.assertNotNull(entry3);

        Assertions.assertEquals(3, pool.getAllocatedCount());
        Assertions.assertEquals(0, pool.getAvailableCount());
        Assertions.assertEquals(3, pool.getLeasedCount());
        Assertions.assertEquals(0, pool.getPendingCount());

        entry2.setState(Boolean.FALSE);
        pool.free(entry1, true);
        pool.free(entry2, true);
        pool.free(entry3, true);

        Assertions.assertSame(entry2, pool.getFree(Boolean.FALSE));
        Assertions.assertSame(entry3, pool.getFree(Boolean.FALSE));
        Assertions.assertSame(entry1, pool.getFree(null));
        Assertions.assertSame(null, pool.getFree(null));

        entry1.setState(Boolean.TRUE);
        entry2.setState(Boolean.FALSE);
        entry3.setState(Boolean.TRUE);
        pool.free(entry1, true);
        pool.free(entry2, true);
        pool.free(entry3, true);

        Assertions.assertSame(null, pool.getFree(null));
        Assertions.assertSame(entry2, pool.getFree(Boolean.FALSE));
        Assertions.assertSame(null, pool.getFree(Boolean.FALSE));
        Assertions.assertSame(entry3, pool.getFree(Boolean.TRUE));
        Assertions.assertSame(entry1, pool.getFree(Boolean.TRUE));
        Assertions.assertSame(null, pool.getFree(Boolean.TRUE));
    }

    @Test
    public void testReleaseInvalidEntry() throws Exception {
        final LocalRoutePool pool = new LocalRoutePool();
        final FakeHttpConnection conn = new FakeHttpConnection();
        final LocalPoolEntry entry = new LocalPoolEntry(ROUTE, conn);
        Assertions.assertThrows(IllegalStateException.class, () -> pool.free(entry, true));
    }

    @Test
    public void testRemove() throws Exception {
        final LocalRoutePool pool = new LocalRoutePool();
        final FakeHttpConnection conn1 = new FakeHttpConnection();
        final LocalPoolEntry entry1 = pool.add(conn1);
        final FakeHttpConnection conn2 = new FakeHttpConnection();
        final LocalPoolEntry entry2 = pool.add(conn2);
        final FakeHttpConnection conn3 = new FakeHttpConnection();
        final LocalPoolEntry entry3 = pool.add(conn3);

        Assertions.assertNotNull(entry1);
        Assertions.assertNotNull(entry2);
        Assertions.assertNotNull(entry3);

        Assertions.assertEquals(3, pool.getAllocatedCount());
        Assertions.assertEquals(0, pool.getAvailableCount());
        Assertions.assertEquals(3, pool.getLeasedCount());
        Assertions.assertEquals(0, pool.getPendingCount());

        Assertions.assertTrue(pool.remove(entry2));
        Assertions.assertFalse(pool.remove(entry2));

        Assertions.assertEquals(2, pool.getAllocatedCount());
        Assertions.assertEquals(0, pool.getAvailableCount());
        Assertions.assertEquals(2, pool.getLeasedCount());
        Assertions.assertEquals(0, pool.getPendingCount());

        pool.free(entry1, true);
        pool.free(entry3, true);

        Assertions.assertEquals(2, pool.getAllocatedCount());
        Assertions.assertEquals(2, pool.getAvailableCount());
        Assertions.assertEquals(0, pool.getLeasedCount());
        Assertions.assertEquals(0, pool.getPendingCount());

        Assertions.assertTrue(pool.remove(entry1));
        Assertions.assertTrue(pool.remove(entry3));

        Assertions.assertEquals(0, pool.getAllocatedCount());
        Assertions.assertEquals(0, pool.getAvailableCount());
        Assertions.assertEquals(0, pool.getLeasedCount());
        Assertions.assertEquals(0, pool.getPendingCount());
    }

    @Test
    public void testReleaseInvalid() throws Exception {
        final LocalRoutePool pool = new LocalRoutePool();
        Assertions.assertThrows(IllegalArgumentException.class, () -> pool.free(null, true));
    }

    @Test
    public void testRemoveInvalid() throws Exception {
        final LocalRoutePool pool = new LocalRoutePool();
        Assertions.assertThrows(IllegalArgumentException.class, () -> pool.remove(null));
    }

    @Test
    public void testWaitingThreadQueuing() throws Exception {
        final LocalRoutePool pool = new LocalRoutePool();
        final RecordingFuture<LocalPoolEntry> future1 = new RecordingFuture<LocalPoolEntry>();
        final RecordingFuture<LocalPoolEntry> future2 = new RecordingFuture<LocalPoolEntry>();

        Assertions.assertEquals(0, pool.getPendingCount());
        pool.queue(future1);
        Assertions.assertEquals(1, pool.getPendingCount());
        pool.queue(null);
        Assertions.assertEquals(1, pool.getPendingCount());
        pool.queue(future2);
        Assertions.assertEquals(2, pool.getPendingCount());
        Assertions.assertSame(future1, pool.nextPending());
        pool.unqueue(future1);
        Assertions.assertEquals(1, pool.getPendingCount());
        Assertions.assertSame(future2, pool.nextPending());
        pool.unqueue(null);
        Assertions.assertEquals(0, pool.getPendingCount());
        pool.unqueue(future2);
        Assertions.assertNull(pool.nextPending());
    }

    @Test
    public void testShutdown() throws Exception {
        final LocalRoutePool pool = new LocalRoutePool();
        final FakeHttpConnection conn1 = new FakeHttpConnection();
        final LocalPoolEntry entry1 = pool.add(conn1);
        final FakeHttpConnection conn2 = new FakeHttpConnection();
        final LocalPoolEntry entry2 = pool.add(conn2);

        final RecordingFuture<LocalPoolEntry> future1 = new RecordingFuture<LocalPoolEntry>();
        pool.queue(future1);

        Assertions.assertNotNull(entry1);
        Assertions.assertNotNull(entry2);

        pool.free(entry1, true);

        Assertions.assertEquals(2, pool.getAllocatedCount());
        Assertions.assertEquals(1, pool.getAvailableCount());
        Assertions.assertEquals(1, pool.getLeasedCount());
        Assertions.assertEquals(1, pool.getPendingCount());

        pool.shutdown();

        Assertions.assertEquals(0, pool.getAllocatedCount());
        Assertions.assertEquals(0, pool.getAvailableCount());
        Assertions.assertEquals(0, pool.getLeasedCount());
        Assertions.assertEquals(0, pool.getPendingCount());

        future1.calls.assertCalledOnce("cancel", true);
        conn2.calls.assertCalledOnce("close");
        conn1.calls.assertCalledOnce("close");
    }

}
