package com.lizy.okhttp.internal.platform;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Created by lizy on 16-8-31.
 */
public class Platform {

    private static final Platform PLATFORM = findPlatform();

    public static Platform get() {
        return PLATFORM;
    }

    private static Platform findPlatform() {

        // Probably an Oracle JDK like OpenJDK.
        return new Platform();
    }
    public void connectSocket(Socket socket, InetSocketAddress address,
                              int connectTimeout) throws IOException {
        socket.connect(address, connectTimeout);
    }
}
