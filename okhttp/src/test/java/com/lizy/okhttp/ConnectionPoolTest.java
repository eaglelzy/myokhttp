package com.lizy.okhttp;

import com.lizy.okhttp.internal.connection.RealConnection;
import com.lizy.okhttp.internal.connection.StreamAllocation;

import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.Socket;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import javax.net.SocketFactory;

import static com.lizy.okhttp.TestUtil.awaitGarbageCollection;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

/**
 * Created by lizy on 16-8-31.
 */
public class ConnectionPoolTest {
    private final Address addressA = newAddress("a");
    private final Route routeA1 = newRoute(addressA);
    private final Address addressB = newAddress("b");
    private final Route routeB1 = newRoute(addressB);
    private final Address addressC = newAddress("c");
    private final Route routeC1 = newRoute(addressC);

    @Test public void connectionsEvictedWhenIdleLongEnough() throws Exception {
        ConnectionPool pool = new ConnectionPool(Integer.MAX_VALUE, 100L, TimeUnit.NANOSECONDS);
        pool.cleanupRunning = true; // Prevent the cleanup runnable from being started.

        RealConnection c1 = newConnection(pool, routeA1, 50L);

        // Running at time 50, the pool returns that nothing can be evicted until time 150.
        assertEquals(100L, pool.cleanup(50L));
        assertEquals(1, pool.connectionCount());
        assertFalse(c1.socket.isClosed());

        // Running at time 60, the pool returns that nothing can be evicted until time 150.
        assertEquals(90L, pool.cleanup(60L));
        assertEquals(1, pool.connectionCount());
        assertFalse(c1.socket.isClosed());

        // Running at time 149, the pool returns that nothing can be evicted until time 150.
        assertEquals(1L, pool.cleanup(149L));
        assertEquals(1, pool.connectionCount());
        assertFalse(c1.socket.isClosed());

        // Running at time 150, the pool evicts.
        assertEquals(0, pool.cleanup(150L));
        assertEquals(0, pool.connectionCount());
        assertTrue(c1.socket.isClosed());

        // Running again, the pool reports that no further runs are necessary.
        assertEquals(-1, pool.cleanup(150L));
        assertEquals(0, pool.connectionCount());
        assertTrue(c1.socket.isClosed());
    }

    @Test public void inUseConnectionsNotEvicted() throws Exception {
        ConnectionPool pool = new ConnectionPool(Integer.MAX_VALUE, 100L, TimeUnit.NANOSECONDS);
        pool.cleanupRunning = true; // Prevent the cleanup runnable from being started.

        RealConnection c1 = newConnection(pool, routeA1, 50L);
        synchronized (pool) {
            StreamAllocation streamAllocation = new StreamAllocation(pool, addressA, null);
            streamAllocation.acquire(c1);
        }

        // Running at time 50, the pool returns that nothing can be evicted until time 150.
        assertEquals(100L, pool.cleanup(50L));
        assertEquals(1, pool.connectionCount());
        assertFalse(c1.socket.isClosed());

        // Running at time 60, the pool returns that nothing can be evicted until time 160.
        assertEquals(100L, pool.cleanup(60L));
        assertEquals(1, pool.connectionCount());
        assertFalse(c1.socket.isClosed());

        // Running at time 160, the pool returns that nothing can be evicted until time 260.
        assertEquals(100L, pool.cleanup(160L));
        assertEquals(1, pool.connectionCount());
        assertFalse(c1.socket.isClosed());
    }

    @Test public void cleanupPrioritizesEarliestEviction() throws Exception {
        ConnectionPool pool = new ConnectionPool(Integer.MAX_VALUE, 100L, TimeUnit.NANOSECONDS);
        pool.cleanupRunning = true; // Prevent the cleanup runnable from being started.

        RealConnection c1 = newConnection(pool, routeA1, 75L);
        RealConnection c2 = newConnection(pool, routeB1, 50L);

        // Running at time 75, the pool returns that nothing can be evicted until time 150.
        assertEquals(75L, pool.cleanup(75L));
        assertEquals(2, pool.connectionCount());

        // Running at time 149, the pool returns that nothing can be evicted until time 150.
        assertEquals(1L, pool.cleanup(149L));
        assertEquals(2, pool.connectionCount());

        // Running at time 150, the pool evicts c2.
        assertEquals(0L, pool.cleanup(150L));
        assertEquals(1, pool.connectionCount());
        assertFalse(c1.socket.isClosed());
        assertTrue(c2.socket.isClosed());

        // Running at time 150, the pool returns that nothing can be evicted until time 175.
        assertEquals(25L, pool.cleanup(150L));
        assertEquals(1, pool.connectionCount());

        // Running at time 175, the pool evicts c1.
        assertEquals(0L, pool.cleanup(175L));
        assertEquals(0, pool.connectionCount());
        assertTrue(c1.socket.isClosed());
        assertTrue(c2.socket.isClosed());
    }

    @Test public void oldestConnectionsEvictedIfIdleLimitExceeded() throws Exception {
        ConnectionPool pool = new ConnectionPool(2, 100L, TimeUnit.NANOSECONDS);
        pool.cleanupRunning = true; // Prevent the cleanup runnable from being started.

        RealConnection c1 = newConnection(pool, routeA1, 50L);
        RealConnection c2 = newConnection(pool, routeB1, 75L);

        // With 2 connections, there's no need to evict until the connections time out.
        assertEquals(50L, pool.cleanup(100L));
        assertEquals(2, pool.connectionCount());
        assertFalse(c1.socket.isClosed());
        assertFalse(c2.socket.isClosed());

        // Add a third connection
        RealConnection c3 = newConnection(pool, routeC1, 75L);

        // The third connection bounces the first.
        assertEquals(0L, pool.cleanup(100L));
        assertEquals(2, pool.connectionCount());
        assertTrue(c1.socket.isClosed());
        assertFalse(c2.socket.isClosed());
        assertFalse(c3.socket.isClosed());
    }

    @Test public void leakedAllocation() throws Exception {
        ConnectionPool pool = new ConnectionPool(2, 100L, TimeUnit.NANOSECONDS);
        pool.cleanupRunning = true; // Prevent the cleanup runnable from being started.

        RealConnection c1 = newConnection(pool, routeA1, 0L);
        allocateAndLeakAllocation(pool, c1);

        awaitGarbageCollection();
        assertEquals(0L, pool.cleanup(100L));
        assertEquals(Collections.emptyList(), c1.allocations);

        assertTrue(c1.noNewStreams); // Can't allocate once a leak has been detected.
    }

    private void allocateAndLeakAllocation(ConnectionPool pool, RealConnection connection) {
        synchronized (pool) {
            StreamAllocation leak = new StreamAllocation(pool, connection.route().address(), null);
            leak.acquire(connection);
        }
    }

    private RealConnection newConnection(ConnectionPool pool, Route route, long idleAtNanos) {
        RealConnection connection = new RealConnection(route);
        connection.idleAtNanos = idleAtNanos;
        connection.socket = new Socket();
        synchronized (pool) {
            pool.put(connection);
        }
        return connection;
    }

    private Address newAddress(String name) {
        return new Address(name, 1, Dns.SYSTEM, SocketFactory.getDefault(),
                Authenticator.NONE, Collections.<Protocol>emptyList(),
                Collections.<ConnectionSpec>emptyList(),
                ProxySelector.getDefault(), null, null, null, null);
    }

    private Route newRoute(Address address) {
        return new Route(address, Proxy.NO_PROXY,
                InetSocketAddress.createUnresolved(address.httpUrl().host(), address.httpUrl().port()));
    }
}
