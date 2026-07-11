package ftp.client.integration;

import ftp.client.ControlChannelClient;
import ftp.client.DataChannelClient;
import ftp.protocol.control.ControlMessage;
import ftp.server.ControlChannelServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class BasicTransferSmokeTest {
    @TempDir
    Path root;

    @Test
    void asciiStorStoresByteIdenticalFile() throws Exception {
        byte[] payload = "hello from ascii stor\nsecond line\n".getBytes(StandardCharsets.US_ASCII);
        try (ControlChannelServer server = ControlChannelServer.bind(InetAddress.getLoopbackAddress(), 0, root);
             ControlChannelClient client = connectedClient(server)) {
            login(client);
            DataChannelClient data = passiveDataChannel(client);

            client.sendCommand("STOR", "stored.txt");
            data.upload(payload);

            assertTransferComplete(client);
            assertArrayEquals(payload, Files.readAllBytes(root.resolve("stored.txt")));
        }
    }

    @Test
    void asciiRetrDownloadsByteIdenticalFile() throws Exception {
        byte[] payload = "hello from ascii retr\nsecond line\n".getBytes(StandardCharsets.US_ASCII);
        Files.write(root.resolve("source.txt"), payload);
        try (ControlChannelServer server = ControlChannelServer.bind(InetAddress.getLoopbackAddress(), 0, root);
             ControlChannelClient client = connectedClient(server)) {
            login(client);
            DataChannelClient data = passiveDataChannel(client);

            client.sendCommand("RETR", "source.txt");
            byte[] downloaded = data.download();

            assertTransferComplete(client);
            assertArrayEquals(payload, downloaded);
        }
    }

    @Test
    void zeroByteStorAndRetrRoundTrip() throws Exception {
        try (ControlChannelServer server = ControlChannelServer.bind(InetAddress.getLoopbackAddress(), 0, root);
             ControlChannelClient client = connectedClient(server)) {
            login(client);
            DataChannelClient upload = passiveDataChannel(client);

            client.sendCommand("STOR", "empty.txt");
            upload.upload(new byte[0]);
            assertTransferComplete(client);
            assertEquals(0, Files.size(root.resolve("empty.txt")));

            DataChannelClient download = passiveDataChannel(client);
            client.sendCommand("RETR", "empty.txt");
            assertArrayEquals(new byte[0], download.download());
            assertTransferComplete(client);
        }
    }

    @Test
    void missingRetrReturnsFileUnavailableAndCreatesNoOutput() throws Exception {
        try (ControlChannelServer server = ControlChannelServer.bind(InetAddress.getLoopbackAddress(), 0, root);
             ControlChannelClient client = connectedClient(server)) {
            login(client);
            passiveDataChannel(client);

            client.sendCommand("RETR", "missing.txt");

            assertEquals(550, client.readReply().replyCode());
            assertFalse(Files.exists(root.resolve("missing.txt")));
        }
    }

    private ControlChannelClient connectedClient(ControlChannelServer server) throws Exception {
        server.start();
        ControlChannelClient client = ControlChannelClient.connect(
                InetAddress.getLoopbackAddress().getHostAddress(),
                server.port(),
                Duration.ofSeconds(2));
        assertEquals(220, client.readReply().replyCode());
        return client;
    }

    private static void login(ControlChannelClient client) throws Exception {
        client.sendCommand("USER", "alice");
        assertEquals(331, client.readReply().replyCode());
        client.sendCommand("PASS", "secret");
        assertEquals(230, client.readReply().replyCode());
    }

    private static DataChannelClient passiveDataChannel(ControlChannelClient client) throws Exception {
        client.sendCommand("PASV", "");
        ControlMessage passiveReply = client.readReply();
        assertEquals(227, passiveReply.replyCode());
        return DataChannelClient.fromPassiveReply(passiveReply, Duration.ofSeconds(2));
    }

    private static void assertTransferComplete(ControlChannelClient client) throws Exception {
        assertEquals(150, client.readReply().replyCode());
        assertEquals(226, client.readReply().replyCode());
    }
}
