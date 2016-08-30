package com.lizy.okhttp.internal.http;

import com.lizy.okhttp.Connection;
import com.lizy.okhttp.Interceptor;
import com.lizy.okhttp.Request;
import com.lizy.okhttp.Response;

import java.io.IOException;
import java.util.List;

/**
 * Created by lizy on 16-8-30.
 */
public final class RealInterceptorChain implements Interceptor.Chain {

    private final List<Interceptor> interceptors;
    private final Request request;
    private final Connection connection;
    private final int index;

    public RealInterceptorChain(List<Interceptor> interceptors,
                                Request request,
                                Connection connection,
                                int index) {
        this.interceptors = interceptors;
        this.request = request;
        this.connection = connection;
        this.index = index;
    }

    @Override
    public Request request() {
        return request;
    }

    @Override
    public Response process(Request request) throws IOException {

        RealInterceptorChain next = new RealInterceptorChain(interceptors, request,
                connection, index + 1);
        Interceptor interceptor = interceptors.get(index);
        Response response = interceptor.intercept(next);

        if (response == null) {
            throw new NullPointerException("interceptor " + interceptor + " return null!");
        }
        return response;
    }

    @Override
    public Connection connection() {
        return connection;
    }
}
