package ftp.protocol.rdt;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.TimeoutException;

public final class RdtReceiver {
    private final RdtConfig config;

    public RdtReceiver(RdtConfig config) {
        this.config = config;
    }

    public byte[] receiveBytes(PacketChannel endpoint) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        receive(endpoint, outputStream);
        return outputStream.toByteArray();
    }

    public void receive(PacketChannel endpoint, OutputStream outputStream) throws IOException {
        int expectedSequence = 0;
        int timeoutCount = 0;
        while (true) {
            ReliableDataPacket packet;
            try {
                packet = ReliableDataPacket.decode(endpoint.receive(config.ackTimeout()));
                timeoutCount = 0;
            } catch (ReliableDataPacket.ChecksumMismatchException ignored) {
                continue;
            } catch (TimeoutException exception) {
                timeoutCount++;
                if (timeoutCount >= config.maxReceiveTimeouts()) {
                    throw new RdtTransferException("Timed out waiting for DATA packet", exception);
                }
                continue;
            }
            if (!packet.hasFlag(ReliableDataPacket.FLAG_DATA)) {
                continue;
            }
            int sequenceNumber = packet.sequenceNumber();
            if (sequenceNumber == expectedSequence) {
                outputStream.write(packet.payload());
                endpoint.send(ReliableDataPacket.ack(sequenceNumber).encode());
                if (packet.hasFlag(ReliableDataPacket.FLAG_FIN)) {
                    outputStream.flush();
                    return;
                }
                expectedSequence++;
            } else if (sequenceNumber < expectedSequence) {
                endpoint.send(ReliableDataPacket.ack(sequenceNumber).encode());
            }
        }
    }
}
