/*
 * Copyright (C) 2014 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lizy.okhttp.internal;

import com.lizy.okhttp.Address;
import com.lizy.okhttp.Call;
import com.lizy.okhttp.ConnectionPool;
import com.lizy.okhttp.ConnectionSpec;
import com.lizy.okhttp.Headers;
import com.lizy.okhttp.HttpUrl;
import com.lizy.okhttp.OkHttpClient;
import com.lizy.okhttp.internal.connection.RealConnection;
import com.lizy.okhttp.internal.connection.RouteDatabase;
import com.lizy.okhttp.internal.connection.StreamAllocation;

import java.net.MalformedURLException;
import java.net.UnknownHostException;

import javax.net.ssl.SSLSocket;

/**
 * Escalate internal APIs in {@code okhttp3} so they can be used from OkHttp's implementation
 * packages. The only implementation of this interface is in {@link OkHttpClient}.
 */
public abstract class Internal {

  public static void initializeInstanceForTests() {
    // Needed in tests to ensure that the instance is actually pointing to something.
    new OkHttpClient();
  }

  public static Internal instance;

  public abstract RealConnection get(
          ConnectionPool pool, Address address, StreamAllocation streamAllocation);

  public abstract void put(ConnectionPool pool, RealConnection connection);

  public abstract boolean connectionBecameIdle(ConnectionPool pool, RealConnection connection);

  public abstract RouteDatabase routeDatabase(ConnectionPool connectionPool);

  public abstract void apply(ConnectionSpec tlsConfiguration, SSLSocket sslSocket,
                             boolean isFallback);

  public abstract HttpUrl getHttpUrlChecked(String url)
      throws MalformedURLException, UnknownHostException;

  public abstract StreamAllocation callEngineGetStreamAllocation(Call call);

  public abstract void setCallWebSocket(Call call);

  public abstract void addLenient(Headers.Builder builder, String line);
}
