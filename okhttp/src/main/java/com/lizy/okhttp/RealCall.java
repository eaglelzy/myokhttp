package com.lizy.okhttp;

import com.lizy.okhttp.internal.NamedRunnable;
import com.lizy.okhttp.internal.cache.CacheInterceptor;
import com.lizy.okhttp.internal.http.CallServerInterceptor;
import com.lizy.okhttp.internal.http.RealInterceptorChain;
import com.lizy.okhttp.internal.http.RetryAndFollowUpInterceptor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by lizy on 16-8-30.
 */
final class RealCall implements Call {

    private boolean executed;

    private final Request originalRequest;
    private final OkHttpClient client;

    public RealCall(OkHttpClient client, Request request) {
        this.client = client;
        this.originalRequest = request;
    }

    @Override
    public Request request() {
        return originalRequest;
    }

    @Override
    public Response execute() throws IOException {
        synchronized (this) {
            if (executed) {
                throw new IllegalStateException("Already Executed!");
            }
            executed = true;
        }

        try {
            client.dispatcher().execute(this);
            Response response = getResponseWithInterceptorChain();
            if (response == null) {
                throw new IOException("Canceled");
            }
            return response;
        } finally {
            client.dispatcher().finished(this);
        }
    }

    private Response getResponseWithInterceptorChain() throws IOException {
        List<Interceptor> interceptors = new ArrayList<>();
        interceptors.add(new RetryAndFollowUpInterceptor());
        interceptors.add(new CacheInterceptor());
        interceptors.add(new CallServerInterceptor());
        Interceptor.Chain chain = new RealInterceptorChain(interceptors, originalRequest,
                null, 0);
        return chain.process(originalRequest);
    }

    @Override
    public void enqueue(Callback callback) {
        synchronized (this) {
            if (executed) throw new IllegalStateException("Already Executed");
            executed = true;
        }
        client.dispatcher().enqueue(new AsyncCall(callback));
    }

    @Override
    public void cancel() {

    }

    @Override
    public boolean isExecuted() {
        return executed;
    }

    @Override
    public boolean isCanceled() {
        return false;
    }

    @Override
    public Call clone() {
        return new RealCall(client, originalRequest);
    }

    final class AsyncCall extends NamedRunnable {
        private final Callback responseCallback;

        private AsyncCall(Callback responseCallback) {
            super("OkHttp %s", redactedUrl());
            this.responseCallback = responseCallback;
        }

        String host() {
            //TODO:
            return originalRequest.toString();
        }

        Request request() {
            return originalRequest;
        }

        RealCall get() {
            return RealCall.this;
        }

        @Override
        public void execute() {
            try {
                Response response = getResponseWithInterceptorChain();
                responseCallback.onResponse(RealCall.this, response);
            } catch (IOException e) {
                responseCallback.onFailure(RealCall.this, e);
            } finally {
                client.dispatcher().finished(this);
            }
        }
    }

    String redactedUrl() {
        //TODO:
        return originalRequest.toString();
    }
}
