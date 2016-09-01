package com.lizy.okhttp;

import java.io.IOException;

/**
 * Created by lizy on 16-8-31.
 */
public interface Authenticator {

    Authenticator NONE = new Authenticator() {
        @Override
        public Request authenticate(Route route, Response response) throws IOException {
            return null;
        }
    };

    Request authenticate(Route route, Response response) throws IOException;
}
