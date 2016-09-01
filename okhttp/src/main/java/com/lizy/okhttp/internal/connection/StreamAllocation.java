package com.lizy.okhttp.internal.connection;

import com.lizy.okhttp.Address;
import com.lizy.okhttp.ConnectionPool;
import com.lizy.okhttp.HttpCodec;
import com.lizy.okhttp.OkHttpClient;
import com.lizy.okhttp.Route;
import com.lizy.okhttp.internal.Internal;
import com.lizy.okhttp.internal.Util;
import com.lizy.okhttp.internal.http1.Http1Codec;

import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;

/**
 * Created by lizy on 16-8-31.
 */
public final class StreamAllocation {

    private final ConnectionPool connectionPool;
    private final Address address;
    private final Object callStackTrace;
    private Route route;
    private HttpCodec codec;
    private boolean released;
    private boolean canceled;
    private final RouteSelector routeSelector;

    private RealConnection connection;

    public StreamAllocation(ConnectionPool connectionPool, Address address, Object callStackTrace) {
        this.connectionPool = connectionPool;
        this.callStackTrace = callStackTrace;
        this.address = address;
        this.routeSelector = new RouteSelector(address, routeDatabase());
    }

    public void acquire(RealConnection connection) {
        assert (Thread.holdsLock(connectionPool));
        connection.allocations.add(new StreamAllocationReference(this, callStackTrace));
    }

    public synchronized RealConnection connection() {
        return connection;
    }

    public HttpCodec newStream(OkHttpClient client, boolean doExtensiveHealthChecks) {
        int connectionTimeout = client.connectionTimeout();
        int readTimeout = client.connectionTimeout();
        int writeTimeout = client.connectionTimeout();

        boolean connectionRetryEnable = false;

        try {
            RealConnection resultConnection = findHealthyConnection(connectionTimeout, readTimeout,
                    writeTimeout, connectionRetryEnable, doExtensiveHealthChecks);

            HttpCodec resultCodec;
            resultConnection.socket().setSoTimeout(connectionTimeout);
            resultConnection.source.timeout().timeout(readTimeout, TimeUnit.MILLISECONDS);
            resultConnection.sink.timeout().timeout(readTimeout, TimeUnit.MILLISECONDS);
            resultCodec = new Http1Codec(client, this, resultConnection.source,
                    resultConnection.sink);
            synchronized (connectionPool) {
                codec = resultCodec;
                return resultCodec;
            }
        } catch (IOException e) {
            throw new RouteException(e);
        }
    }

    private RealConnection findHealthyConnection(int connectionTimeout, int readTimeout,
                 int writeTimeout, boolean connectionRetryEnable,
                 boolean doExtensiveHealthChecks) throws IOException {
        while(true) {
            RealConnection candidate = findConnection(connectionTimeout, readTimeout,
                    writeTimeout, connectionRetryEnable);

            synchronized (connectionPool) {
                if (candidate.successCount == 0) {
                    return candidate;
                }
            }

            if (!candidate.isHealthy(doExtensiveHealthChecks)) {
                noNewStreams();
                continue;
            }

            return candidate;
        }
    }

    private RealConnection findConnection(int connectionTimeout, int readTimeout, int writeTimeout,
                  boolean connectionRetryEnable) throws IOException {
        Route selectedRoute;
        synchronized (connectionPool) {
            if (released) throw new IllegalStateException("released!");
            if (codec != null) throw new IllegalStateException("codec != null");
            if (canceled) throw new IOException("Canceled!");

            RealConnection allocatedConntection = this.connection;
            if (allocatedConntection != null && !allocatedConntection.noNewStreams) {
                return allocatedConntection;
            }

            RealConnection pooledConnection = Internal.instance.get(connectionPool, address, this);
            if (pooledConnection != null) {
                this.connection = pooledConnection;
                return pooledConnection;
            }

            selectedRoute = route;
        }

        if (selectedRoute == null) {
            selectedRoute = routeSelector.next();
            synchronized (connectionPool) {
                route = selectedRoute;
            }
        }

        RealConnection newConnection = new RealConnection(selectedRoute);

        synchronized (connectionPool) {
            acquire(newConnection);
            Internal.instance.put(connectionPool, newConnection);
            this.connection = newConnection;
            if (canceled) throw new IOException("Canceled");
        }

        newConnection.connect(connectionTimeout, readTimeout, writeTimeout, address.connectionSpecs(),
                connectionRetryEnable);
        routeDatabase().connected(newConnection.route());

        return newConnection;
    }

    public RouteDatabase routeDatabase() {
        return Internal.instance.routeDatabase(connectionPool);
    }

    public void noNewStreams() {
        deallocate(true, false, false);
    }

    private void deallocate(boolean noNewStream, boolean released, boolean streamFinished) {
        RealConnection connectionToClose = null;
        synchronized (connectionPool) {
            if (streamFinished) {
                this.codec = null;
            }
            if (released) {
                this.released = true;
            }
            if (connection != null) {
                if (noNewStream) {
                    connection.noNewStreams = true;
                }

                if (codec == null && (this.released || connection.noNewStreams)) {
                    release(connection);
                    if (connection.allocations.isEmpty()) {
                        connection.idleAtNanos = System.nanoTime();
                        if (Internal.instance.connectionBecameIdle(connectionPool, connection)) {
                            connectionToClose = connection;
                        }
                    }
                    connection = null;
                }
            }

            if (connectionToClose != null) {
                Util.closeQuietly(connectionToClose.socket());
            }
        }
    }

    private void release(RealConnection connection) {
        for (int i = 0, size = connection.allocations.size(); i < size; i++) {
            Reference<StreamAllocation> reference = connection.allocations.get(i);
            if (reference.get() == this) {
                connection.allocations.remove(reference);
                return;
            }
        }
        throw new IllegalStateException();
    }

    public void streamFinished(boolean noNewStreams, Http1Codec httpCodec) {

    }

    public static final class StreamAllocationReference extends WeakReference<StreamAllocation> {

        public final Object callStackTrace;

        public StreamAllocationReference(StreamAllocation referent, Object callStackTrace) {
            super(referent);
            this.callStackTrace = callStackTrace;
        }
    }
}
