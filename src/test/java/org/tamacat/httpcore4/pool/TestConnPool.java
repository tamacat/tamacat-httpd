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
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.tamacat.httpcore4.HttpConnection;
import org.tamacat.httpcore4.fakes.CallLog;
import org.tamacat.httpcore4.fakes.FakeConnFactory;
import org.tamacat.httpcore4.fakes.FakeHttpConnection;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestConnPool {

    private static final int GRACE_PERIOD = 10000;

    static class LocalConnFactory extends FakeConnFactory<String, HttpConnection> {
    }

    static class LocalPoolEntry extends PoolEntry<String, HttpConnection> {

        private boolean closed;

        public LocalPoolEntry(final String route, final HttpConnection conn) {
            super(null, route, conn);
        }

        @Override
        public void close() {
            if (this.closed) {
                return;
            }
            this.closed = true;
            try {
                getConnection().close();
            } catch (final IOException ignore) {
            }
        }

        @Override
        public boolean isClosed() {
            return this.closed;
        }

    }

    static class LocalConnPool extends AbstractConnPool<String, HttpConnection, LocalPoolEntry> {

        public LocalConnPool(
                final ConnFactory<String, HttpConnection> connFactory,
                final int defaultMaxPerRoute, final int maxTotal) {
            super(connFactory, defaultMaxPerRoute, maxTotal);
        }

        @Override
        protected LocalPoolEntry createEntry(final String route, final HttpConnection conn) {
            return new LocalPoolEntry(route, conn);
        }

        @Override
        protected boolean validate(final LocalPoolEntry entry) {
            return !entry.getConnection().isStale();
        }
    }

    @Test
    public void testEmptyPool() throws Exception {
        final LocalConnFactory connFactory = new LocalConnFactory();
        final LocalConnPool pool = new LocalConnPool(connFactory, 2, 10);
        pool.setDefaultMaxPerRoute(5);
        pool.setMaxPerRoute("somehost", 3);
        final PoolStats totals = pool.getTotalStats();
        Assertions.assertEquals(0, totals.getAvailable());
        Assertions.assertEquals(0, totals.getLeased());
        Assertions.assertEquals(10, totals.getMax());
        Assertions.assertEquals(Collections.emptySet(), pool.getRoutes());
        final PoolStats stats = pool.getStats("somehost");
        Assertions.assertEquals(0, stats.getAvailable());
        Assertions.assertEquals(0, stats.getLeased());
        Assertions.assertEquals(3, stats.getMax());
        Assertions.assertEquals("[leased: []][available: []][pending: []]", pool.toString());
    }

    @Test
    public void testInvalidConstruction() throws Exception {
        final LocalConnFactory connFactory = new LocalConnFactory();
        try {
            new LocalConnPool(connFactory, -1, 1);
            Assertions.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException expected) {
        }
        try {
            new LocalConnPool(connFactory, 1, -1);
            Assertions.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException expected) {
        }
    }

    @Test
    public void testLeaseRelease() throws Exception {
        final FakeHttpConnection conn1 = new FakeHttpConnection();
        conn1.calls.when("isOpen").thenReturn(true);
        final FakeHttpConnection conn2 = new FakeHttpConnection();
        conn2.calls.when("isOpen").thenReturn(true);

        final LocalConnFactory connFactory = new LocalConnFactory();
        connFactory.calls.when("create", "somehost").thenReturn(conn1);
        connFactory.calls.when("create", "otherhost").thenReturn(conn2);

        final LocalConnPool pool = new LocalConnPool(connFactory, 2, 10);
        final Future<LocalPoolEntry> future1 = pool.lease("somehost", null);
        final LocalPoolEntry entry1 = future1.get(1, TimeUnit.SECONDS);
        Assertions.assertNotNull(entry1);
        final Future<LocalPoolEntry> future2 = pool.lease("somehost", null);
        final LocalPoolEntry entry2 = future2.get(1, TimeUnit.SECONDS);
        Assertions.assertNotNull(entry2);
        final Future<LocalPoolEntry> future3 = pool.lease("otherhost", null);
        final LocalPoolEntry entry3 = future3.get(1, TimeUnit.SECONDS);
        Assertions.assertNotNull(entry3);

        PoolStats totals = pool.getTotalStats();
        Assertions.assertEquals(0, totals.getAvailable());
        Assertions.assertEquals(3, totals.getLeased());

        final LocalPoolEntry entry = future1.get();
        Assertions.assertSame(entry1, entry);

        pool.release(entry1, true);
        pool.release(entry2, true);
        pool.release(entry3, false);
        conn1.calls.assertNeverCalled("close");
        conn2.calls.assertCalled(1, "close");

        totals = pool.getTotalStats();
        Assertions.assertEquals(2, totals.getAvailable());
        Assertions.assertEquals(0, totals.getLeased());
    }

    @Test
    public void testLeaseIllegal() throws Exception {
        final LocalConnFactory connFactory = new LocalConnFactory();
        final LocalConnPool pool = new LocalConnPool(connFactory, 2, 10);
        try {
            pool.lease(null, null);
            Assertions.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException expected) {
        }
    }

    @Test
    public void testReleaseUnknownEntry() throws Exception {
        final LocalConnFactory connFactory = new LocalConnFactory();
        final LocalConnPool pool = new LocalConnPool(connFactory, 2, 10);
        pool.release(new LocalPoolEntry("somehost", new FakeHttpConnection()), true);
    }

    static class GetPoolEntryThread extends Thread {

        private final Future<LocalPoolEntry> future;
        private final long time;
        private final TimeUnit timeUnit;

        private volatile LocalPoolEntry entry;
        private volatile Exception ex;

        GetPoolEntryThread(final Future<LocalPoolEntry> future, final long time, final TimeUnit timeUnit) {
            super();
            this.future = future;
            this.time = time;
            this.timeUnit = timeUnit;
            setDaemon(true);
        }

        GetPoolEntryThread(final Future<LocalPoolEntry> future) {
            this(future, 1000, TimeUnit.SECONDS);
        }

        @Override
        public void run() {
            try {
                this.entry = this.future.get(this.time, this.timeUnit);
            } catch (final Exception ex) {
                this.ex = ex;
            }
        }

        public boolean isDone() {
            return this.future.isDone();
        }

        public LocalPoolEntry getEntry() {
            return this.entry;
        }

        public Exception getException() {
            return this.ex;
        }

    }

    @Test
    public void testMaxLimits() throws Exception {
        final LocalConnFactory connFactory = new LocalConnFactory();

        final FakeHttpConnection conn1 = new FakeHttpConnection();
        conn1.calls.when("isOpen").thenReturn(true);
        connFactory.calls.when("create", "somehost").thenReturn(conn1);

        final FakeHttpConnection conn2 = new FakeHttpConnection();
        conn2.calls.when("isOpen").thenReturn(true);
        connFactory.calls.when("create", "otherhost").thenReturn(conn2);

        final LocalConnPool pool = new LocalConnPool(connFactory, 2, 10);
        pool.setMaxPerRoute("somehost", 2);
        pool.setMaxPerRoute("otherhost", 1);
        pool.setMaxTotal(3);

        final Future<LocalPoolEntry> future1 = pool.lease("somehost", null);
        final GetPoolEntryThread t1 = new GetPoolEntryThread(future1);
        t1.start();
        final Future<LocalPoolEntry> future2 = pool.lease("somehost", null);
        final GetPoolEntryThread t2 = new GetPoolEntryThread(future2);
        t2.start();
        final Future<LocalPoolEntry> future3 = pool.lease("otherhost", null);
        final GetPoolEntryThread t3 = new GetPoolEntryThread(future3);
        t3.start();

        t1.join(GRACE_PERIOD);
        Assertions.assertTrue(future1.isDone());
        final LocalPoolEntry entry1 = t1.getEntry();
        Assertions.assertNotNull(entry1);
        t2.join(GRACE_PERIOD);
        Assertions.assertTrue(future2.isDone());
        final LocalPoolEntry entry2 = t2.getEntry();
        Assertions.assertNotNull(entry2);
        t3.join(GRACE_PERIOD);
        Assertions.assertTrue(future3.isDone());
        final LocalPoolEntry entry3 = t3.getEntry();
        Assertions.assertNotNull(entry3);

        pool.release(entry1, true);
        pool.release(entry2, true);
        pool.release(entry3, true);

        final PoolStats totals = pool.getTotalStats();
        Assertions.assertEquals(3, totals.getAvailable());
        Assertions.assertEquals(0, totals.getLeased());

        final Future<LocalPoolEntry> future4 = pool.lease("somehost", null);
        final GetPoolEntryThread t4 = new GetPoolEntryThread(future4);
        t4.start();
        final Future<LocalPoolEntry> future5 = pool.lease("somehost", null);
        final GetPoolEntryThread t5 = new GetPoolEntryThread(future5);
        t5.start();
        final Future<LocalPoolEntry> future6 = pool.lease("otherhost", null);
        final GetPoolEntryThread t6 = new GetPoolEntryThread(future6);
        t6.start();

        t4.join(GRACE_PERIOD);
        Assertions.assertTrue(future4.isDone());
        final LocalPoolEntry entry4 = t4.getEntry();
        Assertions.assertNotNull(entry4);
        t5.join(GRACE_PERIOD);
        Assertions.assertTrue(future5.isDone());
        final LocalPoolEntry entry5 = t5.getEntry();
        Assertions.assertNotNull(entry5);
        t6.join(GRACE_PERIOD);
        Assertions.assertTrue(future6.isDone());
        final LocalPoolEntry entry6 = t6.getEntry();
        Assertions.assertNotNull(entry6);

        final Future<LocalPoolEntry> future7 = pool.lease("somehost", null);
        final GetPoolEntryThread t7 = new GetPoolEntryThread(future7);
        t7.start();
        final Future<LocalPoolEntry> future8 = pool.lease("somehost", null);
        final GetPoolEntryThread t8 = new GetPoolEntryThread(future8);
        t8.start();
        final Future<LocalPoolEntry> future9 = pool.lease("otherhost", null);
        final GetPoolEntryThread t9 = new GetPoolEntryThread(future9);
        t9.start();

        Assertions.assertFalse(t7.isDone());
        Assertions.assertFalse(t8.isDone());
        Assertions.assertFalse(t9.isDone());

        connFactory.calls.assertCalled(3, "create", CallLog.ANY);

        pool.release(entry4, true);
        pool.release(entry5, false);
        pool.release(entry6, true);

        t7.join();
        Assertions.assertTrue(future7.isDone());
        t8.join();
        Assertions.assertTrue(future8.isDone());
        t9.join();
        Assertions.assertTrue(future9.isDone());

        connFactory.calls.assertCalled(4, "create", CallLog.ANY);
    }

    @Test
    public void testConnectionRedistributionOnTotalMaxLimit() throws Exception {
        final LocalConnFactory connFactory = new LocalConnFactory();

        final FakeHttpConnection conn1 = new FakeHttpConnection();
        conn1.calls.when("isOpen").thenReturn(true);
        final FakeHttpConnection conn2 = new FakeHttpConnection();
        conn2.calls.when("isOpen").thenReturn(true);
        final FakeHttpConnection conn3 = new FakeHttpConnection();
        conn3.calls.when("isOpen").thenReturn(true);
        connFactory.calls.when("create", "somehost").thenReturn(conn1, conn2, conn3);

        final FakeHttpConnection conn4 = new FakeHttpConnection();
        conn4.calls.when("isOpen").thenReturn(true);
        final FakeHttpConnection conn5 = new FakeHttpConnection();
        conn5.calls.when("isOpen").thenReturn(true);
        connFactory.calls.when("create", "otherhost").thenReturn(conn4, conn5);

        final LocalConnPool pool = new LocalConnPool(connFactory, 2, 10);
        pool.setMaxPerRoute("somehost", 2);
        pool.setMaxPerRoute("otherhost", 2);
        pool.setMaxTotal(2);

        final Future<LocalPoolEntry> future1 = pool.lease("somehost", null);
        final GetPoolEntryThread t1 = new GetPoolEntryThread(future1);
        t1.start();
        final Future<LocalPoolEntry> future2 = pool.lease("somehost", null);
        final GetPoolEntryThread t2 = new GetPoolEntryThread(future2);
        t2.start();

        t1.join(GRACE_PERIOD);
        Assertions.assertTrue(future1.isDone());
        final LocalPoolEntry entry1 = t1.getEntry();
        Assertions.assertNotNull(entry1);
        t2.join(GRACE_PERIOD);
        Assertions.assertTrue(future2.isDone());
        final LocalPoolEntry entry2 = t2.getEntry();
        Assertions.assertNotNull(entry2);

        final Future<LocalPoolEntry> future3 = pool.lease("otherhost", null);
        final GetPoolEntryThread t3 = new GetPoolEntryThread(future3);
        t3.start();
        final Future<LocalPoolEntry> future4 = pool.lease("otherhost", null);
        final GetPoolEntryThread t4 = new GetPoolEntryThread(future4);
        t4.start();

        Assertions.assertFalse(t3.isDone());
        Assertions.assertFalse(t4.isDone());

        connFactory.calls.assertCalled(2, "create", "somehost");
        connFactory.calls.assertNeverCalled("create", "otherhost");

        PoolStats totals = pool.getTotalStats();
        Assertions.assertEquals(0, totals.getAvailable());
        Assertions.assertEquals(2, totals.getLeased());

        pool.release(entry1, true);
        pool.release(entry2, true);

        t3.join(GRACE_PERIOD);
        Assertions.assertTrue(future3.isDone());
        final LocalPoolEntry entry3 = t3.getEntry();
        Assertions.assertNotNull(entry3);
        t4.join(GRACE_PERIOD);
        Assertions.assertTrue(future4.isDone());
        final LocalPoolEntry entry4 = t4.getEntry();
        Assertions.assertNotNull(entry4);

        connFactory.calls.assertCalled(2, "create", "somehost");
        connFactory.calls.assertCalled(2, "create", "otherhost");

        totals = pool.getTotalStats();
        Assertions.assertEquals(0, totals.getAvailable());
        Assertions.assertEquals(2, totals.getLeased());

        final Future<LocalPoolEntry> future5 = pool.lease("somehost", null);
        final GetPoolEntryThread t5 = new GetPoolEntryThread(future5);
        t5.start();
        final Future<LocalPoolEntry> future6 = pool.lease("otherhost", null);
        final GetPoolEntryThread t6 = new GetPoolEntryThread(future6);
        t6.start();

        pool.release(entry3, true);
        pool.release(entry4, true);

        t5.join(GRACE_PERIOD);
        Assertions.assertTrue(future5.isDone());
        final LocalPoolEntry entry5 = t5.getEntry();
        Assertions.assertNotNull(entry5);
        t6.join(GRACE_PERIOD);
        Assertions.assertTrue(future6.isDone());
        final LocalPoolEntry entry6 = t6.getEntry();
        Assertions.assertNotNull(entry6);

        connFactory.calls.assertCalled(3, "create", "somehost");
        connFactory.calls.assertCalled(2, "create", "otherhost");

        totals = pool.getTotalStats();
        Assertions.assertEquals(0, totals.getAvailable());
        Assertions.assertEquals(2, totals.getLeased());

        pool.release(entry5, true);
        pool.release(entry6, true);

        totals = pool.getTotalStats();
        Assertions.assertEquals(2, totals.getAvailable());
        Assertions.assertEquals(0, totals.getLeased());
    }

    @Test
    public void testStatefulConnectionRedistributionOnPerRouteMaxLimit() throws Exception {
        final LocalConnFactory connFactory = new LocalConnFactory();

        final FakeHttpConnection conn1 = new FakeHttpConnection();
        conn1.calls.when("isOpen").thenReturn(true);
        final FakeHttpConnection conn2 = new FakeHttpConnection();
        conn2.calls.when("isOpen").thenReturn(true);
        final FakeHttpConnection conn3 = new FakeHttpConnection();
        conn3.calls.when("isOpen").thenReturn(true);
        connFactory.calls.when("create", "somehost").thenReturn(conn1, conn2, conn3);

        final LocalConnPool pool = new LocalConnPool(connFactory, 2, 10);
        pool.setMaxPerRoute("somehost", 2);
        pool.setMaxTotal(2);

        final Future<LocalPoolEntry> future1 = pool.lease("somehost", null);
        final GetPoolEntryThread t1 = new GetPoolEntryThread(future1);
        t1.start();

        t1.join(GRACE_PERIOD);
        Assertions.assertTrue(future1.isDone());
        final LocalPoolEntry entry1 = t1.getEntry();
        Assertions.assertNotNull(entry1);

        final Future<LocalPoolEntry> future2 = pool.lease("somehost", null);
        final GetPoolEntryThread t2 = new GetPoolEntryThread(future2);
        t2.start();

        t2.join(GRACE_PERIOD);
        Assertions.assertTrue(future2.isDone());
        final LocalPoolEntry entry2 = t2.getEntry();
        Assertions.assertNotNull(entry2);

        PoolStats totals = pool.getTotalStats();
        Assertions.assertEquals(0, totals.getAvailable());
        Assertions.assertEquals(2, totals.getLeased());
        Assertions.assertEquals(0, totals.getPending());

        entry1.setState("some-stuff");
        pool.release(entry1, true);
        entry2.setState("some-stuff");
        pool.release(entry2, true);

        connFactory.calls.assertCalled(2, "create", "somehost");

        final Future<LocalPoolEntry> future3 = pool.lease("somehost", "some-other-stuff");
        final GetPoolEntryThread t3 = new GetPoolEntryThread(future3);
        t3.start();

        t3.join(GRACE_PERIOD);
        Assertions.assertTrue(future3.isDone());
        final LocalPoolEntry entry3 = t3.getEntry();
        Assertions.assertNotNull(entry3);

        connFactory.calls.assertCalled(3, "create", "somehost");

        conn1.calls.assertCalledOnce("close");
        conn2.calls.assertNeverCalled("close");

        totals = pool.getTotalStats();
        Assertions.assertEquals(1, totals.getAvailable());
        Assertions.assertEquals(1, totals.getLeased());

    }

    @Test
    public void testCreateNewIfExpired() throws Exception {
        final LocalConnFactory connFactory = new LocalConnFactory();

        final FakeHttpConnection conn1 = new FakeHttpConnection();
        conn1.calls.when("isOpen").thenReturn(true);
        connFactory.calls.when("create", "somehost").thenReturn(conn1);

        final LocalConnPool pool = new LocalConnPool(connFactory, 2, 2);

        final Future<LocalPoolEntry> future1 = pool.lease("somehost", null);
        final LocalPoolEntry entry1 = future1.get(1, TimeUnit.SECONDS);
        Assertions.assertNotNull(entry1);

        connFactory.calls.assertCalled(1, "create", "somehost");

        entry1.updateExpiry(1, TimeUnit.MILLISECONDS);
        pool.release(entry1, true);

        Thread.sleep(200L);

        final Future<LocalPoolEntry> future2 = pool.lease("somehost", null);
        final LocalPoolEntry entry2 = future2.get(1, TimeUnit.SECONDS);
        Assertions.assertNotNull(entry2);

        connFactory.calls.assertCalled(2, "create", "somehost");

        final PoolStats totals = pool.getTotalStats();
        Assertions.assertEquals(0, totals.getAvailable());
        Assertions.assertEquals(1, totals.getLeased());
        Assertions.assertEquals(Collections.singleton("somehost"), pool.getRoutes());
        final PoolStats stats = pool.getStats("somehost");
        Assertions.assertEquals(0, stats.getAvailable());
        Assertions.assertEquals(1, stats.getLeased());
    }

    @Test
    public void testCloseExpired() throws Exception {
        final LocalConnFactory connFactory = new LocalConnFactory();

        final FakeHttpConnection conn1 = new FakeHttpConnection();
        conn1.calls.when("isOpen").thenReturn(Boolean.FALSE);
        final FakeHttpConnection conn2 = new FakeHttpConnection();
        conn2.calls.when("isOpen").thenReturn(Boolean.TRUE);

        connFactory.calls.when("create", "somehost").thenReturn(conn1, conn2);

        final LocalConnPool pool = new LocalConnPool(connFactory, 2, 2);

        final Future<LocalPoolEntry> future1 = pool.lease("somehost", null);
        final LocalPoolEntry entry1 = future1.get(1, TimeUnit.SECONDS);
        Assertions.assertNotNull(entry1);
        final Future<LocalPoolEntry> future2 = pool.lease("somehost", null);
        final LocalPoolEntry entry2 = future2.get(1, TimeUnit.SECONDS);
        Assertions.assertNotNull(entry2);

        entry1.updateExpiry(1, TimeUnit.MILLISECONDS);
        pool.release(entry1, true);

        Thread.sleep(200);

        entry2.updateExpiry(1000, TimeUnit.SECONDS);
        pool.release(entry2, true);

        pool.closeExpired();

        conn1.calls.assertCalledOnce("close");
        conn2.calls.assertNeverCalled("close");

        final PoolStats totals = pool.getTotalStats();
        Assertions.assertEquals(1, totals.getAvailable());
        Assertions.assertEquals(0, totals.getLeased());
        final PoolStats stats = pool.getStats("somehost");
        Assertions.assertEquals(1, stats.getAvailable());
        Assertions.assertEquals(0, stats.getLeased());
    }

    @Test
    public void testLeaseTimeout() throws Exception {
        final LocalConnFactory connFactory = new LocalConnFactory();

        final FakeHttpConnection conn1 = new FakeHttpConnection();
        conn1.calls.when("isOpen").thenReturn(true);
        connFactory.calls.when("create", "somehost").thenReturn(conn1);

        final LocalConnPool pool = new LocalConnPool(connFactory, 1, 1);

        final Future<LocalPoolEntry> future1 = pool.lease("somehost", null);
        final GetPoolEntryThread t1 = new GetPoolEntryThread(future1);
        t1.start();

        t1.join(GRACE_PERIOD);
        Assertions.assertTrue(future1.isDone());
        final LocalPoolEntry entry1 = t1.getEntry();
        Assertions.assertNotNull(entry1);

        final Future<LocalPoolEntry> future2 = pool.lease("somehost", null);
        final GetPoolEntryThread t2 = new GetPoolEntryThread(future2, 50, TimeUnit.MICROSECONDS);
        t2.start();

        t2.join(GRACE_PERIOD);
        Assertions.assertTrue(t2.getException() instanceof TimeoutException);
        Assertions.assertFalse(future2.isDone());
        Assertions.assertFalse(future2.isCancelled());
    }

    @Test
    public void testLeaseIOException() throws Exception {
        final LocalConnFactory connFactory = new LocalConnFactory();
        connFactory.calls.when("create", "somehost").thenThrow(new IOException("Oppsie"));

        final LocalConnPool pool = new LocalConnPool(connFactory, 2, 10);

        final Future<LocalPoolEntry> future1 = pool.lease("somehost", null);
        final GetPoolEntryThread t1 = new GetPoolEntryThread(future1);
        t1.start();

        t1.join(GRACE_PERIOD);
        Assertions.assertTrue(future1.isDone());
        Assertions.assertTrue(t1.getException() instanceof ExecutionException);
        Assertions.assertTrue(t1.getException().getCause() instanceof IOException);
        Assertions.assertFalse(future1.isCancelled());
    }

    @Test
    public void testLeaseCancel() throws Exception {
        final LocalConnFactory connFactory = new LocalConnFactory();

        final FakeHttpConnection conn1 = new FakeHttpConnection();
        conn1.calls.when("isOpen").thenReturn(true);
        connFactory.calls.when("create", "somehost").thenReturn(conn1);

        final LocalConnPool pool = new LocalConnPool(connFactory, 1, 1);

        final Future<LocalPoolEntry> future1 = pool.lease("somehost", null);
        final GetPoolEntryThread t1 = new GetPoolEntryThread(future1);
        t1.start();

        t1.join(GRACE_PERIOD);
        Assertions.assertTrue(future1.isDone());
        final LocalPoolEntry entry1 = t1.getEntry();
        Assertions.assertNotNull(entry1);

        final Future<LocalPoolEntry> future2 = pool.lease("somehost", null);
        final GetPoolEntryThread t2 = new GetPoolEntryThread(future2);
        t2.start();

        Thread.sleep(5);

        Assertions.assertFalse(future2.isDone());
        Assertions.assertFalse(future2.isCancelled());

        future2.cancel(true);
        t2.join(GRACE_PERIOD);
        Assertions.assertTrue(future2.isDone());
        Assertions.assertTrue(future2.isCancelled());
        future2.cancel(true);
        future2.cancel(true);
    }

    @Test
    public void testCloseIdle() throws Exception {
        final LocalConnFactory connFactory = new LocalConnFactory();

        final FakeHttpConnection conn1 = new FakeHttpConnection();
        conn1.calls.when("isOpen").thenReturn(true);
        final FakeHttpConnection conn2 = new FakeHttpConnection();
        conn2.calls.when("isOpen").thenReturn(true);

        connFactory.calls.when("create", "somehost").thenReturn(conn1, conn2);

        final LocalConnPool pool = new LocalConnPool(connFactory, 2, 2);

        final Future<LocalPoolEntry> future1 = pool.lease("somehost", null);
        final LocalPoolEntry entry1 = future1.get(1, TimeUnit.SECONDS);
        Assertions.assertNotNull(entry1);
        final Future<LocalPoolEntry> future2 = pool.lease("somehost", null);
        final LocalPoolEntry entry2 = future2.get(1, TimeUnit.SECONDS);
        Assertions.assertNotNull(entry2);

        entry1.updateExpiry(0, TimeUnit.MILLISECONDS);
        pool.release(entry1, true);

        Thread.sleep(200L);

        entry2.updateExpiry(0, TimeUnit.MILLISECONDS);
        pool.release(entry2, true);

        pool.closeIdle(50, TimeUnit.MILLISECONDS);

        conn1.calls.assertCalledOnce("close");
        conn2.calls.assertNeverCalled("close");

        PoolStats totals = pool.getTotalStats();
        Assertions.assertEquals(1, totals.getAvailable());
        Assertions.assertEquals(0, totals.getLeased());
        PoolStats stats = pool.getStats("somehost");
        Assertions.assertEquals(1, stats.getAvailable());
        Assertions.assertEquals(0, stats.getLeased());

        pool.closeIdle(-1, TimeUnit.MILLISECONDS);

        conn2.calls.assertCalledOnce("close");

        totals = pool.getTotalStats();
        Assertions.assertEquals(0, totals.getAvailable());
        Assertions.assertEquals(0, totals.getLeased());
        stats = pool.getStats("somehost");
        Assertions.assertEquals(0, stats.getAvailable());
        Assertions.assertEquals(0, stats.getLeased());
    }

    @Test
    public void testCloseIdleInvalid() throws Exception {
        final LocalConnFactory connFactory = new LocalConnFactory();
        final LocalConnPool pool = new LocalConnPool(connFactory, 2, 2);
        Assertions.assertThrows(IllegalArgumentException.class, () -> pool.closeIdle(50, null));
    }

    @Test
    public void testGetStatsInvalid() throws Exception {
        final LocalConnFactory connFactory = new LocalConnFactory();
        final LocalConnPool pool = new LocalConnPool(connFactory, 2, 2);
        Assertions.assertThrows(IllegalArgumentException.class, () -> pool.getStats(null));
    }

    @Test
    public void testSetMaxInvalid() throws Exception {
        final LocalConnFactory connFactory = new LocalConnFactory();
        final LocalConnPool pool = new LocalConnPool(connFactory, 2, 2);
        try {
            pool.setMaxTotal(-1);
            Assertions.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException expected) {
        }
        try {
            pool.setMaxPerRoute(null, 1);
            Assertions.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException expected) {
        }
        try {
            pool.setDefaultMaxPerRoute(-1);
            Assertions.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException expected) {
        }
    }

    @Test
    public void testSetMaxPerRoute() throws Exception {
        final LocalConnFactory connFactory = new LocalConnFactory();
        final LocalConnPool pool = new LocalConnPool(connFactory, 2, 2);
        pool.setMaxPerRoute("somehost", 1);
        Assertions.assertEquals(1, pool.getMaxPerRoute("somehost"));
        pool.setMaxPerRoute("somehost", 0);
        Assertions.assertEquals(0, pool.getMaxPerRoute("somehost"));
        pool.setMaxPerRoute("somehost", -1);
        Assertions.assertEquals(2, pool.getMaxPerRoute("somehost"));
    }

    @Test
    public void testShutdown() throws Exception {
        final LocalConnFactory connFactory = new LocalConnFactory();

        final FakeHttpConnection conn1 = new FakeHttpConnection();
        conn1.calls.when("isOpen").thenReturn(true);
        connFactory.calls.when("create", "somehost").thenReturn(conn1);
        final FakeHttpConnection conn2 = new FakeHttpConnection();
        conn2.calls.when("isOpen").thenReturn(true);
        connFactory.calls.when("create", "otherhost").thenReturn(conn2);

        final LocalConnPool pool = new LocalConnPool(connFactory, 2, 2);
        final Future<LocalPoolEntry> future1 = pool.lease("somehost", null);
        final LocalPoolEntry entry1 = future1.get(1, TimeUnit.SECONDS);
        Assertions.assertNotNull(entry1);
        final Future<LocalPoolEntry> future2 = pool.lease("otherhost", null);
        final LocalPoolEntry entry2 = future2.get(1, TimeUnit.SECONDS);
        Assertions.assertNotNull(entry2);

        pool.release(entry2, true);

        final PoolStats totals = pool.getTotalStats();
        Assertions.assertEquals(1, totals.getAvailable());
        Assertions.assertEquals(1, totals.getLeased());

        pool.shutdown();
        Assertions.assertTrue(pool.isShutdown());
        pool.shutdown();
        pool.shutdown();

        conn1.calls.assertCalledAtLeastOnce("close");
        conn2.calls.assertCalledAtLeastOnce("close");

        try {
            pool.lease("somehost", null);
            Assertions.fail("IllegalStateException should have been thrown");
        } catch (final IllegalStateException expected) {
        }
        // Ignored if shut down
        pool.release(new LocalPoolEntry("somehost", new FakeHttpConnection()), true);
    }

    @Test
    public void testValidateConnectionNotStale() throws Exception {
        final FakeHttpConnection conn = new FakeHttpConnection();
        conn.calls.when("isOpen").thenReturn(true);
        conn.calls.when("isStale").thenReturn(false);

        final LocalConnFactory connFactory = new LocalConnFactory();
        connFactory.calls.when("create", "somehost").thenReturn(conn);

        final LocalConnPool pool = new LocalConnPool(connFactory, 2, 10);
        pool.setValidateAfterInactivity(100);

        final Future<LocalPoolEntry> future1 = pool.lease("somehost", null);
        final LocalPoolEntry entry1 = future1.get(1, TimeUnit.SECONDS);
        Assertions.assertNotNull(entry1);

        pool.release(entry1, true);

        Thread.sleep(150);

        final Future<LocalPoolEntry> future2 = pool.lease("somehost", null);
        final LocalPoolEntry entry2 = future2.get(1, TimeUnit.SECONDS);
        Assertions.assertNotNull(entry2);
        Assertions.assertSame(entry1, entry2);

        conn.calls.assertCalled(1, "isStale");
    }

    @Test
    public void testValidateConnectionStale() throws Exception {
        final FakeHttpConnection conn = new FakeHttpConnection();
        conn.calls.when("isOpen").thenReturn(true);
        conn.calls.when("isStale").thenReturn(false);

        final LocalConnFactory connFactory = new LocalConnFactory();
        connFactory.calls.when("create", "somehost").thenReturn(conn);

        final LocalConnPool pool = new LocalConnPool(connFactory, 2, 10);
        pool.setValidateAfterInactivity(5);

        final Future<LocalPoolEntry> future1 = pool.lease("somehost", null);
        final LocalPoolEntry entry1 = future1.get(1, TimeUnit.SECONDS);
        Assertions.assertNotNull(entry1);

        pool.release(entry1, true);

        Thread.sleep(10);

        connFactory.calls.assertCalled(1, "create", "somehost");
        conn.calls.when("isStale").thenReturn(true);

        final Future<LocalPoolEntry> future2 = pool.lease("somehost", null);
        final LocalPoolEntry entry2 = future2.get(1, TimeUnit.SECONDS);
        Assertions.assertNotNull(entry2);
        Assertions.assertNotSame(entry1, entry2);

        conn.calls.assertCalled(1, "isStale");
        conn.calls.assertCalled(1, "close");
        connFactory.calls.assertCalled(2, "create", "somehost");
    }

}
