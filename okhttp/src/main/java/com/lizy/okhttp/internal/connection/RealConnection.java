package com.lizy.okhttp.internal.connection;

import com.lizy.okhttp.Address;
import com.lizy.okhttp.CertificatePinner;
import com.lizy.okhttp.Connection;
import com.lizy.okhttp.ConnectionSpec;
import com.lizy.okhttp.Handshake;
import com.lizy.okhttp.Protocol;
import com.lizy.okhttp.Route;
import com.lizy.okhttp.internal.Util;
import com.lizy.okhttp.internal.platform.Platform;
import com.lizy.okhttp.internal.tls.OkHostnameVerifier;

import java.io.IOException;
import java.lang.ref.Reference;
import java.net.ConnectException;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

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

    private Handshake handshake;
    public Socket socket;
    private Protocol protocol;
    BufferedSource source;
    BufferedSink sink;
    public final List<Reference<StreamAllocation>> allocations = new ArrayList<>();

    public int allocationLimit;
    public boolean noNewStreams;

    public long idleAtNanos;
    public int successCount;

    public RealConnection(Route route) {
        this.route = route;
    }

    public void connect(int connectTimeout, int readTimeout, int writeTimeout,
                        List<ConnectionSpec> connectionSpecs, boolean connectionRetryEnabled) {
        if (protocol != null) throw new IllegalStateException("already connected");

        RouteException routeException = null;
        ConnectionSpecSelector connectionSpecSelector = new ConnectionSpecSelector(connectionSpecs);

        while (protocol == null) {
            try {
                if (route.requiresTunnel()) {

                } else {
                    buildConnection(connectTimeout, readTimeout, writeTimeout,
                            connectionSpecSelector);
                }
            } catch (IOException e) {
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

    /**
     * Does all the work necessary to build a full HTTP or HTTPS connection on a raw socket.
     */
    private void buildConnection(int connectTimeout, int readTimeout, int writeTimeout,
                                 ConnectionSpecSelector connectionSpecSelector)
            throws IOException {
        connectSocket(connectTimeout, readTimeout);
        establishProtocol(readTimeout, writeTimeout, connectionSpecSelector);
    }

    private void establishProtocol(int readTimeout, int writeTimeout,
           ConnectionSpecSelector connectionSpecSelector) throws IOException {
        if (route.address().sslSocketFactory() != null) {
            connectTls(readTimeout, writeTimeout, connectionSpecSelector);
        } else {
            protocol = Protocol.HTTP_1_1;
            socket = rawSocket;
        }

        if (protocol == Protocol.HTTP_2) {

        } else {
            allocationLimit = 1;
        }
    }

    private void connectTls(int readTimeout, int writeTimeout,
                ConnectionSpecSelector connectionSpecSelector) throws IOException {
        Address address = route.address();
        SSLSocketFactory sslSocketFactory = address.sslSocketFactory();
        boolean success = false;
        SSLSocket sslSocket = null;
        try {
            // Create the wrapper over the connected socket.
            sslSocket = (SSLSocket) sslSocketFactory.createSocket(
                    rawSocket, address.url().host(), address.url().port(), true /* autoClose */);

            // Configure the socket's ciphers, TLS versions, and extensions.
            ConnectionSpec connectionSpec = connectionSpecSelector.configureSecureSocket(sslSocket);
            if (connectionSpec.supportsTlsExtensions()) {
                Platform.get().configureTlsExtensions(
                        sslSocket, address.url().host(), address.protocols());
            }

            // Force handshake. This can throw!
            sslSocket.startHandshake();
            Handshake unverifiedHandshake = Handshake.get(sslSocket.getSession());

            // Verify that the socket's certificates are acceptable for the target host.
            if (!address.hostnameVerifier().verify(address.url().host(), sslSocket.getSession())) {
                X509Certificate cert = (X509Certificate) unverifiedHandshake.peerCertificates().get(0);
                throw new SSLPeerUnverifiedException("Hostname " + address.url().host() + " not verified:"
                        + "\n    certificate: " + CertificatePinner.pin(cert)
                        + "\n    DN: " + cert.getSubjectDN().getName()
                        + "\n    subjectAltNames: " + OkHostnameVerifier.allSubjectAltNames(cert));
            }

            // Check that the certificate pinner is satisfied by the certificates presented.
            address.certificatePinner().check(address.url().host(),
                    unverifiedHandshake.peerCertificates());

            // Success! Save the handshake and the ALPN protocol.
            String maybeProtocol = connectionSpec.supportsTlsExtensions()
                    ? Platform.get().getSelectedProtocol(sslSocket)
                    : null;
            socket = sslSocket;
            source = Okio.buffer(Okio.source(socket));
            sink = Okio.buffer(Okio.sink(socket));
            handshake = unverifiedHandshake;
            protocol = maybeProtocol != null
                    ? Protocol.get(maybeProtocol)
                    : Protocol.HTTP_1_1;
            success = true;
        } catch (AssertionError e) {
            if (Util.isAndroidGetsocknameError(e)) throw new IOException(e);
            throw e;
        } finally {
            if (sslSocket != null) {
                Platform.get().afterHandshake(sslSocket);
            }
            if (!success) {
                closeQuietly(sslSocket);
            }
        }
    }

    private void connectSocket(int connectTimeout, int readTimeout) throws IOException {
        Proxy proxy = route.proxy();
        Address address = route.address();

        rawSocket = proxy.type() == Proxy.Type.DIRECT || proxy.type() == Proxy.Type.HTTP
                ? address.socketFactory().createSocket()
                : new Socket(proxy);

        rawSocket.setSoTimeout(readTimeout);
        try {
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

    public boolean isHealthy(boolean doExtensiveHealthChecks) {
        if (socket.isClosed() || socket.isInputShutdown() || socket.isOutputShutdown()) {
            return false;
        }

        if (doExtensiveHealthChecks) {
            try {
                int readTimeout = socket.getSoTimeout();
                try {
                    if (source.exhausted()) {
                        return false;
                    }
                    return true;
                }finally {
                    socket.setSoTimeout(readTimeout);
                }
            } catch (SocketTimeoutException ignore) {
            } catch (IOException e) {
                return false;
            }
        }
        return true;
    }
}
