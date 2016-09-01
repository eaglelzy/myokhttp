package com.lizy.okhttp.internal.http;

import com.lizy.okhttp.Connection;
import com.lizy.okhttp.HttpCodec;
import com.lizy.okhttp.Interceptor;
import com.lizy.okhttp.Request;
import com.lizy.okhttp.Response;
import com.lizy.okhttp.internal.connection.StreamAllocation;

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

    private final StreamAllocation streamAllocation;
    private final HttpCodec httpCodec;

    public RealInterceptorChain(List<Interceptor> interceptors,
                                Request request,
                                Connection connection,
                                int index,
                                StreamAllocation streamAllocation,
                                HttpCodec httpCodec) {
        this.interceptors = interceptors;
        this.request = request;
        this.connection = connection;
        this.index = index;
        this.streamAllocation = streamAllocation;
        this.httpCodec = httpCodec;
    }

    public StreamAllocation streamAllocation() {
        return streamAllocation;
    }

    public HttpCodec httpStream() {
        return httpCodec;
    }

    @Override
    public Request request() {
        return request;
    }

    @Override
    public Response proceed(Request request) throws IOException {
        return proceed(request, streamAllocation, httpCodec, connection);
    }

    public Response proceed(Request request, StreamAllocation streamAllocation,
                            HttpCodec httpCodec, Connection connection) throws IOException {

        if (index > interceptors.size()) throw new AssertionError();

        RealInterceptorChain next = new RealInterceptorChain(interceptors, request,
                connection, index + 1, streamAllocation, httpCodec);
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
