package com.bunker.survival.launcher;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One live Platforma151 process per user data dir. A second start asks the first
 * to show its window, then exits.
 */
public final class SingleInstance implements AutoCloseable {
    private final FileChannel channel;
    private final FileLock lock;
    private final ServerSocket server;
    private final Path portPath;
    private final AtomicReference<Runnable> onShow = new AtomicReference<>();

    private SingleInstance(FileChannel channel, FileLock lock, ServerSocket server, Path portPath) {
        this.channel = channel;
        this.lock = lock;
        this.server = server;
        this.portPath = portPath;
        if (server != null) {
            Thread acceptThread = new Thread(this::acceptLoop, "platforma-instance");
            acceptThread.setDaemon(true);
            acceptThread.start();
        }
    }

    public static SingleInstance acquire(Path appDir) {
        if (appDir == null) {
            return new SingleInstance(null, null, null, null);
        }
        FileChannel channel = null;
        FileLock lock = null;
        try {
            Files.createDirectories(appDir);
            Path lockPath = appDir.resolve("instance.lock");
            Path portPath = appDir.resolve("instance.port");
            channel = FileChannel.open(
                    lockPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE);
            lock = channel.tryLock();
            if (lock == null) {
                channel.close();
                return null;
            }
            try {
                ServerSocket server = new ServerSocket();
                server.setReuseAddress(true);
                server.bind(new InetSocketAddress("127.0.0.1", 0));
                Files.writeString(
                        portPath,
                        Integer.toString(server.getLocalPort()),
                        StandardCharsets.UTF_8);
                return new SingleInstance(channel, lock, server, portPath);
            } catch (Exception e) {
                return new SingleInstance(channel, lock, null, portPath);
            }
        } catch (Exception e) {
            System.err.println("Platforma151 single-instance lock unavailable: " + e.getMessage());
            closeQuietly(lock, channel);
            return new SingleInstance(null, null, null, null);
        }
    }

    public static boolean signalShow(Path appDir) {
        if (appDir == null) {
            return false;
        }
        Path portPath = appDir.resolve("instance.port");
        try {
            if (!Files.isRegularFile(portPath)) {
                return false;
            }
            String raw = Files.readString(portPath, StandardCharsets.UTF_8).trim();
            int port = Integer.parseInt(raw);
            if (port <= 0 || port > 65535) {
                return false;
            }
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", port), 800);
                socket.getOutputStream().write("SHOW\n".getBytes(StandardCharsets.UTF_8));
                socket.getOutputStream().flush();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void setOnShow(Runnable action) {
        onShow.set(action);
    }

    private void acceptLoop() {
        while (server != null && !server.isClosed()) {
            try (Socket socket = server.accept();
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                if (line != null && "SHOW".equalsIgnoreCase(line.trim())) {
                    Runnable action = onShow.get();
                    if (action != null) {
                        action.run();
                    }
                }
            } catch (SocketException closed) {
                break;
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void close() {
        if (server != null) {
            try {
                server.close();
            } catch (Exception ignored) {
            }
        }
        closeQuietly(lock, channel);
        if (portPath != null) {
            try {
                Files.deleteIfExists(portPath);
            } catch (Exception ignored) {
            }
        }
    }

    private static void closeQuietly(FileLock lock, FileChannel channel) {
        try {
            if (lock != null && lock.isValid()) {
                lock.release();
            }
        } catch (Exception ignored) {
        }
        try {
            if (channel != null) {
                channel.close();
            }
        } catch (Exception ignored) {
        }
    }
}
