package com.lizy.okhttp.internal.connection;

import com.lizy.okhttp.Address;
import com.lizy.okhttp.Connection;
import com.lizy.okhttp.ConnectionSpec;
import com.lizy.okhttp.Protocol;
import com.lizy.okhttp.Route;
import com.lizy.okhttp.internal.platform.Platform;

import java.io.IOException;
import java.lang.ref.Reference;
import java.net.ConnectException;
import java.net.Proxy;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

import static com.lizy.okhttp.internal.Util.closeQuietly;

/**
 * Created by lizy on 16-8-31.
 */
public class RealConnection implements Connection {
    private final Route route;

    private Socket rawSocket;

    public Socket socket;
    private Protocol protocol;
    BufferedSource source;
    BufferedSink sink;
    public final List<Reference<StreamAllocation>> allocations = new ArrayList<>();

    public int allocationLimit;
    public boolean noNewStreams;

    public long idleAtNanos;

    public RealConnection(Route route) {
        this.route = route;
    }

    public void connect(int connectTimeout, int readTimeout, int writeTimeout,
                        List<ConnectionSpec> connectionSpecs, boolean connectionRetryEnabled) {
        if (protocol != null) throw new IllegalStateException("already connected");

        RouteException routeException = null;
        while(protocol == null) {
            try {
                if (route.requiresTunnel()) {

                } else {
                    buildConnection(connectTimeout, readTimeout, writeTimeout);
                }
            }catch (IOException e) {
                closeQuietly(socket);
                closeQuietly(rawSocket);
                socket = null;
                rawSocket = null;
                source = null;
                sink = null;
//                handshake = null;
                protocol = null;

                if (routeException == null) {
                    routeException = new RouteException(e);
                } else {
                    routeException.addConnectException(e);
                }

                if (!connectionRetryEnabled) {// || !connectionSpecSelector.connectionFailed(e)) {
                    throw routeException;
                }
            }
        }
    }

    /** Does all the work necessary to build a full HTTP or HTTPS connection on a raw socket. */
    private void buildConnection(int connectTimeout, int readTimeout, int writeTimeout)
            throws IOException {
        connectSocket(connectTimeout, readTimeout);
        establishProtocol();
    }

    private void establishProtocol() {
        protocol = Protocol.HTTP_1_1;
        socket = rawSocket;
        allocationLimit = 1;
    }

    private void connectSocket(int connectTimeout, int readTimeout) throws IOException {
        Proxy proxy = route.proxy();
        Address address = route.address();

        rawSocket = proxy.type() == Proxy.Type.DIRECT || proxy.type() == Proxy.Type.HTTP
                ? address.socketFactory().createSocket()
                : new Socket(proxy);

        rawSocket.setSoTimeout(readTimeout);
        try {
            System.out.println("1111");
            Platform.get().connectSocket(rawSocket, route.socketAddress(), connectTimeout);
        } catch (ConnectException e) {
            throw new ConnectException("Failed to connect to " + route.socketAddress());
        }
        source = Okio.buffer(Okio.source(rawSocket));
        sink = Okio.buffer(Okio.sink(rawSocket));
    }

    @Override
    public Route route() {
        return route;
    }

    @Override
    public Socket socket() {
        return socket;
    }

    @Override
    public Protocol protocol() {
        return protocol;
    }
}
