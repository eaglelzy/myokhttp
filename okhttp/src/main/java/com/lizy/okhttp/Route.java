package com.lizy.okhttp;

import com.lizy.okhttp.internal.Util;

import java.net.InetSocketAddress;
import java.net.Proxy;

/**
 * Created by lizy on 16-8-31.
 */
public final class Route {
    final Address address;
    final Proxy proxy;
    final InetSocketAddress inetSocketAddress;

    public Route(Address address, Proxy proxy, InetSocketAddress inetSocketAddress) {
        this.address = Util.checkNull(address, "address == null");
        this.proxy = Util.checkNull(proxy, "proxy == null");
        this.inetSocketAddress = Util.checkNull(inetSocketAddress, "inetSocketAddress == null");
    }

    public Address address() {
        return address;
    }

    public Proxy proxy() {
        return proxy;
    }

    public InetSocketAddress socketAddress() {
        return inetSocketAddress;
    }

    public boolean requiresTunnel() {
        return address.sslSocketFactory() != null && proxy.type() == Proxy.Type.HTTP;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Route route = (Route) o;

        if (!address.equals(route.address)) return false;
        if (!proxy.equals(route.proxy)) return false;
        return inetSocketAddress.equals(route.inetSocketAddress);

    }

    @Override
    public int hashCode() {
        int result = address.hashCode();
        result = 31 * result + proxy.hashCode();
        result = 31 * result + inetSocketAddress.hashCode();
        return result;
    }
}
