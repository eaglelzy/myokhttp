package com.lizy.okhttp;

import java.io.IOException;

/**
 * Created by lizy on 16-8-30.
 */
public interface Call extends Cloneable {

    Request request();

    Response execute() throws IOException;

    void enqueue(Callback callback);

    void cancel();

    boolean isExecuted();
    boolean isCanceled();

    Call clone();

    interface Factory {
        Call newCall(Request request);
    }
}
