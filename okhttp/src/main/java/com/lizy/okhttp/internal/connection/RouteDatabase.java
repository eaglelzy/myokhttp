package com.lizy.okhttp.internal.connection;

import com.lizy.okhttp.Route;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Created by lizy on 16-8-31.
 */
public final class RouteDatabase {
    private final Set<Route> failedRoutes = new LinkedHashSet<>();

    public synchronized void failed(Route route) {
        failedRoutes.add(route);
    }

    public synchronized void connected(Route route) {
        failedRoutes.remove(route);
    }

    public synchronized boolean shouldPostpone(Route route) {
        return failedRoutes.contains(route);
    }
}
