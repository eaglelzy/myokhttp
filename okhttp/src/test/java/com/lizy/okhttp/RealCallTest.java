package com.lizy.okhttp;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.fail;

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
        Call call = client.newCall(new Request());
        call.execute();

        try {
            call.execute();
            fail("should exception!");
        }catch (Exception e) {
            assertThat(e).hasMessage("Already Executed!");
        }

        Call call1 = call.clone();
        call1.execute();
    }

    @Test
    public void testEnqueue() throws IOException {
        Call call = client.newCall(new Request());
        for (int i = 0; i < 10; i++) {
            Call newCall = call.clone();
            newCall.enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    System.out.println("failed");
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    System.out.println("successed");
                }
            });
        }

        try {
            Thread.sleep(50 * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @After public void end() throws Exception {

    }
}
