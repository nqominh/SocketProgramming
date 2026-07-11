package ftp.protocol.unit;

import ftp.protocol.rdt.ReliableDataPacket;
import ftp.protocol.rdt.ReliableDataPacket.ChecksumMismatchException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ReliableDataPacketTest {
    @Test
    void encodesAndDecodesDataPacket() {
        byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);
        ReliableDataPacket packet = ReliableDataPacket.data(7, true, payload);

        ReliableDataPacket decoded = ReliableDataPacket.decode(packet.encode());

        assertEquals(7, decoded.sequenceNumber());
        assertTrue(decoded.hasFlag(ReliableDataPacket.FLAG_DATA));
        assertTrue(decoded.hasFlag(ReliableDataPacket.FLAG_FIN));
        assertArrayEquals(payload, decoded.payload());
    }

    @Test
    void rejectsChecksumMismatch() {
        byte[] encoded = ReliableDataPacket.data(1, false, new byte[] {1, 2, 3}).encode();
        encoded[encoded.length - 1] ^= 0x01;

        assertThrows(ChecksumMismatchException.class, () -> ReliableDataPacket.decode(encoded));
    }

    @Test
    void encodesAndDecodesAckPacket() {
        ReliableDataPacket decoded = ReliableDataPacket.decode(ReliableDataPacket.ack(42).encode());

        assertEquals(42, decoded.sequenceNumber());
        assertTrue(decoded.hasFlag(ReliableDataPacket.FLAG_ACK));
        assertEquals(0, decoded.payload().length);
    }
}
