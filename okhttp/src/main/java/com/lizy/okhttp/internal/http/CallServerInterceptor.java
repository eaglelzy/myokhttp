package com.lizy.okhttp.internal.http;

import com.lizy.okhttp.Interceptor;
import com.lizy.okhttp.Response;

import java.io.IOException;

/**
 * Created by lizy on 16-8-30.
 */
public final class CallServerInterceptor implements Interceptor {
    @Override
    public Response intercept(Chain chan) throws IOException {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return new Response();
    }
}
