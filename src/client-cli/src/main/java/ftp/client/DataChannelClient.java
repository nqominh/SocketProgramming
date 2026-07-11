package ftp.client;

import ftp.protocol.control.ControlMessage;
import ftp.protocol.rdt.DatagramPacketEndpoint;
import ftp.protocol.rdt.RdtConfig;
import ftp.protocol.rdt.RdtReceiver;
import ftp.protocol.rdt.RdtSender;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DataChannelClient implements AutoCloseable {
    private static final Pattern PASV_REPLY = Pattern.compile(".*\\((\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+)\\).*");
    private static final byte[] READY_DATAGRAM = new byte[] {0};

    private final DatagramPacketEndpoint endpoint;
    private final RdtConfig config;

    private DataChannelClient(DatagramPacketEndpoint endpoint, RdtConfig config) {
        this.endpoint = endpoint;
        this.config = config;
    }

    public static DataChannelClient fromPassiveReply(ControlMessage reply, Duration timeout) throws IOException {
        if (reply.replyCode() != 227) {
            throw new IllegalArgumentException("Expected 227 PASV reply");
        }
        Matcher matcher = PASV_REPLY.matcher(reply.replyText());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Malformed PASV reply: " + reply.replyText());
        }
        byte[] address = new byte[4];
        for (int index = 0; index < address.length; index++) {
            address[index] = (byte) Integer.parseInt(matcher.group(index + 1));
        }
        int port = (Integer.parseInt(matcher.group(5)) * 256) + Integer.parseInt(matcher.group(6));
        DatagramSocket socket = new DatagramSocket();
        socket.setSoTimeout(Math.toIntExact(timeout.toMillis()));
        InetSocketAddress peer = new InetSocketAddress(InetAddress.getByAddress(address), port);
        return new DataChannelClient(new DatagramPacketEndpoint(socket, peer), RdtConfig.defaults());
    }

    public void upload(byte[] payload) throws IOException {
        try {
            new RdtSender(config).send(payload, endpoint);
        } finally {
            close();
        }
    }

    public byte[] download() throws IOException {
        try {
            endpoint.send(READY_DATAGRAM);
            return new RdtReceiver(config).receiveBytes(endpoint);
        } finally {
            close();
        }
    }

    @Override
    public void close() {
        endpoint.close();
    }
}
