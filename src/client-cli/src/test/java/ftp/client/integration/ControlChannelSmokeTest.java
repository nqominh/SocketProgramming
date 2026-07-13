package ftp.client.integration;

import ftp.client.ControlChannelClient;
import ftp.protocol.control.ControlMessage;
import ftp.server.ControlChannelServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ControlChannelSmokeTest {
    private static final Pattern PASV_REPLY = Pattern.compile(".*\\((\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+)\\).*");

    @TempDir
    Path root;

    @Test
    void clientConnectsToTcpServerAndReadsGreeting() throws Exception {
        try (ControlChannelServer server = ControlChannelServer.bind(InetAddress.getLoopbackAddress(), 0, root)) {
            server.start();

            try (ControlChannelClient client = ControlChannelClient.connect(
                    InetAddress.getLoopbackAddress().getHostAddress(),
                    server.port(),
                    Duration.ofSeconds(1))) {
                ControlMessage greeting = client.readReply();

                assertEquals(220, greeting.replyCode());
            }
        }
    }

    @Test
    void serverLoopsCommandsThroughDispatcherAndOpensPassiveUdpPort() throws Exception {
        try (ControlChannelServer server = ControlChannelServer.bind(InetAddress.getLoopbackAddress(), 0, root)) {
            server.start();

            try (ControlChannelClient client = ControlChannelClient.connect(
                    InetAddress.getLoopbackAddress().getHostAddress(),
                    server.port(),
                    Duration.ofSeconds(1))) {
                assertEquals(220, client.readReply().replyCode());

                client.sendCommand("NOOP", "");
                assertEquals(200, client.readReply().replyCode());

                client.sendCommand("USER", "alice");
                assertEquals(331, client.readReply().replyCode());

                client.sendCommand("PASS", "secret");
                assertEquals(230, client.readReply().replyCode());

                client.sendCommand("PASV", "");
                ControlMessage passiveReply = client.readReply();

                assertEquals(227, passiveReply.replyCode());
                int passivePort = passivePort(passiveReply.replyText());
                assertUdpPortIsBound(passivePort);

                client.sendCommand("QUIT", "");
                assertEquals(221, client.readReply().replyCode());
            }
        }
    }

    @Test
    void abruptClientDisconnectDoesNotKillAcceptLoop() throws Exception {
        try (ControlChannelServer server = ControlChannelServer.bind(InetAddress.getLoopbackAddress(), 0, root)) {
            server.start();

            try (Socket rude = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
                assertEquals(220, readReplyCode(rude));
                rude.setSoLinger(true, 0);
            } // closing with linger=0 sends RST, so the server's blocking readLine() fails mid-loop

            Thread.sleep(200); // let the accept-loop thread observe and (mis)handle the reset

            try (ControlChannelClient client = ControlChannelClient.connect(
                    InetAddress.getLoopbackAddress().getHostAddress(),
                    server.port(),
                    Duration.ofSeconds(2))) {
                assertEquals(220, client.readReply().replyCode(),
                        "accept loop should still serve new clients after one client's abrupt disconnect");
            }
        }
    }

    @Test
    void malformedCommandReturnsSyntaxErrorWithoutClosingServer() throws Exception {
        try (ControlChannelServer server = ControlChannelServer.bind(InetAddress.getLoopbackAddress(), 0, root)) {
            server.start();

            try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
                assertEquals(220, readReplyCode(socket));

                socket.getOutputStream().write("12BAD\r\n".getBytes(StandardCharsets.UTF_8));
                socket.getOutputStream().flush();
                assertEquals(500, readReplyCode(socket));

                socket.getOutputStream().write("NOOP\r\n".getBytes(StandardCharsets.UTF_8));
                socket.getOutputStream().flush();
                assertEquals(200, readReplyCode(socket));
            }
        }
    }
    private static int passivePort(String replyText) {
        Matcher matcher = PASV_REPLY.matcher(replyText);
        assertTrue(matcher.matches(), "Reply should contain PASV host/port tuple: " + replyText);
        int p1 = Integer.parseInt(matcher.group(5));
        int p2 = Integer.parseInt(matcher.group(6));
        return (p1 * 256) + p2;
    }

    private static void assertUdpPortIsBound(int port) throws Exception {
        try (DatagramSocket socket = new DatagramSocket(port, InetAddress.getLoopbackAddress())) {
            assertTrue(socket.isBound(), "Unexpected unbound UDP probe socket");
            throw new AssertionError("Expected UDP port to be owned by server PASV socket: " + port);
        } catch (java.net.BindException expected) {
            // Expected: the server owns the passive UDP socket.
        }
    }
    private static int readReplyCode(Socket socket) throws Exception {
        byte[] buffer = new byte[128];
        int read = socket.getInputStream().read(buffer);
        String reply = new String(buffer, 0, read, StandardCharsets.UTF_8);
        return Integer.parseInt(reply.substring(0, 3));
    }}
