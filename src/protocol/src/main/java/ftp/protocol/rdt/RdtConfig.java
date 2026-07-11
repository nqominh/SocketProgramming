package ftp.protocol.rdt;

import java.time.Duration;

public record RdtConfig(int maxPayloadBytes, Duration ackTimeout, int maxRetries, int maxReceiveTimeouts) {
    public static final int DEFAULT_MAX_PAYLOAD_BYTES = 1_200;

    public RdtConfig {
        if (maxPayloadBytes < 1 || maxPayloadBytes > ReliableDataPacket.MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("maxPayloadBytes out of range");
        }
        if (ackTimeout == null || ackTimeout.isZero() || ackTimeout.isNegative()) {
            throw new IllegalArgumentException("ackTimeout must be positive");
        }
        if (maxRetries < 1) {
            throw new IllegalArgumentException("maxRetries must be positive");
        }
        if (maxReceiveTimeouts < 1) {
            throw new IllegalArgumentException("maxReceiveTimeouts must be positive");
        }
    }

    public static RdtConfig defaults() {
        return new RdtConfig(DEFAULT_MAX_PAYLOAD_BYTES, Duration.ofMillis(250), 8, 120);
    }
}
