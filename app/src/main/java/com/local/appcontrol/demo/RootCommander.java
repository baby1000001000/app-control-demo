package com.local.appcontrol.demo;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Bounded subprocess execution off the UI thread. Never accepts a user supplied command. */
final class RootCommander {
    boolean checkRoot() throws Exception { return "0".equals(run("id -u", 20).trim()); }
    String run(String command, int timeoutSeconds) throws Exception {
        Process process;
        try { process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start(); }
        catch (IOException missing) { throw new IOException("未找到可用su，请先安装并启用Root", missing); }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Thread reader = new Thread(() -> {
            try (InputStream input = process.getInputStream()) {
                byte[] buffer = new byte[1024]; int count;
                while ((count = input.read(buffer)) != -1) synchronized (bytes) {
                    int accepted = Math.min(count, 8192 - bytes.size()); if (accepted > 0) bytes.write(buffer, 0, accepted);
                }
            } catch (IOException ignored) { /* Closing a timed-out process closes this pipe. */ }
        }, "root-output");
        reader.setDaemon(true); reader.start();
        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) throw new IOException("Root执行超时，请在Root管理器中永久允许本Demo后重试");
            reader.join(500);
            String output;
            synchronized (bytes) { output = new String(bytes.toByteArray(), StandardCharsets.UTF_8).trim(); }
            if (process.exitValue() != 0) throw new IOException("Root命令失败：" + (output.isEmpty() ? "请检查Root授权" : output));
            return output;
        } finally {
            process.destroyForcibly();
            try { process.getInputStream().close(); } catch (IOException ignored) { }
        }
    }
}
