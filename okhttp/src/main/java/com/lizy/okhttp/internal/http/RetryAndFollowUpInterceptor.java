package com.lizy.okhttp.internal.http;

import com.lizy.okhttp.Interceptor;
import com.lizy.okhttp.Response;

import java.io.IOException;

/**
 * Created by lizy on 16-8-30.
 */
public final class RetryAndFollowUpInterceptor implements Interceptor {
    @Override
    public Response intercept(Chain chain) throws IOException {
        return chain.process(chain.request());
    }
}
