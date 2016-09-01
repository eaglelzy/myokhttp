package com.lizy.okhttp;

import java.net.Socket;

/**
 * Created by lizy on 16-8-30.
 */
public interface Connection {
    Route route();

    Socket socket();

//    Handshake handshake();

    Protocol protocol();
}
