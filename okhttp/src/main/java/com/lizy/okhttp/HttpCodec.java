package com.lizy.okhttp;

import java.io.IOException;

import okio.Sink;

/**
 * Created by lizy on 16-8-31.
 */
public interface HttpCodec {

    int DISCARD_STREAM_TIMEOUT_MILLIS = 100;

    Sink createRequestBody(Request request, long contentLength);

    void writeRequestHeaders(Request request) throws IOException;

    void finishRequest() throws IOException;

    Response.Builder readResponseHeaders() throws IOException;

    ResponseBody openResponseBody(Response response) throws IOException;

    void cancel();
}
