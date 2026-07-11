package ftp.protocol.rdt;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;

public final class RdtSender {
    private final RdtConfig config;

    public RdtSender(RdtConfig config) {
        this.config = config;
    }

    public void send(byte[] payload, PacketEndpoint endpoint) throws IOException {
        byte[] bytes = payload == null ? new byte[0] : payload;
        send(new java.io.ByteArrayInputStream(bytes), endpoint);
    }

    public void send(InputStream inputStream, PacketEndpoint endpoint) throws IOException {
        byte[] buffer = new byte[config.maxPayloadBytes()];
        int sequenceNumber = 0;
        int read = inputStream.read(buffer);
        if (read < 0) {
            sendWithRetry(ReliableDataPacket.data(sequenceNumber, true, new byte[0]), endpoint);
            return;
        }
        while (read >= 0) {
            byte[] payload = Arrays.copyOf(buffer, read);
            int nextRead = inputStream.read(buffer);
            sendWithRetry(ReliableDataPacket.data(sequenceNumber, nextRead < 0, payload), endpoint);
            sequenceNumber++;
            read = nextRead;
        }
    }

    private void sendWithRetry(ReliableDataPacket packet, PacketEndpoint endpoint) throws IOException {
        byte[] datagram = packet.encode();
        for (int attempt = 1; attempt <= config.maxRetries(); attempt++) {
            endpoint.send(datagram);
            try {
                ReliableDataPacket ack = ReliableDataPacket.decode(endpoint.receive(config.ackTimeout()));
                if (ack.isAckFor(packet.sequenceNumber())) {
                    return;
                }
            } catch (ReliableDataPacket.ChecksumMismatchException ignored) {
                // Corrupt ACK behaves like a lost ACK.
            } catch (TimeoutException ignored) {
                // Stop-and-Wait retransmits after timeout.
            }
        }
        throw new RdtTransferException("ACK not received for sequence " + packet.sequenceNumber());
    }
}
