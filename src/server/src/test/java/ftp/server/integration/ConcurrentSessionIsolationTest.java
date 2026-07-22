package ftp.server.integration;

import ftp.server.ControlChannelServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

final class ConcurrentSessionIsolationTest {
    @TempDir
    Path root;

    @Test
    void twoConnectedSessionsKeepIndependentAuthState() throws Exception {
        try (ControlChannelServer server = startedServer();
             TestConnection first = connect(server)) {
            first.login();

            try (TestConnection second = assertTimeoutPreemptively(Duration.ofSeconds(1), () -> connect(server))) {
                second.send("PWD");
                assertEquals(530, second.readCode());

                first.send("PWD");
                assertEquals(257, first.readCode());
            }
        }
    }

    @Test
    void twoConnectedSessionsKeepIndependentWorkingDirectories() throws Exception {
        try (ControlChannelServer server = startedServer();
             TestConnection first = connect(server)) {
            first.login();
            first.command("MKD alpha", 257);
            first.command("CWD alpha", 250);

            try (TestConnection second = assertTimeoutPreemptively(Duration.ofSeconds(1), () -> connect(server))) {
                second.login();

                first.send("PWD");
                assertTrue(first.readLine().contains("/alpha"));
                second.send("PWD");
                assertEquals("257 \"/\"", second.readLine());
            }
        }
    }

    @Test
    void simultaneousSessionsDoNotShareActiveOrPassiveChannelState() throws Exception {
        try (ControlChannelServer server = startedServer();
             TestConnection passiveSession = connect(server)) {
            passiveSession.login();
            passiveSession.send("PASV");
            assertEquals(227, passiveSession.readCode());

            try (TestConnection activeSession = assertTimeoutPreemptively(Duration.ofSeconds(1), () -> connect(server))) {
                activeSession.login();
                activeSession.command("PORT 127,0,0,1,195,87", 200);

                passiveSession.command("PWD", 257);
                activeSession.command("PWD", 257);
            }
        }
    }

    private ControlChannelServer startedServer() throws IOException {
        ControlChannelServer server = ControlChannelServer.bind(InetAddress.getLoopbackAddress(), 0, root);
        server.start();
        return server;
    }

    private static TestConnection connect(ControlChannelServer server) throws IOException {
        Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.port());
        socket.setSoTimeout(1_000);
        TestConnection connection = new TestConnection(socket);
        assertEquals(220, connection.readCode());
        return connection;
    }

    private static final class TestConnection implements AutoCloseable {
        private final Socket socket;
        private final BufferedReader reader;
        private final BufferedWriter writer;

        TestConnection(Socket socket) throws IOException {
            this.socket = socket;
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        void login() throws IOException {
            command("USER alice", 331);
            command("PASS secret", 230);
        }

        void command(String line, int expectedCode) throws IOException {
            send(line);
            assertEquals(expectedCode, readCode());
        }

        void send(String line) throws IOException {
            writer.write(line);
            writer.write("\r\n");
            writer.flush();
        }

        int readCode() throws IOException {
            return Integer.parseInt(readLine().substring(0, 3));
        }

        String readLine() throws IOException {
            return reader.readLine();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
