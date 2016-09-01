package com.lizy.okhttp.internal.http;

import com.lizy.okhttp.HttpCodec;
import com.lizy.okhttp.Interceptor;
import com.lizy.okhttp.Request;
import com.lizy.okhttp.Response;
import com.lizy.okhttp.internal.connection.StreamAllocation;

import java.io.IOException;
import java.net.ProtocolException;

import okio.BufferedSink;
import okio.Okio;
import okio.Sink;

/**
 * Created by lizy on 16-8-30.
 */
public final class CallServerInterceptor implements Interceptor {

    private final boolean forWebSocket;

    public CallServerInterceptor(boolean forWebSocket) {
        this.forWebSocket = forWebSocket;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        HttpCodec httpCodec = ((RealInterceptorChain) chain).httpStream();
        StreamAllocation streamAllocation = ((RealInterceptorChain) chain).streamAllocation();
        Request request = chain.request();

        long sentRequestMillis = System.currentTimeMillis();
        httpCodec.writeRequestHeaders(request);

        if (HttpMethod.permitsRequestBody(request.method()) && request.body() != null) {
            Sink requestBodyOut = httpCodec.createRequestBody(request, request.body().contentLength());
            BufferedSink bufferedRequestBody = Okio.buffer(requestBodyOut);
            request.body().writeTo(bufferedRequestBody);
            bufferedRequestBody.close();
        }

        httpCodec.finishRequest();

        Response response = httpCodec.readResponseHeaders()
                .request(request)
//                .handshake(streamAllocation.connection().handshake())
                .sentRequestAtMillis(sentRequestMillis)
                .receivedResponseAtMillis(System.currentTimeMillis())
                .build();

        if (!forWebSocket || response.code() != 101) {
            response = response.newBuilder()
                    .body(httpCodec.openResponseBody(response))
                    .build();
        }

//        if ("close".equalsIgnoreCase(response.request().header("Connection"))
//                || "close".equalsIgnoreCase(response.header("Connection"))) {
//            streamAllocation.noNewStreams();
//        }

        int code = response.code();
        if ((code == 204 || code == 205) && response.body().contentLength() > 0) {
            throw new ProtocolException(
                    "HTTP " + code + " had non-zero Content-Length: " + response.body().contentLength());
        }

        return response;
    }
}
