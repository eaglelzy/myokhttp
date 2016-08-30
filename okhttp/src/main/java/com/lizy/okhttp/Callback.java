package com.lizy.okhttp;

import java.io.IOException;

/**
 * Created by lizy on 16-8-30.
 */
public interface Callback {

    void onFailure(Call call, IOException e);

    void onResponse(Call call, Response response) throws IOException;
}
