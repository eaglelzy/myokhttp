package com.lizy.okhttp.internal.http;

import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;

/**
 * Created by lizy on 16-8-31.
 */
public class RecordingProxySelector extends ProxySelector {
    @Override
    public List<Proxy> select(URI uri) {
        return null;
    }

    @Override
    public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {

    }
}
