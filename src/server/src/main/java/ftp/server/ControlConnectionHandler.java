package ftp.server;

import ftp.protocol.control.ControlCodec;
import ftp.protocol.control.ControlMessage;
import ftp.server.command.CommandDispatcher;
import ftp.server.command.CommandResult;
import ftp.server.command.ReplyFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public final class ControlConnectionHandler {
    private final Socket socket;
    private final Path root;
    private final String sessionId;
    private final CommandDispatcher commandDispatcher;
    private final PrintStream log;

    public ControlConnectionHandler(Socket socket, Path root, String sessionId, CommandDispatcher commandDispatcher) {
        this(socket, root, sessionId, commandDispatcher, System.out);
    }

    ControlConnectionHandler(
            Socket socket,
            Path root,
            String sessionId,
            CommandDispatcher commandDispatcher,
            PrintStream log) {
        this.socket = socket;
        this.root = root;
        this.sessionId = sessionId;
        this.commandDispatcher = commandDispatcher;
        this.log = log;
    }

    public void handle() throws IOException {
        socket.setSoTimeout(2_000);
        try (Socket closeableSocket = socket;
             ClientSession session = new ClientSession(sessionId, new FileSystemRoot(root), passiveBindAddress(socket));
             BufferedReader reader = new BufferedReader(new InputStreamReader(closeableSocket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(closeableSocket.getOutputStream(), StandardCharsets.UTF_8))) {
            writeReply(writer, ReplyFactory.ready());

            String clientIp = closeableSocket.getInetAddress().getHostAddress();
            String line;
            while ((line = reader.readLine()) != null) {
                CommandResult result = dispatch(session, line);
                writeResult(writer, result);
                logResult(clientIp, line, result);
                if (result.closeAfterReply()) {
                    return;
                }
            }
        }
    }

    private CommandResult dispatch(ClientSession session, String line) {
        try {
            return commandDispatcher.dispatch(session, ControlCodec.parseCommand(line));
        } catch (IllegalArgumentException exception) {
            return CommandResult.single(ReplyFactory.syntaxError());
        }
    }

    private void logResult(String clientIp, String commandLine, CommandResult result) {
        ControlMessage reply = result.primaryReply();
        log.println(ServerLog.commandLine(clientIp, sessionId, commandLine, formatReply(reply)));
        if (isTransferCommand(commandLine) && result.replies().stream().anyMatch(message -> message.replyCode() == 226)) {
            log.println(ServerLog.progressLine(clientIp, sessionId, commandLine, "complete"));
        }
    }

    private static boolean isTransferCommand(String commandLine) {
        String upper = commandLine.toUpperCase(java.util.Locale.ROOT);
        return upper.startsWith("STOR ") || upper.equals("STOR") || upper.startsWith("RETR ") || upper.equals("RETR");
    }

    private static String formatReply(ControlMessage reply) {
        return reply.replyText().isBlank()
                ? "%03d".formatted(reply.replyCode())
                : "%03d %s".formatted(reply.replyCode(), reply.replyText());
    }

    private static void writeResult(BufferedWriter writer, CommandResult result) throws IOException {
        for (ControlMessage reply : result.replies()) {
            writer.write(ControlCodec.formatReply(reply));
        }
        writer.flush();
    }

    private static void writeReply(BufferedWriter writer, ControlMessage reply) throws IOException {
        writer.write(ControlCodec.formatReply(reply));
        writer.flush();
    }

    private static InetAddress passiveBindAddress(Socket socket) {
        InetAddress localAddress = socket.getLocalAddress();
        if (localAddress.isAnyLocalAddress()) {
            return InetAddress.getLoopbackAddress();
        }
        return localAddress;
    }
}