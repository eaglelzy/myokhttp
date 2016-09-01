package com.lizy.okhttp;

import com.lizy.okhttp.internal.Internal;
import com.lizy.okhttp.internal.Util;
import com.lizy.okhttp.internal.connection.RealConnection;
import com.lizy.okhttp.internal.connection.RouteDatabase;
import com.lizy.okhttp.internal.connection.StreamAllocation;
import com.lizy.okhttp.internal.tls.CertificateChainCleaner;
import com.lizy.okhttp.internal.tls.OkHostnameVerifier;

import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.List;

import javax.net.SocketFactory;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Created by lizy on 16-8-30.
 */
public final class OkHttpClient implements Cloneable, Call.Factory {
    private static final List<Protocol> DEFAULT_PROTOCOLS = Util.immutableList(
            Protocol.HTTP_2, Protocol.HTTP_1_1);

    private static final List<ConnectionSpec> DEFAULT_CONNECTION_SPECS = Util.immutableList(
            ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT);

    static {
        Internal.instance = new Internal() {

            @Override public boolean connectionBecameIdle(
                    ConnectionPool pool, RealConnection connection) {
                return pool.connectionBecameIdle(connection);
            }

            @Override public RealConnection get(
                    ConnectionPool pool, Address address, StreamAllocation streamAllocation) {
                return pool.get(address, streamAllocation);
            }

            @Override public void put(ConnectionPool pool, RealConnection connection) {
                pool.put(connection);
            }

            @Override public RouteDatabase routeDatabase(ConnectionPool connectionPool) {
                return connectionPool.routeDatabase;
            }

            @Override public StreamAllocation callEngineGetStreamAllocation(Call call) {
                return ((RealCall) call).streamAllocation();
            }

            @Override
            public void apply(ConnectionSpec tlsConfiguration, SSLSocket sslSocket, boolean isFallback) {
                tlsConfiguration.apply(sslSocket, isFallback);
            }

            @Override public HttpUrl getHttpUrlChecked(String url)
                    throws MalformedURLException, UnknownHostException {
                return HttpUrl.getChecked(url);
            }

            @Override public void setCallWebSocket(Call call) {
            }

            @Override
            public void addLenient(Headers.Builder builder, String line) {
                builder.addLenient(line);
            }

            @Override
            public void addLenient(Headers.Builder builder, String name, String value) {
                builder.addLenient(name, value);
            }
        };
    }

    @Override
    public Call newCall(Request request) {
        return new RealCall(this, request);
    }

    final Dispatcher dispatcher;
    final ConnectionPool connectionPool;
    final ProxySelector proxySelector;
    final Dns dns;
    final List<Protocol> protocols;
    final SocketFactory socketFactory;
    final SSLSocketFactory sslSocketFactory;
    final HostnameVerifier hostnameVerifier;
    final CertificatePinner certificatePinner;
    final CertificateChainCleaner certificateChainCleaner;
    final Proxy proxy;
    final Authenticator proxyAuthenticator;
    final Authenticator authenticator;
    final List<ConnectionSpec> connectionSpecs;
    final int connectionTimeout;
    final int readTimeout;
    final int writeTimeout;

    Dispatcher dispatcher() {
        return dispatcher;
    }

    public OkHttpClient() {
        this(new Builder());
    }

    public OkHttpClient(Builder builder) {
        this.dispatcher = builder.dispatcher;
        this.connectionPool = builder.connectionPool;
        this.proxy = builder.proxy;
        this.proxySelector = builder.proxySelector;
        this.dns = builder.dns;
        this.socketFactory = builder.socketFactory;
        this.protocols = builder.protocols;
        this.proxyAuthenticator = builder.proxyAuthenticator;
        this.authenticator = builder.authenticator;
        this.connectionSpecs = builder.connectionSpecs;
        this.connectionTimeout = builder.connectionTimeout;
        this.readTimeout = builder.readTimeout;
        this.writeTimeout = builder.writeTimeout;

        if (builder.sslSocketFactory != null) {
            this.sslSocketFactory = builder.sslSocketFactory;
            this.certificateChainCleaner = builder.certificateChainCleaner;
        } else {
            X509TrustManager trustManager = systemDefaultTrustManager();
            this.sslSocketFactory = systemDefaultSslSocketFactory(trustManager);
            this.certificateChainCleaner = CertificateChainCleaner.get(trustManager);
        }

        this.hostnameVerifier = builder.hostnameVerifier;
        this.certificatePinner = builder.certificatePinner.withCertificateChainCleaner(
                certificateChainCleaner);
    }

    private X509TrustManager systemDefaultTrustManager() {
        try {
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init((KeyStore) null);
            TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
            if (trustManagers.length != 1 || !(trustManagers[0] instanceof X509TrustManager)) {
                throw new IllegalStateException("Unexpected default trust managers:"
                        + Arrays.toString(trustManagers));
            }
            return (X509TrustManager) trustManagers[0];
        } catch (GeneralSecurityException e) {
            throw new AssertionError(); // The system has no TLS. Just give up.
        }
    }

    private SSLSocketFactory systemDefaultSslSocketFactory(X509TrustManager trustManager) {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[] { trustManager }, null);
            return sslContext.getSocketFactory();
        } catch (GeneralSecurityException e) {
            throw new AssertionError(); // The system has no TLS. Just give up.
        }
    }

    public HostnameVerifier hostnameVerifier() {
        return hostnameVerifier;
    }
    public CertificatePinner certificatePinner() {
        return certificatePinner;
    }

    public SSLSocketFactory sslSocketFactory() {
        return sslSocketFactory;
    }

    public int connectionTimeout() {
        return connectionTimeout;
    }

    public int readTimeout() {
        return readTimeout;
    }

    public int writeTimeout() {
        return writeTimeout;
    }

    public List<ConnectionSpec> connectionSpecs() {
        return connectionSpecs;
    }

    public Authenticator proxyAuthenticator() {
        return proxyAuthenticator;
    }

    public Authenticator authenticator() {
        return authenticator;
    }

    public SocketFactory socketFactory() {
        return socketFactory;
    }

    public List<Protocol> protocols() {
        return protocols;
    }

    public Dns dns() {
        return dns;
    }

    public Proxy proxy() {
        return proxy;
    }

    public ProxySelector proxySelector() {
        return proxySelector;
    }

    public ConnectionPool connectionPool() {
        return connectionPool;
    }

    public static class Builder {
        Dispatcher dispatcher;
        ConnectionPool connectionPool;
        SocketFactory socketFactory;
        SSLSocketFactory sslSocketFactory;
        HostnameVerifier hostnameVerifier;
        CertificatePinner certificatePinner;
        CertificateChainCleaner certificateChainCleaner;
        List<Protocol> protocols;
        List<ConnectionSpec> connectionSpecs;
        Proxy proxy;
        ProxySelector proxySelector;
        Dns dns;
        Authenticator proxyAuthenticator;
        Authenticator authenticator;

        final int connectionTimeout;
        final int readTimeout;
        final int writeTimeout;

        public Builder() {
            dispatcher = new Dispatcher();
            connectionPool = new ConnectionPool();
            connectionSpecs = DEFAULT_CONNECTION_SPECS;
            dns = Dns.SYSTEM;
            socketFactory = SocketFactory.getDefault();
            protocols = DEFAULT_PROTOCOLS;
            hostnameVerifier = OkHostnameVerifier.INSTANCE;
            certificatePinner = CertificatePinner.DEFAULT;
            proxySelector = ProxySelector.getDefault();
            proxyAuthenticator = Authenticator.NONE;
            authenticator = Authenticator.NONE;
            connectionTimeout = 10_000;
            readTimeout = 10_000;
            writeTimeout = 10_000;
        }
    }
}
