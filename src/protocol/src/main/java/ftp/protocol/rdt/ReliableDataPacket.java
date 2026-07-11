package ftp.protocol.rdt;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.zip.CRC32;

public record ReliableDataPacket(int sequenceNumber, byte flags, long checksum, byte[] payload) {
    public static final byte FLAG_DATA = 0x01;
    public static final byte FLAG_ACK = 0x02;
    public static final byte FLAG_FIN = 0x04;
    public static final int HEADER_BYTES = 11;
    public static final int MAX_PAYLOAD_BYTES = 1_400;
    private static final int CHECKSUM_OFFSET = 7;

    public ReliableDataPacket {
        if (sequenceNumber < 0) {
            throw new IllegalArgumentException("sequenceNumber must be non-negative");
        }
        validateFlags(flags);
        payload = payload == null ? new byte[0] : Arrays.copyOf(payload, payload.length);
        if (payload.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("payload too large");
        }
        if (hasFlag(flags, FLAG_ACK) && payload.length != 0) {
            throw new IllegalArgumentException("ACK packet cannot carry payload");
        }
        checksum = checksum & 0xffff_ffffL;
    }

    public static ReliableDataPacket data(int sequenceNumber, boolean fin, byte[] payload) {
        byte flags = fin ? (byte) (FLAG_DATA | FLAG_FIN) : FLAG_DATA;
        return new ReliableDataPacket(sequenceNumber, flags, 0L, payload);
    }

    public static ReliableDataPacket ack(int sequenceNumber) {
        return new ReliableDataPacket(sequenceNumber, FLAG_ACK, 0L, new byte[0]);
    }

    public static ReliableDataPacket decode(byte[] datagram) {
        if (datagram == null || datagram.length < HEADER_BYTES) {
            throw new IllegalArgumentException("Datagram shorter than packet header");
        }
        ByteBuffer buffer = ByteBuffer.wrap(datagram);
        int sequenceNumber = buffer.getInt();
        byte flags = buffer.get();
        int payloadLength = Short.toUnsignedInt(buffer.getShort());
        long transmittedChecksum = Integer.toUnsignedLong(buffer.getInt());
        if (datagram.length != HEADER_BYTES + payloadLength) {
            throw new IllegalArgumentException("Datagram payload length mismatch");
        }
        long computedChecksum = checksum(datagram, true);
        if (computedChecksum != transmittedChecksum) {
            throw new ChecksumMismatchException("Packet checksum mismatch");
        }
        byte[] payload = Arrays.copyOfRange(datagram, HEADER_BYTES, datagram.length);
        return new ReliableDataPacket(sequenceNumber, flags, transmittedChecksum, payload);
    }

    public byte[] encode() {
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_BYTES + payload.length);
        buffer.putInt(sequenceNumber);
        buffer.put(flags);
        buffer.putShort((short) payload.length);
        buffer.putInt(0);
        buffer.put(payload);
        byte[] datagram = buffer.array();
        long computedChecksum = checksum(datagram, false);
        ByteBuffer.wrap(datagram).putInt(CHECKSUM_OFFSET, (int) computedChecksum);
        return datagram;
    }

    @Override
    public byte[] payload() {
        return Arrays.copyOf(payload, payload.length);
    }

    public boolean hasFlag(byte flag) {
        return hasFlag(flags, flag);
    }

    public boolean isAckFor(int sequenceNumber) {
        return hasFlag(FLAG_ACK) && this.sequenceNumber == sequenceNumber;
    }

    private static boolean hasFlag(byte flags, byte flag) {
        return (flags & flag) == flag;
    }

    private static void validateFlags(byte flags) {
        boolean data = hasFlag(flags, FLAG_DATA);
        boolean ack = hasFlag(flags, FLAG_ACK);
        if (data == ack) {
            throw new IllegalArgumentException("Exactly one of DATA or ACK flag is required");
        }
        if (ack && hasFlag(flags, FLAG_FIN)) {
            throw new IllegalArgumentException("ACK packet cannot carry FIN");
        }
    }

    private static long checksum(byte[] datagram, boolean zeroChecksumField) {
        byte[] copy = zeroChecksumField ? Arrays.copyOf(datagram, datagram.length) : datagram;
        copy[CHECKSUM_OFFSET] = 0;
        copy[CHECKSUM_OFFSET + 1] = 0;
        copy[CHECKSUM_OFFSET + 2] = 0;
        copy[CHECKSUM_OFFSET + 3] = 0;
        CRC32 crc32 = new CRC32();
        crc32.update(copy, 0, copy.length);
        return crc32.getValue();
    }

    public static final class ChecksumMismatchException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        public ChecksumMismatchException(String message) {
            super(message);
        }
    }
}
