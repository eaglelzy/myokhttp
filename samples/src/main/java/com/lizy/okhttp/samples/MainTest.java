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
                .url(HttpUrl.parse("http://publicobject.com/helloworld.txt"))
                .build();

        Call call = client.newCall(request);

        try {
            Response response = call.execute();
            System.out.println("response:" + response.body().string());
        } catch (IOException e) {
            e.printStackTrace();
        }

        Call call1 = call.clone();
        call1.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {

            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                System.out.println("response:" + response.body().string());
            }
        });

    }
}
