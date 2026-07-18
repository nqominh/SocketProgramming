package ftp.server.datachannel;

import ftp.protocol.rdt.PacketChannel;
import ftp.protocol.rdt.ReliableDataPacket;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

public final class ActiveDataChannel implements PacketChannel {
    private static final int MAX_DATAGRAM_BYTES = ReliableDataPacket.HEADER_BYTES + ReliableDataPacket.MAX_PAYLOAD_BYTES;
    private static final byte[] READY_DATAGRAM = new byte[] {0};

    private final DatagramSocket socket;
    private final InetSocketAddress peer;

    public ActiveDataChannel(InetAddress bindAddress, InetSocketAddress peer) throws IOException {
        socket = new DatagramSocket(0, Objects.requireNonNull(bindAddress, "bindAddress"));
        socket.setSoTimeout(2_000);
        this.peer = Objects.requireNonNull(peer, "peer");
    }

    public void signalPeerReady() throws IOException {
        send(READY_DATAGRAM);
    }

    @Override
    public void send(byte[] datagram) throws IOException {
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
        return Arrays.copyOf(packet.getData(), packet.getLength());
    }

    @Override
    public void close() {
        socket.close();
    }
}
