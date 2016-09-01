package com.lizy.okhttp.internal.connection;

import com.lizy.okhttp.HttpCodec;
import com.lizy.okhttp.Interceptor;
import com.lizy.okhttp.OkHttpClient;
import com.lizy.okhttp.Request;
import com.lizy.okhttp.Response;
import com.lizy.okhttp.internal.http.RealInterceptorChain;

import java.io.IOException;

/**
 * Created by lizy on 16-8-31.
 */
public final class ConnectionInterceptor implements Interceptor {
    private final OkHttpClient client;

    public ConnectionInterceptor(OkHttpClient client) {
        this.client = client;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        RealInterceptorChain realChain = (RealInterceptorChain) chain;
        Request request = realChain.request();
        StreamAllocation streamAllocation = realChain.streamAllocation();

        // We need the network to satisfy this request. Possibly for validating a conditional GET.
        boolean doExtensiveHealthChecks = !request.method().equals("GET");
        HttpCodec httpCodec = streamAllocation.newStream(client, doExtensiveHealthChecks);
        RealConnection connection = streamAllocation.connection();

        return realChain.proceed(request, streamAllocation, httpCodec, connection);
    }
}
