package ftp.client;

import ftp.protocol.control.ControlCodec;
import ftp.protocol.control.ControlMessage;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class ControlChannelClient implements AutoCloseable {
    private final Socket socket;
    private final BufferedReader reader;
    private final BufferedWriter writer;

    private ControlChannelClient(Socket socket) throws IOException {
        this.socket = socket;
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    public static ControlChannelClient connect(String host, int port, Duration timeout) throws IOException {
        Socket socket = new Socket();
        int timeoutMillis = Math.toIntExact(timeout.toMillis());
        socket.connect(new InetSocketAddress(host, port), timeoutMillis);
        socket.setSoTimeout(timeoutMillis);
        return new ControlChannelClient(socket);
    }

    public ControlMessage readReply() throws IOException {
        String line = reader.readLine();
        if (line == null) {
            throw new IOException("Control channel closed before reply");
        }
        return ControlCodec.parseReply(line);
    }

    public void sendCommand(String verb, String argument) throws IOException {
        writer.write(ControlCodec.formatCommand(ControlMessage.command(verb, argument)));
        writer.flush();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}