package com.lizy.okhttp;

import com.lizy.okhttp.RealCall.AsyncCall;
import com.lizy.okhttp.internal.Util;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by lizy on 16-8-30.
 */
public final class Dispatcher {

    private int maxRequest = 64;
    private int maxRequestPerHost = 5;
    private ExecutorService executorService;

    private Runnable idleCallback;

    private final Deque<AsyncCall> readyAsyncCalls = new ArrayDeque<>();
    private final Deque<AsyncCall> runningAsyncCalls = new ArrayDeque<>();
    private final Deque<RealCall> runningSyncCalls = new ArrayDeque<>();

    public Dispatcher(ExecutorService executorService) {
        this.executorService = executorService;
    }
    public Dispatcher() {

    }

    public synchronized ExecutorService executorService() {
        if (executorService == null) {
            executorService = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60, TimeUnit.SECONDS,
                    new SynchronousQueue<>(), Util.threadFactory("Okhttp Dispatcher", false));
        }
        return executorService;
    }

    synchronized void execute(RealCall realCall) {
        runningSyncCalls.add(realCall);
    }

    synchronized void enqueue(AsyncCall call) {
        if (runningAsyncCalls.size() < maxRequest && runningCallsForHost(call) < maxRequestPerHost) {
            runningAsyncCalls.add(call);
            executorService().execute(call);
        } else {
            readyAsyncCalls.add(call);
        }
    }

    void finished(RealCall call) {
        finished(runningSyncCalls, call, false);
    }

    void finished(AsyncCall call) {
        finished(runningAsyncCalls, call, true);
    }

    private <T> void finished(Deque<T> calls, T call, boolean promoteCalls) {
        int runningCallsCount;
        Runnable idleCallback;
        synchronized (this) {
            if (!calls.remove(call)) throw new AssertionError("Call wan't in-flight!");
            if (promoteCalls) promoteCalls();
            runningCallsCount = runningCallsCount();
            idleCallback = this.idleCallback;
        }

        if (runningCallsCount == 0 && idleCallback != null) {
            idleCallback.run();
        }
    }

    private void promoteCalls() {
        if (runningAsyncCalls.size() > maxRequest) return;
        if (readyAsyncCalls.isEmpty()) return;

        for (Iterator<AsyncCall> i = readyAsyncCalls.iterator(); i.hasNext(); ) {
            AsyncCall call = i.next();

            if (runningCallsForHost(call) < maxRequestPerHost) {
                i.remove();
                runningAsyncCalls.add(call);
                executorService().execute(call);
            }

            if (runningAsyncCalls.size() > maxRequest) return;
        }
    }

    private int runningCallsForHost(AsyncCall call) {
        int result = 0;
        for (AsyncCall c : runningAsyncCalls) {
            if (c.host().equals(call.host())) result++;
        }
        return result;
    }

    private int runningCallsCount() {
        return runningSyncCalls.size() + runningAsyncCalls.size();
    }
}
