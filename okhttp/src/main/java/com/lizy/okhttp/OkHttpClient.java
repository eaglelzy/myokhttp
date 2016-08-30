package com.lizy.okhttp;

/**
 * Created by lizy on 16-8-30.
 */
public final class OkHttpClient implements Cloneable, Call.Factory {

    @Override
    public Call newCall(Request request) {
        return new RealCall(this, request);
    }

    final Dispatcher dispatcher;

    Dispatcher dispatcher() {
        return dispatcher;
    }

    public OkHttpClient() {
        this(new Builder());
    }

    public OkHttpClient(Builder builder) {
        this.dispatcher = builder.dispatcher;
    }

    public static class Builder {
        Dispatcher dispatcher;

        public Builder() {
            dispatcher = new Dispatcher();
        }
    }
}
