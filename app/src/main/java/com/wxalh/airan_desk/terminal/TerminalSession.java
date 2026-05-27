package com.wxalh.airan_desk.terminal;

import android.os.Build;
import android.util.Base64;
import android.util.Log;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.webrtc.DataChannel;

@SuppressWarnings({"deprecation", "unchecked"})
public final class TerminalSession {
    private static final String TAG = "TerminalSession";
    private Process process;
    private BufferedOutputStream input;
    private Thread outputThread;
    private volatile boolean running;
    private DataChannel channel;
    private int rows = 24;
    private int cols = 80;
    private boolean lastOutputWasCr;

    public TerminalSession() {
    }

    public void start(DataChannel fileTextChannel) {
        this.start(fileTextChannel, this.rows, this.cols);
    }

    public void start(DataChannel fileTextChannel, int rows, int cols) {
        this.channel = fileTextChannel;
        if (this.running) {
            return;
        }
        try {
            this.rows = Math.max(8, rows);
            this.cols = Math.max(20, cols);
            this.lastOutputWasCr = false;
            ProcessBuilder builder = new ProcessBuilder("/system/bin/sh", "-i");
            builder.redirectErrorStream(true);
            builder.environment().put("TERM", "xterm-256color");
            builder.environment().put("COLUMNS", String.valueOf(this.cols));
            builder.environment().put("LINES", String.valueOf(this.rows));
            builder.environment().put("PS1", "$ ");
            this.process = builder.start();
            this.input = new BufferedOutputStream(this.process.getOutputStream());
            this.running = true;
            this.sendInfo();
            this.outputThread = new Thread(new Runnable(){

                @Override
                public void run() {
                    TerminalSession.this.readOutput();
                }
            }, "AiranTerminal");
            this.outputThread.start();
        }
        catch (Exception e) {
            this.sendError(e.getMessage());
        }
    }

    public void input(byte[] bytes) {
        if (!this.running || this.input == null || bytes == null || bytes.length == 0) {
            return;
        }
        try {
            this.input.write(this.normalizeInput(bytes));
            this.input.flush();
        }
        catch (Exception e) {
            this.sendError(e.getMessage());
        }
    }

    public void input(String text) {
        if (text == null) {
            return;
        }
        this.input(text.getBytes(StandardCharsets.UTF_8));
    }

    public void resize(int rows, int cols) {
        this.rows = Math.max(8, rows);
        this.cols = Math.max(20, cols);
        if (!this.running || this.input == null) {
            return;
        }
        try {
            String command = "export COLUMNS=" + this.cols + " LINES=" + this.rows + "\n";
            this.input.write(command.getBytes(StandardCharsets.UTF_8));
            this.input.flush();
        }
        catch (Exception e) {
            this.sendError(e.getMessage());
        }
    }

    private byte[] normalizeInput(byte[] bytes) {
        ByteArrayOutputStream normalized = new ByteArrayOutputStream(bytes.length);
        for (int i = 0; i < bytes.length; ++i) {
            byte b = bytes[i];
            if (b == 13) {
                normalized.write(10);
                if (i + 1 >= bytes.length || bytes[i + 1] != 10) continue;
                ++i;
                continue;
            }
            normalized.write(b);
        }
        return normalized.toByteArray();
    }

    public void stop() {
        this.running = false;
        try {
            if (this.input != null) {
                this.input.close();
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        try {
            if (this.process != null) {
                this.process.destroy();
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        this.sendClosed(0);
    }

    private void readOutput() {
        block3: {
            try {
                int read;
                BufferedInputStream output = new BufferedInputStream(this.process.getInputStream());
                byte[] buf = new byte[4096];
                while (this.running && (read = output.read(buf)) >= 0) {
                    byte[] chunk = new byte[read];
                    System.arraycopy(buf, 0, chunk, 0, read);
                    this.sendOutput(this.normalizeOutput(chunk));
                }
                int code = this.process.waitFor();
                this.running = false;
                this.sendClosed(code);
            }
            catch (Exception e) {
                if (!this.running) break block3;
                this.sendError(e.getMessage());
            }
        }
    }

    private void sendInfo() throws Exception {
        JSONObject object = new JSONObject();
        object.put("msgType", (Object)"terminal_info");
        object.put("os", (Object)("Android " + Build.VERSION.RELEASE));
        object.put("shell", (Object)"/system/bin/sh");
        object.put("terminalMode", (Object)"pipe");
        object.put("pathTracking", false);
        this.sendJson(object);
    }

    private void sendOutput(byte[] data) throws Exception {
        JSONObject object = new JSONObject();
        object.put("msgType", (Object)"terminal_output");
        object.put("data", (Object)Base64.encodeToString((byte[])data, (int)2));
        object.put("encoding", (Object)"base64");
        this.sendJson(object);
    }

    private byte[] normalizeOutput(byte[] data) {
        if (data == null || data.length == 0) {
            return data;
        }
        ByteArrayOutputStream normalized = new ByteArrayOutputStream(data.length + 16);
        for (byte b : data) {
            if (b == 10 && !this.lastOutputWasCr) {
                normalized.write(13);
            }
            normalized.write(b);
            this.lastOutputWasCr = b == 13;
            if (b != 13 && b != 10) {
                this.lastOutputWasCr = false;
            }
        }
        return normalized.toByteArray();
    }

    private void sendClosed(int code) {
        try {
            JSONObject object = new JSONObject();
            object.put("msgType", (Object)"terminal_closed");
            object.put("status", code);
            this.sendJson(object);
        }
        catch (Exception e) {
            Log.e((String)TAG, (String)"send closed failed", (Throwable)e);
        }
    }

    private void sendError(String error) {
        try {
            JSONObject object = new JSONObject();
            object.put("msgType", (Object)"terminal_error");
            object.put("error", (Object)(error == null ? "unknown terminal error" : error));
            this.sendJson(object);
        }
        catch (Exception e) {
            Log.e((String)TAG, (String)"send error failed", (Throwable)e);
        }
    }

    private void sendJson(JSONObject object) {
        byte[] bytes;
        if (this.channel == null || this.channel.state() != DataChannel.State.OPEN) {
            return;
        }
        try {
            bytes = object.toString().getBytes("UTF-8");
        }
        catch (Exception e) {
            bytes = object.toString().getBytes();
        }
        this.channel.send(new DataChannel.Buffer(ByteBuffer.wrap(bytes), false));
    }
}
