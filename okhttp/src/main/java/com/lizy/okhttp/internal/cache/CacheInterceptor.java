package com.lizy.okhttp.internal.cache;

import com.lizy.okhttp.Interceptor;
import com.lizy.okhttp.Response;

import java.io.IOException;

/**
 * Created by lizy on 16-8-30.
 */
public final class CacheInterceptor implements Interceptor {

    private Response cacheResponse;

    @Override
    public Response intercept(Chain chain) throws IOException {
        if (cacheResponse != null) {
            System.out.println("cache hit!");
            return cacheResponse;
        }
        Response response = chain.proceed(chain.request());
        cacheResponse = response;
        return response;
    }
}
