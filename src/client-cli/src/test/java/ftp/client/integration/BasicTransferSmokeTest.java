package ftp.client.integration;

import ftp.client.ControlChannelClient;
import ftp.client.DataChannelClient;
import ftp.protocol.control.ControlMessage;
import ftp.protocol.rdt.PacketChannel;
import ftp.protocol.rdt.RdtConfig;
import ftp.protocol.rdt.RdtReceiver;
import ftp.protocol.rdt.RdtSender;
import ftp.protocol.rdt.ReliableDataPacket;
import ftp.server.ControlChannelServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class BasicTransferSmokeTest {
    private static final RdtConfig TEST_RDT_CONFIG = new RdtConfig(
            RdtConfig.DEFAULT_MAX_PAYLOAD_BYTES,
            Duration.ofMillis(100),
            8,
            10);

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

    @Test
    void binaryPassiveStorStoresByteIdenticalFile() throws Exception {
        byte[] payload = new byte[] {0, 1, 2, 10, 13, 26, 65, 90, 100, 0, -1, -128, 127};
        try (ControlChannelServer server = ControlChannelServer.bind(InetAddress.getLoopbackAddress(), 0, root);
             ControlChannelClient client = connectedClient(server)) {
            login(client);
            setBinaryType(client);
            DataChannelClient data = passiveDataChannel(client);

            client.sendCommand("STOR", "binary.bin");
            data.upload(payload);

            assertTransferComplete(client);
            assertArrayEquals(payload, Files.readAllBytes(root.resolve("binary.bin")));
        }
    }

    @Test
    void binaryPassiveRetrDownloadsByteIdenticalFile() throws Exception {
        byte[] payload = new byte[] {-1, 0, 3, 4, 5, 10, 13, 42, 100, 101, -64, 0, 127};
        Files.write(root.resolve("source.bin"), payload);
        try (ControlChannelServer server = ControlChannelServer.bind(InetAddress.getLoopbackAddress(), 0, root);
             ControlChannelClient client = connectedClient(server)) {
            login(client);
            setBinaryType(client);
            DataChannelClient data = passiveDataChannel(client);

            client.sendCommand("RETR", "source.bin");
            byte[] downloaded = data.download();

            assertTransferComplete(client);
            assertArrayEquals(payload, downloaded);
        }
    }

    @Test
    void activeStorStoresByteIdenticalFile() throws Exception {
        byte[] payload = new byte[] {9, 8, 7, 0, 6, 5, 4, -1, -2, 3, 2, 1};
        try (ControlChannelServer server = ControlChannelServer.bind(InetAddress.getLoopbackAddress(), 0, root);
             ControlChannelClient client = connectedClient(server);
             ActiveDataChannel data = new ActiveDataChannel()) {
            login(client);
            setBinaryType(client);
            enterActiveMode(client, data);

            client.sendCommand("STOR", "active-stor.bin");
            data.upload(payload);

            assertTransferComplete(client);
            assertArrayEquals(payload, Files.readAllBytes(root.resolve("active-stor.bin")));
        }
    }

    @Test
    void activeRetrDownloadsByteIdenticalFile() throws Exception {
        byte[] payload = new byte[] {-8, -7, 0, 1, 2, 3, 4, 0, 5, 6, 127};
        Files.write(root.resolve("active-source.bin"), payload);
        try (ControlChannelServer server = ControlChannelServer.bind(InetAddress.getLoopbackAddress(), 0, root);
             ControlChannelClient client = connectedClient(server);
             ActiveDataChannel data = new ActiveDataChannel()) {
            login(client);
            setBinaryType(client);
            enterActiveMode(client, data);

            client.sendCommand("RETR", "active-source.bin");
            byte[] downloaded = data.download();

            assertTransferComplete(client);
            assertArrayEquals(payload, downloaded);
        }
    }

    @Test
    void passiveStorStillWorksAfterPortThenPasv() throws Exception {
        byte[] payload = "passive after port\n".getBytes(StandardCharsets.US_ASCII);
        try (ControlChannelServer server = ControlChannelServer.bind(InetAddress.getLoopbackAddress(), 0, root);
             ControlChannelClient client = connectedClient(server);
             ActiveDataChannel active = new ActiveDataChannel()) {
            login(client);
            enterActiveMode(client, active);
            DataChannelClient passive = passiveDataChannel(client);

            client.sendCommand("STOR", "passive-after-port.txt");
            passive.upload(payload);

            assertTransferComplete(client);
            assertArrayEquals(payload, Files.readAllBytes(root.resolve("passive-after-port.txt")));
        }
    }

    @Test
    void activeRetrToUnreachableEndpointFailsCleanly() throws Exception {
        byte[] payload = "unreachable active retr\n".getBytes(StandardCharsets.US_ASCII);
        Files.write(root.resolve("unreachable-source.txt"), payload);
        try (ControlChannelServer server = ControlChannelServer.bind(InetAddress.getLoopbackAddress(), 0, root);
             ControlChannelClient client = connectedClient(server)) {
            login(client);
            setBinaryType(client);
            enterActiveMode(client, unusedActivePortArgument());

            client.sendCommand("RETR", "unreachable-source.txt");

            assertEquals(451, client.readReply().replyCode());
        }
    }

    @Test
    void failedActiveStorLeavesNoCommittedPartialFile() throws Exception {
        byte[] partialPayload = new byte[] {65, 66, 0, 67, -1};
        try (ControlChannelServer server = ControlChannelServer.bind(InetAddress.getLoopbackAddress(), 0, root);
             ControlChannelClient client = connectedClient(server);
             ActiveDataChannel data = new ActiveDataChannel()) {
            login(client);
            setBinaryType(client);
            enterActiveMode(client, data);

            client.sendCommand("STOR", "partial-active.bin");
            data.sendPartialUploadAndStop(partialPayload);

            assertEquals(451, client.readReply().replyCode());
            assertFalse(Files.exists(root.resolve("partial-active.bin")));
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

    private static void setBinaryType(ControlChannelClient client) throws Exception {
        client.sendCommand("TYPE", "I");
        assertEquals(200, client.readReply().replyCode());
    }

    private static void enterActiveMode(ControlChannelClient client, ActiveDataChannel data) throws Exception {
        enterActiveMode(client, data.portArgument());
    }

    private static void enterActiveMode(ControlChannelClient client, String portArgument) throws Exception {
        client.sendCommand("PORT", portArgument);
        assertEquals(200, client.readReply().replyCode());
    }

    private static String unusedActivePortArgument() throws Exception {
        InetAddress address = InetAddress.getByName("127.0.0.1");
        int port;
        try (DatagramSocket socket = new DatagramSocket(0, address)) {
            port = socket.getLocalPort();
        }
        byte[] bytes = address.getAddress();
        return "%d,%d,%d,%d,%d,%d".formatted(
                Byte.toUnsignedInt(bytes[0]),
                Byte.toUnsignedInt(bytes[1]),
                Byte.toUnsignedInt(bytes[2]),
                Byte.toUnsignedInt(bytes[3]),
                port / 256,
                port % 256);
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

    private static final class ActiveDataChannel implements PacketChannel {
        private static final int MAX_DATAGRAM_BYTES =
                ReliableDataPacket.HEADER_BYTES + ReliableDataPacket.MAX_PAYLOAD_BYTES;
        private static final byte[] READY_DATAGRAM = new byte[] {0};

        private final DatagramSocket socket;
        private final InetAddress advertisedAddress;
        private InetSocketAddress peer;

        ActiveDataChannel() throws IOException {
            advertisedAddress = InetAddress.getByName("127.0.0.1");
            socket = new DatagramSocket(0, advertisedAddress);
            socket.setSoTimeout(Math.toIntExact(Duration.ofSeconds(2).toMillis()));
        }

        String portArgument() {
            byte[] address = advertisedAddress.getAddress();
            int port = socket.getLocalPort();
            return "%d,%d,%d,%d,%d,%d".formatted(
                    Byte.toUnsignedInt(address[0]),
                    Byte.toUnsignedInt(address[1]),
                    Byte.toUnsignedInt(address[2]),
                    Byte.toUnsignedInt(address[3]),
                    port / 256,
                    port % 256);
        }

        void upload(byte[] payload) throws IOException {
            awaitServerPeer();
            new RdtSender(TEST_RDT_CONFIG).send(payload, this);
        }

        byte[] download() throws IOException {
            return new RdtReceiver(TEST_RDT_CONFIG).receiveBytes(this);
        }

        void sendPartialUploadAndStop(byte[] payload) throws IOException {
            awaitServerPeer();
            send(ReliableDataPacket.data(0, false, payload).encode());
        }

        private void awaitServerPeer() throws IOException {
            byte[] buffer = new byte[MAX_DATAGRAM_BYTES];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);
            peer = (InetSocketAddress) packet.getSocketAddress();
            if (packet.getLength() != READY_DATAGRAM.length || packet.getData()[0] != READY_DATAGRAM[0]) {
                throw new IOException("Expected active-mode readiness datagram");
            }
        }

        @Override
        public void send(byte[] datagram) throws IOException {
            if (peer == null) {
                throw new IOException("Active data peer is unknown");
            }
            byte[] copy = Arrays.copyOf(datagram, datagram.length);
            socket.send(new DatagramPacket(copy, copy.length, peer));
        }

        @Override
        public byte[] receive(Duration timeout) throws IOException, TimeoutException {
            socket.setSoTimeout(Math.toIntExact(timeout.toMillis()));
            byte[] buffer = new byte[MAX_DATAGRAM_BYTES];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
            } catch (SocketTimeoutException exception) {
                TimeoutException timeoutException = new TimeoutException("Timed out waiting for active UDP datagram");
                timeoutException.initCause(exception);
                throw timeoutException;
            }
            peer = (InetSocketAddress) packet.getSocketAddress();
            return Arrays.copyOf(packet.getData(), packet.getLength());
        }

        @Override
        public void close() {
            socket.close();
        }
    }
}
