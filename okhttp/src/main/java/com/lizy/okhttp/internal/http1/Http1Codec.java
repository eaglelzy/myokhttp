package com.lizy.okhttp.internal.http1;

import com.lizy.okhttp.Headers;
import com.lizy.okhttp.HttpCodec;
import com.lizy.okhttp.HttpUrl;
import com.lizy.okhttp.OkHttpClient;
import com.lizy.okhttp.Request;
import com.lizy.okhttp.Response;
import com.lizy.okhttp.ResponseBody;
import com.lizy.okhttp.internal.Internal;
import com.lizy.okhttp.internal.Util;
import com.lizy.okhttp.internal.connection.StreamAllocation;
import com.lizy.okhttp.internal.http.HttpHeaders;
import com.lizy.okhttp.internal.http.RealResponseBody;
import com.lizy.okhttp.internal.http.RequestLine;
import com.lizy.okhttp.internal.http.StatusLine;

import java.io.EOFException;
import java.io.IOException;
import java.net.ProtocolException;

import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.ForwardingTimeout;
import okio.Okio;
import okio.Sink;
import okio.Source;
import okio.Timeout;

import static com.lizy.okhttp.internal.http.StatusLine.HTTP_CONTINUE;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Created by lizy on 16-8-31.
 */
public class Http1Codec implements HttpCodec {
    private static final int STATE_IDLE = 0; // Idle connections are ready to write request headers.
    private static final int STATE_OPEN_REQUEST_BODY = 1;
    private static final int STATE_WRITING_REQUEST_BODY = 2;
    private static final int STATE_READ_RESPONSE_HEADERS = 3;
    private static final int STATE_OPEN_RESPONSE_BODY = 4;
    private static final int STATE_READING_RESPONSE_BODY = 5;
    private static final int STATE_CLOSED = 6;

    /** The client that configures this stream. May be null for HTTPS proxy tunnels. */
    private final OkHttpClient client;
    /** The stream allocation that owns this stream. May be null for HTTPS proxy tunnels. */
    private final StreamAllocation streamAllocation;

    private final BufferedSource source;
    private final BufferedSink sink;
    private int state = STATE_IDLE;

    public Http1Codec(OkHttpClient client, StreamAllocation streamAllocation,
                      BufferedSource source, BufferedSink sink) {
        this.client = client;
        this.streamAllocation = streamAllocation;
        this.source = source;
        this.sink = sink;
    }

    @Override
    public Sink createRequestBody(Request request, long contentLength) {
        return null;
    }

    @Override
    public void writeRequestHeaders(Request request) throws IOException {
        String requestLine = RequestLine.get(
                request, streamAllocation.connection().route().proxy().type());
        writeRequest(request.headers(), requestLine);
    }

    /** Returns bytes of a request header for sending on an HTTP transport. */
    public void writeRequest(Headers headers, String requestLine) throws IOException {
        if (state != STATE_IDLE) throw new IllegalStateException("state: " + state);
        sink.writeUtf8(requestLine).writeUtf8("\r\n");
        for (int i = 0, size = headers.size(); i < size; i++) {
            sink.writeUtf8(headers.name(i))
                    .writeUtf8(": ")
                    .writeUtf8(headers.value(i))
                    .writeUtf8("\r\n");
        }
        sink.writeUtf8("\r\n");
        state = STATE_OPEN_REQUEST_BODY;
    }

    @Override
    public void finishRequest() throws IOException {
        sink.flush();
    }

    @Override
    public Response.Builder readResponseHeaders() throws IOException {
        return readResponse();
    }

    /** Parses bytes of a response header from an HTTP transport. */
    public Response.Builder readResponse() throws IOException {
        if (state != STATE_OPEN_REQUEST_BODY && state != STATE_READ_RESPONSE_HEADERS) {
            throw new IllegalStateException("state: " + state);
        }

        try {
            while (true) {
                StatusLine statusLine = StatusLine.parse(source.readUtf8LineStrict());

                Response.Builder responseBuilder = new Response.Builder()
                        .protocol(statusLine.protocol)
                        .code(statusLine.code)
                        .message(statusLine.message)
                        .headers(readHeaders());

                if (statusLine.code != HTTP_CONTINUE) {
                    state = STATE_OPEN_RESPONSE_BODY;
                    return responseBuilder;
                }
            }
        } catch (EOFException e) {
            // Provide more context if the server ends the stream before sending a response.
            IOException exception = new IOException("unexpected end of stream on " + streamAllocation);
            exception.initCause(e);
            throw exception;
        }
    }

    /** Reads headers or trailers. */
    public Headers readHeaders() throws IOException {
        Headers.Builder headers = new Headers.Builder();
        // parse the result headers until the first blank line
        for (String line; (line = source.readUtf8LineStrict()).length() != 0; ) {
            Internal.instance.addLenient(headers, line);
        }
        return headers.build();
    }

    @Override
    public ResponseBody openResponseBody(Response response) throws IOException {
        Source source = getTransferStream(response);
        return new RealResponseBody(response.headers(), Okio.buffer(source));
    }

    private Source getTransferStream(Response response) throws IOException {
        if (!HttpHeaders.hasBody(response)) {
            return newFixedLengthSource(0);
        }

        if ("chunked".equalsIgnoreCase(response.header("Transfer-Encoding"))) {
            return newChunkedSource(response.request().url());
        }

        long contentLength = HttpHeaders.contentLength(response);
        if (contentLength != -1) {
            return newFixedLengthSource(contentLength);
        }

        // Wrap the input stream from the connection (rather than just returning
        // "socketIn" directly here), so that we can control its use after the
        // reference escapes.
        return newUnknownLengthSource();
    }

    @Override
    public void cancel() {

    }

    public Source newFixedLengthSource(long length) throws IOException {
        if (state != STATE_OPEN_RESPONSE_BODY) throw new IllegalStateException("state: " + state);
        state = STATE_READING_RESPONSE_BODY;
        return new FixedLengthSource(length);
    }

    public Source newChunkedSource(HttpUrl url) throws IOException {
        if (state != STATE_OPEN_RESPONSE_BODY) throw new IllegalStateException("state: " + state);
        state = STATE_READING_RESPONSE_BODY;
        return new ChunkedSource(url);
    }

    public Source newUnknownLengthSource() throws IOException {
        if (state != STATE_OPEN_RESPONSE_BODY) throw new IllegalStateException("state: " + state);
        if (streamAllocation == null) throw new IllegalStateException("streamAllocation == null");
        state = STATE_READING_RESPONSE_BODY;
        streamAllocation.noNewStreams();
        return new UnknownLengthSource();
    }

    private abstract class AbstractSource implements Source {
        protected final ForwardingTimeout timeout = new ForwardingTimeout(source.timeout());
        protected boolean closed;

        @Override public Timeout timeout() {
            return timeout;
        }

        /**
         * Closes the cache entry and makes the socket available for reuse. This should be invoked when
         * the end of the body has been reached.
         */
        protected final void endOfInput(boolean reuseConnection) throws IOException {
            if (state == STATE_CLOSED) return;
            if (state != STATE_READING_RESPONSE_BODY) throw new IllegalStateException("state: " + state);

            detachTimeout(timeout);

            state = STATE_CLOSED;
            if (streamAllocation != null) {
                streamAllocation.streamFinished(!reuseConnection, Http1Codec.this);
            }
        }
    }
    private void detachTimeout(ForwardingTimeout timeout) {
        Timeout oldDelegate = timeout.delegate();
        timeout.setDelegate(Timeout.NONE);
        oldDelegate.clearDeadline();
        oldDelegate.clearTimeout();
    }

    /** An HTTP body with a fixed length specified in advance. */
    private class FixedLengthSource extends AbstractSource {
        private long bytesRemaining;

        public FixedLengthSource(long length) throws IOException {
            bytesRemaining = length;
            if (bytesRemaining == 0) {
                endOfInput(true);
            }
        }

        @Override public long read(Buffer sink, long byteCount) throws IOException {
            if (byteCount < 0) throw new IllegalArgumentException("byteCount < 0: " + byteCount);
            if (closed) throw new IllegalStateException("closed");
            if (bytesRemaining == 0) return -1;

            long read = source.read(sink, Math.min(bytesRemaining, byteCount));
            if (read == -1) {
                endOfInput(false); // The server didn't supply the promised content length.
                throw new ProtocolException("unexpected end of stream");
            }

            bytesRemaining -= read;
            if (bytesRemaining == 0) {
                endOfInput(true);
            }
            return read;
        }

        @Override public void close() throws IOException {
            if (closed) return;

            if (bytesRemaining != 0 && !Util.discard(this, DISCARD_STREAM_TIMEOUT_MILLIS, MILLISECONDS)) {
                endOfInput(false);
            }

            closed = true;
        }
    }

    /** An HTTP body with alternating chunk sizes and chunk bodies. */
    private class ChunkedSource extends AbstractSource {
        private static final long NO_CHUNK_YET = -1L;
        private final HttpUrl url;
        private long bytesRemainingInChunk = NO_CHUNK_YET;
        private boolean hasMoreChunks = true;

        ChunkedSource(HttpUrl url) {
            this.url = url;
        }

        @Override public long read(Buffer sink, long byteCount) throws IOException {
            if (byteCount < 0) throw new IllegalArgumentException("byteCount < 0: " + byteCount);
            if (closed) throw new IllegalStateException("closed");
            if (!hasMoreChunks) return -1;

            if (bytesRemainingInChunk == 0 || bytesRemainingInChunk == NO_CHUNK_YET) {
                readChunkSize();
                if (!hasMoreChunks) return -1;
            }

            long read = source.read(sink, Math.min(byteCount, bytesRemainingInChunk));
            if (read == -1) {
                endOfInput(false); // The server didn't supply the promised chunk length.
                throw new ProtocolException("unexpected end of stream");
            }
            bytesRemainingInChunk -= read;
            return read;
        }

        private void readChunkSize() throws IOException {
            // Read the suffix of the previous chunk.
            if (bytesRemainingInChunk != NO_CHUNK_YET) {
                source.readUtf8LineStrict();
            }
            try {
                bytesRemainingInChunk = source.readHexadecimalUnsignedLong();
                String extensions = source.readUtf8LineStrict().trim();
                if (bytesRemainingInChunk < 0 || (!extensions.isEmpty() && !extensions.startsWith(";"))) {
                    throw new ProtocolException("expected chunk size and optional extensions but was \""
                            + bytesRemainingInChunk + extensions + "\"");
                }
            } catch (NumberFormatException e) {
                throw new ProtocolException(e.getMessage());
            }
            if (bytesRemainingInChunk == 0L) {
                hasMoreChunks = false;
//                HttpHeaders.receiveHeaders(client.cookieJar(), url, readHeaders());
                endOfInput(true);
            }
        }

        @Override public void close() throws IOException {
            if (closed) return;
            if (hasMoreChunks && !Util.discard(this, DISCARD_STREAM_TIMEOUT_MILLIS, MILLISECONDS)) {
                endOfInput(false);
            }
            closed = true;
        }
    }

    /** An HTTP message body terminated by the end of the underlying stream. */
    private class UnknownLengthSource extends AbstractSource {
        private boolean inputExhausted;

        @Override public long read(Buffer sink, long byteCount)
                throws IOException {
            if (byteCount < 0) throw new IllegalArgumentException("byteCount < 0: " + byteCount);
            if (closed) throw new IllegalStateException("closed");
            if (inputExhausted) return -1;

            long read = source.read(sink, byteCount);
            if (read == -1) {
                inputExhausted = true;
                endOfInput(true);
                return -1;
            }
            return read;
        }

        @Override public void close() throws IOException {
            if (closed) return;
            if (!inputExhausted) {
                endOfInput(false);
            }
            closed = true;
        }
    }
}
