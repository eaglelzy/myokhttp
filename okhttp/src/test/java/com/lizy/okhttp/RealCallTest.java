package com.lizy.okhttp;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Created by lizy on 16-8-30.
 */
public class RealCallTest {

    private OkHttpClient client;

    @Before
    public void setUp() {
        client = new OkHttpClient();
    }

    @Test
    public void testExecute() throws IOException {
        Request request = new Request.Builder()
                .url(HttpUrl.parse("http://publicobject.com/helloworld.txt"))
                .build();
        Call call = client.newCall(request);
        Response response = call.execute();
        System.out.print(response.body().string());
    }

    @Test
    public void testEnqueue() throws IOException {

        Request request = new Request.Builder()
                .url(HttpUrl.parse("http://publicobject.com/helloworld.txt"))
                .build();
        Call call = client.newCall(request);
        for (int i = 0; i < 1; i++) {
            Call newCall = call.clone();
            newCall.enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    System.out.println("failed:" + e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    System.out.println(response.body().string());
                }
            });
        }

        try {
            Thread.sleep(10 * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @After public void end() throws Exception {

    }

    @Test public void socket() throws Exception {
        Socket socket = new Socket();//"www.javathinker.com", 80);
        socket.connect(new InetSocketAddress("45.78.13.238", 5222), 10_000);
    }
}
