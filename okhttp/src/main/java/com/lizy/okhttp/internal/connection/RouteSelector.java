package com.lizy.okhttp.internal.connection;

import com.lizy.okhttp.Address;
import com.lizy.okhttp.Route;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;

/**
 * Created by lizy on 16-9-1.
 */
public class RouteSelector {

    private final Address address;
    private final RouteDatabase routeDatabase;

    public RouteSelector(Address address, RouteDatabase routeDatabase) {
        this.address = address;
        this.routeDatabase = routeDatabase;
    }

    public Route next() throws IOException {
        return new Route(address, Proxy.NO_PROXY,
                new InetSocketAddress(address.httpUrl().host(), address.httpUrl().port()));
    }
}
