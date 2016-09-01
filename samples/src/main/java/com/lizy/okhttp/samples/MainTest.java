package com.lizy.okhttp.samples;

import com.lizy.okhttp.Call;
import com.lizy.okhttp.Callback;
import com.lizy.okhttp.HttpUrl;
import com.lizy.okhttp.OkHttpClient;
import com.lizy.okhttp.Request;
import com.lizy.okhttp.Response;

import java.io.IOException;

public class MainTest {
    public static void main(String[] args) {
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
//                .url(HttpUrl.parse("http://publicobject.com/helloworld.txt"))
                .url(HttpUrl.parse("https://api.github.com"))
//                .url(HttpUrl.parse("https://kyfw.12306.cn/otn/"))
                .build();

        Call call = client.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                System.out.println("failure:" + e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                System.out.println("response:" + response.body().string());
            }
        });

    }
}
