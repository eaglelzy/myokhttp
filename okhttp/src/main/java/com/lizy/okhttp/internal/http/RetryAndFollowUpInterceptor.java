package com.lizy.okhttp.internal.http;

import com.lizy.okhttp.Address;
import com.lizy.okhttp.CertificatePinner;
import com.lizy.okhttp.HttpUrl;
import com.lizy.okhttp.Interceptor;
import com.lizy.okhttp.OkHttpClient;
import com.lizy.okhttp.Request;
import com.lizy.okhttp.Response;
import com.lizy.okhttp.internal.connection.StreamAllocation;

import java.io.IOException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;

/**
 * Created by lizy on 16-8-30.
 */
public final class RetryAndFollowUpInterceptor implements Interceptor {

    private StreamAllocation streamAllocation;
    private final OkHttpClient client;

    public RetryAndFollowUpInterceptor(OkHttpClient client) {
        this.client = client;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        streamAllocation = new StreamAllocation(client.connectionPool(),
                createAddress(request.url()), null);

        Response response = ((RealInterceptorChain) chain).proceed(request, streamAllocation, null, null);
        return response;
    }

    private Address createAddress(HttpUrl url) {
        SSLSocketFactory sslSocketFactory = null;
        HostnameVerifier hostnameVerifier = null;
        CertificatePinner certificatePinner = null;
        if (url.isHttps()) {
            sslSocketFactory = client.sslSocketFactory();
            hostnameVerifier = client.hostnameVerifier();
            certificatePinner = client.certificatePinner();
        }

        return new Address(url.host(), url.port(), client.dns(), client.socketFactory(),
                client.authenticator(), client.protocols(), client.connectionSpecs(),
                client.proxySelector(), client.proxy(),
                sslSocketFactory, hostnameVerifier, certificatePinner);
    }

    public StreamAllocation streamAllocation() {
        return streamAllocation;
    }
}
