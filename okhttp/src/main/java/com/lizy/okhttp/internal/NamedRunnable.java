package com.lizy.okhttp.internal;

/**
 * Created by lizy on 16-8-30.
 */
public abstract class NamedRunnable implements Runnable {

    private final String name;

    public NamedRunnable(String format, Object... args) {
        this.name = String.format(format, args);
    }

    @Override
    public void run() {
        String oldName = Thread.currentThread().getName();
        Thread.currentThread().setName(name);
        try {
            execute();
        }finally {
            Thread.currentThread().setName(oldName);
        }
    }

    public abstract void execute();
}
