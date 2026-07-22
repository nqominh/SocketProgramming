package ftp.server;

import ftp.server.command.CommandDispatcher;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class ControlChannelServer implements AutoCloseable {
    private final ServerSocket serverSocket;
    private final Path root;
    private final CommandDispatcher commandDispatcher = new CommandDispatcher();
    private final AtomicInteger sessionSequence = new AtomicInteger();
    private final ExecutorService acceptExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService clientExecutor = Executors.newFixedThreadPool(8);
    private final AtomicBoolean running = new AtomicBoolean(false);

    private ControlChannelServer(ServerSocket serverSocket, Path root) {
        this.serverSocket = serverSocket;
        this.root = root;
    }

    public static ControlChannelServer bind(InetAddress address, int port, Path root) throws IOException {
        ServerSocket socket = new ServerSocket(port, 50, address);
        socket.setSoTimeout(500);
        return new ControlChannelServer(socket, root);
    }

    public int port() {
        return serverSocket.getLocalPort();
    }

    public Path root() {
        return root;
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            acceptExecutor.submit(this::acceptLoop);
        }
    }

    @Override
    public void close() throws IOException {
        running.set(false);
        serverSocket.close();
        acceptExecutor.shutdownNow();
        clientExecutor.shutdownNow();
    }

    private void acceptLoop() {
        while (running.get()) {
            java.net.Socket socket;
            try {
                socket = serverSocket.accept();
            } catch (java.net.SocketTimeoutException ignored) {
                continue;
            } catch (IOException exception) {
                if (running.get()) {
                    throw new IllegalStateException("Control server accept failed", exception);
                }
                return;
            }
            submitClient(socket);
        }
    }

    private void submitClient(java.net.Socket socket) {
        try {
            clientExecutor.submit(() -> {
                try {
                    handleClient(socket);
                } catch (IOException exception) {
                    logClientFailure(socket, exception);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException exception) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Server is shutting down; no client reply is possible here.
            }
        }
    }

    private void handleClient(java.net.Socket socket) throws IOException {
        new ControlConnectionHandler(socket, root, nextSessionId(), commandDispatcher).handle();
    }

    private static void logClientFailure(java.net.Socket socket, IOException exception) {
        System.err.printf("Connection from %s failed: %s%n", socket.getRemoteSocketAddress(), exception.getMessage());
    }

    private String nextSessionId() {
        return "s" + sessionSequence.incrementAndGet();
    }

    public static void main(String[] args) throws Exception {
        int port = optionInt(args, "--port", 2121);
        Path root = Path.of(option(args, "--root", "ftp-root"));
        ControlChannelServer server = ControlChannelServer.bind(InetAddress.getByName("0.0.0.0"), port, root);
        server.start();
        System.out.printf("Hybrid FTP server listening on %d rooted at %s%n", server.port(), server.root());
        Thread.currentThread().join();
    }

    private static int optionInt(String[] args, String name, int defaultValue) {
        return Integer.parseInt(option(args, name, Integer.toString(defaultValue)));
    }

    private static String option(String[] args, String name, String defaultValue) {
        for (int index = 0; index + 1 < args.length; index++) {
            if (name.equals(args[index])) {
                return args[index + 1];
            }
        }
        return defaultValue;
    }
}