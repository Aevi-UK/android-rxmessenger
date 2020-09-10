/*
 * Copyright (c) 2019 AEVI International GmbH. All rights reserved
 * Unauthorized copying or distribution of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package com.aevi.android.rxmessenger.service.pipe;

import android.os.ParcelFileDescriptor;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;

import static com.aevi.android.rxmessenger.service.WebSocketChannelServer.CLOSE_MESSAGE;

public class Pipe implements Closeable, Runnable {

    private final InputStream input;
    private final DataOutputStream output;
    private Subject<String> callback;
    private volatile boolean closed = false;
    private static final int HEADER_SIZE = 4;

    public Pipe(ParcelFileDescriptor descriptor) {
        this(descriptor, null);
    }

    public Pipe(ParcelFileDescriptor descriptor, Subject<String> callbackEmitter) {
        this.input = new ParcelFileDescriptor.AutoCloseInputStream(descriptor);
        this.output = new DataOutputStream(new ParcelFileDescriptor.AutoCloseOutputStream(descriptor));
        this.callback = callbackEmitter != null ? callbackEmitter : PublishSubject.<String>create();
    }

    public void updateCallback(Subject<String> callback) {
        this.callback = callback;
    }

    public boolean isConnected() {
        return !closed;
    }

    @Override
    public void run() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                read();
            }
        }).start();
    }

    public boolean write(String string) {
        try {
            byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
            this.output.writeInt(bytes.length);
            this.output.write(bytes);
            return true;
        } catch (IOException e) {
            safeClose(this);
            return false;
        }
    }

    private void read() {
        try {
            byte[] tmp = new byte[2048];
            int size;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            while (!closed) {
                size = input.read(tmp);
                if (size > -1) {
                    out.write(tmp, 0, size);
                }
                if (out.size() >= HEADER_SIZE) {
                    // Read header
                    byte[] data = out.toByteArray();
                    int length = ByteBuffer.wrap(data).getInt();
                    int requiredLength = HEADER_SIZE + length;
                    if (data.length >= requiredLength) {
                        // Read body
                        String string = new String(data, HEADER_SIZE, length, StandardCharsets.UTF_8);
                        read(string);
                        // Clear buffer and append next message when applicable
                        out.reset();
                        if(data.length > requiredLength) {
                            out.write(data, requiredLength, data.length - requiredLength);
                        }
                    }
                }
            }
        } catch (IOException e) {
            safeClose(this);
        }
    }

    private void read(String string) {
        if (string.equals(CLOSE_MESSAGE)) {
            callback.onComplete();
        } else {
            callback.onNext(string);
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (!closed) {
            close(input, output);
            closed = true;
        }
    }

    public static void safeClose(Closeable... closeables) {
        try {
            close(closeables);
        } catch (IOException e) {

        }
    }

    public static void close(Closeable... closeables) throws IOException {
        IOException exception = null;
        for (Closeable closeable : closeables) {
            try {
                if (closeable != null) {
                    closeable.close();
                }
            } catch (IOException e) {
                if (exception == null) {
                    exception = e;
                }
            }
        }
        if (exception != null) {
            throw exception;
        }
    }
}
