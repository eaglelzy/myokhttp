package com.lizy.okhttp.internal;

import java.util.concurrent.ThreadFactory;

/**
 * Created by lizy on 16-8-30.
 */
public class Util {
    private Util(){}


    public static ThreadFactory threadFactory(String name, boolean deamon) {
        return new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, name);
                thread.setDaemon(deamon);
                return thread;
            }
        };
    }
}
