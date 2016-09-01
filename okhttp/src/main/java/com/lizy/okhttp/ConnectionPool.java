package com.lizy.okhttp;

import com.lizy.okhttp.internal.Util;
import com.lizy.okhttp.internal.connection.RealConnection;
import com.lizy.okhttp.internal.connection.RouteDatabase;
import com.lizy.okhttp.internal.connection.StreamAllocation;

import java.lang.ref.Reference;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.lizy.okhttp.internal.Util.closeQuietly;

/**
 * Created by lizy on 16-8-31.
 */
public final class ConnectionPool {

    private static final Executor EXECUTOR = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60,
            TimeUnit.SECONDS, new SynchronousQueue<>(),
            Util.threadFactory("Okhttp ConnectionPoll", true));

    private final int maxIdleConnections;
    private final long keepAliveDurationNs;
    private final Runnable cleanUpRunnable = new Runnable() {
        @Override
        public void run() {
            while(true) {
                long waitNanos = cleanup(System.nanoTime());
                if (waitNanos == -1) return;
                if (waitNanos > 0) {
                    long waitMillis = waitNanos / 1000000L;
                    waitNanos -= waitMillis * 1000000L;
                    synchronized (ConnectionPool.this) {
                        try {
                            ConnectionPool.this.wait(waitMillis, (int)waitNanos);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    };

    boolean cleanupRunning;

    private final Deque<RealConnection> connections = new ArrayDeque<>();

    final RouteDatabase routeDatabase = new RouteDatabase();

    public ConnectionPool() {
        this(5, 5, TimeUnit.MINUTES);
    }

    public ConnectionPool(int maxIdleConnections, long keepAliveDuration, TimeUnit timeUnit) {
        this.maxIdleConnections = maxIdleConnections;
        this.keepAliveDurationNs = timeUnit.toNanos(keepAliveDuration);

        if (keepAliveDurationNs <= 0) {
            throw new IllegalArgumentException("keepAliveDuration <= 0: " + keepAliveDuration);
        }
    }

    RealConnection get(Address address, StreamAllocation streamAllocation) {
        assert (Thread.holdsLock(this));
        for (RealConnection connection : connections) {
            if (connection.allocations.size() < connection.allocationLimit
                    && address.equals(connection.route().address())
                    && !connection.noNewStreams) {
                streamAllocation.acquire(connection);
                return connection;
            }
        }
        return null;
    }

    public synchronized int idleConnectionCount() {
        int total = 0;
        for (RealConnection connection : connections) {
            if (connection.allocations.isEmpty()) total++;
        }
        return total;
    }

    public synchronized int connectionCount() {
        return connections.size();
    }

    void put(RealConnection connection) {
        assert (Thread.holdsLock(this));

        if (!cleanupRunning) {
            cleanupRunning = true;
            EXECUTOR.execute(cleanUpRunnable);
        }
        connections.add(connection);
    }

    public long cleanup(long now) {
        int inUsedConnectionCount = 0;
        int idleConnectionCount = 0;
        RealConnection longestIdleConnection = null;
        long longestIdleDurationNs = Long.MIN_VALUE;

        synchronized (this) {

            for (Iterator<RealConnection> i = connections.iterator(); i.hasNext(); ) {
                RealConnection connection = i.next();

                if (pruneAndGetAllocationCount(connection, now) > 0) {
                    inUsedConnectionCount++;
                    continue;
                }

                idleConnectionCount++;

                long idleDuration = now - connection.idleAtNanos;
                if (idleDuration > longestIdleDurationNs) {
                    longestIdleDurationNs = idleDuration;
                    longestIdleConnection = connection;
                }
            }

            System.out.println("l:" + longestIdleDurationNs + " idle:" + idleConnectionCount
                    + " use:" + inUsedConnectionCount + " keep:" + keepAliveDurationNs);
            if (longestIdleDurationNs >= keepAliveDurationNs
                    || idleConnectionCount > this.maxIdleConnections) {
                connections.remove(longestIdleConnection);
            } else if (idleConnectionCount > 0) {
                return keepAliveDurationNs - longestIdleDurationNs;
            } else if (inUsedConnectionCount > 0) {
                return keepAliveDurationNs;
            } else {
                cleanupRunning = false;
                return -1;
            }
        }

        closeQuietly(longestIdleConnection.socket());
        return 0;
    }

    private int pruneAndGetAllocationCount(RealConnection connection, long now) {
        List<Reference<StreamAllocation>> references = connection.allocations;
        for (int i = 0; i < references.size(); ) {
            Reference<StreamAllocation> reference = references.get(i);
            if (reference.get() != null) {
                i++;
                continue;
            }

            references.remove(i);
            connection.noNewStreams = true;

            if (references.isEmpty()) {
                connection.idleAtNanos = now - keepAliveDurationNs;
                return 0;
            }
        }
        return references.size();
    }

    boolean connectionBecameIdle(RealConnection connection) {
        assert (Thread.holdsLock(this));
        if (connection.noNewStreams || maxIdleConnections == 0) {
            connections.remove(connection);
            return true;
        } else {
            notifyAll(); // Awake the cleanup thread: we may have exceeded the idle connection limit.
            return false;
        }
    }
}
