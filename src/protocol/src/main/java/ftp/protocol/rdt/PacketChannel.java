package ftp.protocol.rdt;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

public interface PacketChannel extends AutoCloseable {
    void send(byte[] datagram) throws IOException;

    byte[] receive(Duration timeout) throws IOException, TimeoutException;

    @Override
    default void close() throws IOException {
    }
}
