package com.lizy.okhttp;

import com.lizy.okhttp.internal.Util;

import java.net.Proxy;
import java.net.ProxySelector;
import java.util.List;

import javax.net.SocketFactory;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;

/**
 * Created by lizy on 16-8-31.
 */
public final class Address {
    final HttpUrl httpUrl;
    final Dns dns;
    final SocketFactory socketFactory;
    final Authenticator proxyAuthenticator;
    final List<Protocol> protocols;
    final List<ConnectionSpec> connectionSpecs;
    final ProxySelector proxySelector;
    final Proxy proxy;
    final SSLSocketFactory sslSocketFactory;
    final HostnameVerifier hostnameVerifier;
    final CertificatePinner certificatePinner;

    public Address(String uriHost,
                   int uriPort,
                   Dns dns,
                   SocketFactory socketFactory,
                   Authenticator proxyAuthenticator,
                   List<Protocol> protocols,
                   List<ConnectionSpec> connectionSpecs,
                   ProxySelector proxySelector,
                   Proxy proxy,
                   SSLSocketFactory sslSocketFactory,
                   HostnameVerifier hostnameVerifier,
                   CertificatePinner certificatePinner) {
        httpUrl = new HttpUrl.Builder()
                .scheme(sslSocketFactory != null ? "https" : "http")
                .host(uriHost)
                .port(uriPort)
                .build();

        this.dns = Util.checkNull(dns, "dns == null");
        this.socketFactory = Util.checkNull(socketFactory, "socketFactory == null");
        this.proxyAuthenticator = Util.checkNull(proxyAuthenticator, "proxyAuthenticator == null");
        this.protocols = Util.checkNull(protocols, "protocols == null");
        this.connectionSpecs = Util.checkNull(connectionSpecs, "connectionSpecs == null");
        this.proxySelector = Util.checkNull(proxySelector, "proxySelector == null");

        this.proxy = proxy;
        this.sslSocketFactory = sslSocketFactory;
        this.hostnameVerifier = hostnameVerifier;
        this.certificatePinner = certificatePinner;
    }

    public HttpUrl httpUrl() {
        return httpUrl;
    }

    public Dns dns() {
        return dns;
    }

    public SocketFactory socketFactory() {
        return socketFactory;
    }

    public Authenticator proxyAuthenticator() {
        return proxyAuthenticator;
    }

    public List<Protocol> protocols() {
        return protocols;
    }

    public List<ConnectionSpec> connectionSpecs() {
        return connectionSpecs;
    }

    public ProxySelector proxySelector() {
        return proxySelector;
    }

    public Proxy proxy() {
        return proxy;
    }

    public SSLSocketFactory sslSocketFactory() {
        return sslSocketFactory;
    }

    public HostnameVerifier hostnameVerifier() {
        return hostnameVerifier;
    }

    public CertificatePinner certificatePinner() {
        return certificatePinner;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Address address = (Address) o;

        if (!httpUrl.equals(address.httpUrl)) return false;
        if (!dns.equals(address.dns)) return false;
        if (!socketFactory.equals(address.socketFactory)) return false;
        if (!proxyAuthenticator.equals(address.proxyAuthenticator)) return false;
        if (!protocols.equals(address.protocols)) return false;
        if (!connectionSpecs.equals(address.connectionSpecs)) return false;
        if (!proxySelector.equals(address.proxySelector)) return false;
        if (!Util.equal(proxy, address.proxy)) return false;
        if (!Util.equal(sslSocketFactory, address.sslSocketFactory)) return false;
        if (!Util.equal(hostnameVerifier, address.hostnameVerifier)) return false;
        return Util.equal(certificatePinner, address.certificatePinner);

    }

    @Override
    public int hashCode() {
        int result = httpUrl.hashCode();
        result = 31 * result + dns.hashCode();
        result = 31 * result + socketFactory.hashCode();
        result = 31 * result + proxyAuthenticator.hashCode();
        result = 31 * result + protocols.hashCode();
        result = 31 * result + connectionSpecs.hashCode();
        result = 31 * result + proxySelector.hashCode();
        result = 31 * result + (proxy == null ? 0 : proxy.hashCode());
        result = 31 * result + (sslSocketFactory == null ? 0 : sslSocketFactory.hashCode());
        result = 31 * result + (hostnameVerifier == null ? 0 : hostnameVerifier.hashCode());
        result = 31 * result + (certificatePinner == null ? 0 : certificatePinner.hashCode());
        return result;
    }
}
