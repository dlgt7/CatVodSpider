package com.github.catvod.net;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.ForwardingSource;
import okio.Okio;
import okio.Source;

import java.io.IOException;

public class ProgressResponseBody extends ResponseBody {

    private final ResponseBody responseBody;
    private final ProgressCallback progressCallback;
    private BufferedSource bufferedSource;

    public ProgressResponseBody(ResponseBody responseBody, ProgressCallback progressCallback) {
        this.responseBody = responseBody;
        this.progressCallback = progressCallback;
    }

    public static ResponseBody create(ResponseBody responseBody, ProgressCallback progressCallback) {
        return new ProgressResponseBody(responseBody, progressCallback);
    }

    @Override
    public MediaType contentType() {
        return responseBody.contentType();
    }

    @Override
    public long contentLength() {
        return responseBody.contentLength();
    }

    @Override
    public BufferedSource source() {
        if (bufferedSource == null) {
            bufferedSource = Okio.buffer(source(responseBody.source()));
        }
        return bufferedSource;
    }

    private Source source(Source source) {
        return new ForwardingSource(source) {
            long totalBytesRead = 0L;

            @Override
            public long read(Buffer sink, long byteCount) throws IOException {
                long bytesRead = super.read(sink, byteCount);
                totalBytesRead += bytesRead != -1 ? bytesRead : 0;
                if (progressCallback != null) {
                    progressCallback.onProgress(totalBytesRead, responseBody.contentLength());
                }
                return bytesRead;
            }
        };
    }

    public interface ProgressCallback {
        void onProgress(long current, long total);
    }
}