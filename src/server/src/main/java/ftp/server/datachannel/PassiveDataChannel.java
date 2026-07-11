package ftp.server.datachannel;

import ftp.protocol.rdt.PacketEndpoint;
import ftp.protocol.rdt.ReliableDataPacket;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

public final class PassiveDataChannel implements PacketEndpoint {
    private static final int MAX_DATAGRAM_BYTES = ReliableDataPacket.HEADER_BYTES + ReliableDataPacket.MAX_PAYLOAD_BYTES;

    private final DatagramSocket socket;
    private final Inet4Address advertisedAddress;
    private InetSocketAddress peer;

    public PassiveDataChannel(InetAddress bindAddress) throws IOException {
        advertisedAddress = requireIpv4(bindAddress);
        socket = new DatagramSocket(0, advertisedAddress);
        socket.setSoTimeout(2_000);
    }

    public int port() {
        return socket.getLocalPort();
    }

    public String replyTuple() {
        byte[] address = advertisedAddress.getAddress();
        int p1 = port() / 256;
        int p2 = port() % 256;
        return "%d,%d,%d,%d,%d,%d".formatted(
                Byte.toUnsignedInt(address[0]),
                Byte.toUnsignedInt(address[1]),
                Byte.toUnsignedInt(address[2]),
                Byte.toUnsignedInt(address[3]),
                p1,
                p2);
    }

    public void awaitPeer(Duration timeout) throws IOException, TimeoutException {
        socket.setSoTimeout(Math.toIntExact(timeout.toMillis()));
        byte[] buffer = new byte[MAX_DATAGRAM_BYTES];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        try {
            socket.receive(packet);
        } catch (SocketTimeoutException exception) {
            TimeoutException timeoutException = new TimeoutException("Timed out waiting for data channel peer");
            timeoutException.initCause(exception);
            throw timeoutException;
        }
        peer = (InetSocketAddress) packet.getSocketAddress();
    }

    @Override
    public void send(byte[] datagram) throws IOException {
        if (peer == null) {
            throw new IOException("Data channel peer is unknown");
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
            TimeoutException timeoutException = new TimeoutException("Timed out waiting for UDP datagram");
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

    private static Inet4Address requireIpv4(InetAddress address) {
        Objects.requireNonNull(address, "bindAddress");
        if (address instanceof Inet4Address inet4Address) {
            return inet4Address;
        }
        throw new IllegalArgumentException("PASV requires an IPv4 bind address");
    }
}