package com.lizy.okhttp;

import java.io.IOException;

/**
 * Created by lizy on 16-8-30.
 */
public interface Interceptor {
    Response intercept(Chain chain) throws IOException;

    interface Chain {
        Request request();
        Response proceed(Request request) throws IOException;

        Connection connection();
    }
}
