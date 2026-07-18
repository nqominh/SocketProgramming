package ftp.server.command;

import ftp.protocol.control.ControlMessage;
import ftp.protocol.rdt.PacketChannel;
import ftp.protocol.rdt.RdtConfig;
import ftp.protocol.rdt.RdtReceiver;
import ftp.protocol.rdt.RdtSender;
import ftp.server.ClientSession;
import ftp.server.datachannel.ActiveDataChannel;
import ftp.server.datachannel.DataChannelService;
import ftp.server.datachannel.PassiveDataChannel;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeoutException;

public final class CommandDispatcher {
    private static final DateTimeFormatter MDTM_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);
    private static final RdtConfig ACTIVE_RDT_CONFIG = new RdtConfig(
            RdtConfig.DEFAULT_MAX_PAYLOAD_BYTES,
            Duration.ofMillis(100),
            8,
            5);

    private final DataChannelService dataChannelService;
    private final Map<String, CommandHandler> handlers;
    private final RdtConfig rdtConfig = RdtConfig.defaults();

    public CommandDispatcher() {
        this(new DataChannelService());
    }

    public CommandDispatcher(DataChannelService dataChannelService) {
        this.dataChannelService = dataChannelService;
        handlers = Map.ofEntries(
                Map.entry("USER", this::handleUser),
                Map.entry("PASS", this::handlePass),
                Map.entry("NOOP", (session, command) -> CommandResult.single(ReplyFactory.ok())),
                Map.entry("QUIT", (session, command) -> CommandResult.closing(ReplyFactory.goodbye())),
                Map.entry("PWD", this::handlePwd),
                Map.entry("CWD", this::handleCwd),
                Map.entry("CDUP", this::handleCdup),
                Map.entry("MKD", this::handleMkd),
                Map.entry("RMD", this::handleRmd),
                Map.entry("NLST", this::handleNlst),
                Map.entry("LIST", this::handleList),
                Map.entry("SIZE", this::handleSize),
                Map.entry("MDTM", this::handleMdtm),
                Map.entry("STAT", this::handleStat),
                Map.entry("TYPE", this::handleType),
                Map.entry("MODE", this::handleMode),
                Map.entry("PORT", this::handlePort),
                Map.entry("PASV", this::handlePassive),
                Map.entry("STOR", this::handleStor),
                Map.entry("RETR", this::handleRetr));
    }

    public CommandResult dispatch(ClientSession session, ControlMessage command) {
        String verb = command.verb().toUpperCase(Locale.ROOT);
        CommandHandler handler = handlers.get(verb);
        if (handler == null) {
            return CommandResult.single(ReplyFactory.commandNotImplemented());
        }
        return handler.handle(session, command);
    }

    private CommandResult handleUser(ClientSession session, ControlMessage command) {
        String username = command.argument();
        if (username == null || username.isBlank()) {
            return CommandResult.single(ReplyFactory.usernameRequired());
        }
        session.markUserSent(username);
        return CommandResult.single(ReplyFactory.passwordRequired());
    }

    private CommandResult handlePass(ClientSession session, ControlMessage command) {
        String password = command.argument();
        if (session.authState() != ClientSession.AuthState.USER_SENT || password == null || password.isBlank()) {
            return CommandResult.single(ReplyFactory.loginIncorrect());
        }
        session.markAuthenticated();
        return CommandResult.single(ReplyFactory.loggedIn());
    }

    private CommandResult handlePwd(ClientSession session, ControlMessage command) {
        CommandResult authFailure = authenticated(session);
        if (authFailure != null) {
            return authFailure;
        }
        return CommandResult.single(ReplyFactory.pathCreated(session.currentDirectory()));
    }

    private CommandResult handleCwd(ClientSession session, ControlMessage command) {
        CommandResult authFailure = authenticated(session);
        if (authFailure != null) {
            return authFailure;
        }
        try {
            session.changeDirectory(command.argument());
            return CommandResult.single(ReplyFactory.directoryChanged());
        } catch (IOException | SecurityException exception) {
            return CommandResult.single(ReplyFactory.fileUnavailable());
        }
    }

    private CommandResult handleCdup(ClientSession session, ControlMessage command) {
        CommandResult authFailure = authenticated(session);
        if (authFailure != null) {
            return authFailure;
        }
        try {
            session.changeDirectory("..");
            return CommandResult.single(ReplyFactory.directoryChanged());
        } catch (IOException | SecurityException exception) {
            return CommandResult.single(ReplyFactory.fileUnavailable());
        }
    }

    private CommandResult handleMkd(ClientSession session, ControlMessage command) {
        CommandResult authFailure = authenticated(session);
        if (authFailure != null) {
            return authFailure;
        }
        try {
            Path directory = session.resolvePath(command.argument());
            Files.createDirectory(directory);
            return CommandResult.single(ReplyFactory.pathCreated(command.argument()));
        } catch (IOException | SecurityException exception) {
            return CommandResult.single(ReplyFactory.fileUnavailable());
        }
    }

    private CommandResult handleRmd(ClientSession session, ControlMessage command) {
        CommandResult authFailure = authenticated(session);
        if (authFailure != null) {
            return authFailure;
        }
        try {
            Path directory = session.resolvePath(command.argument());
            if (!Files.isDirectory(directory)) {
                return CommandResult.single(ReplyFactory.fileUnavailable());
            }
            Files.delete(directory);
            return CommandResult.single(ReplyFactory.directoryRemoved());
        } catch (IOException | SecurityException exception) {
            return CommandResult.single(ReplyFactory.fileUnavailable());
        }
    }

    private CommandResult handleNlst(ClientSession session, ControlMessage command) {
        CommandResult authFailure = authenticated(session);
        if (authFailure != null) {
            return authFailure;
        }
        try {
            List<Path> entries = sortedDirectoryEntries(session, command.argument());
            List<String> names = new ArrayList<>();
            for (Path entry : entries) {
                names.add(entry.getFileName().toString());
            }
            return CommandResult.single(ReplyFactory.listing(String.join(";", names)));
        } catch (IOException | SecurityException exception) {
            return CommandResult.single(ReplyFactory.fileUnavailable());
        }
    }

    private CommandResult handleList(ClientSession session, ControlMessage command) {
        CommandResult authFailure = authenticated(session);
        if (authFailure != null) {
            return authFailure;
        }
        try {
            List<Path> entries = sortedDirectoryEntries(session, command.argument());
            List<String> lines = new ArrayList<>();
            for (Path entry : entries) {
                lines.add(Files.isDirectory(entry)
                        ? "d " + entry.getFileName()
                        : "- " + entry.getFileName() + " " + Files.size(entry));
            }
            return CommandResult.single(ReplyFactory.listing(String.join(";", lines)));
        } catch (IOException | SecurityException exception) {
            return CommandResult.single(ReplyFactory.fileUnavailable());
        }
    }

    private List<Path> sortedDirectoryEntries(ClientSession session, String argument) throws IOException {
        Path directory = session.resolvePath(argument);
        if (!Files.isDirectory(directory)) {
            throw new IOException("Not a directory: " + argument);
        }
        List<Path> entries = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path entry : stream) {
                entries.add(entry);
            }
        }
        entries.sort(Comparator.comparing(entry -> entry.getFileName().toString()));
        return entries;
    }

    private CommandResult handleSize(ClientSession session, ControlMessage command) {
        CommandResult authFailure = authenticated(session);
        if (authFailure != null) {
            return authFailure;
        }
        try {
            Path target = session.resolvePath(command.argument());
            if (!Files.isRegularFile(target)) {
                return CommandResult.single(ReplyFactory.fileUnavailable());
            }
            return CommandResult.single(ReplyFactory.fileStatus(Long.toString(Files.size(target))));
        } catch (IOException | SecurityException exception) {
            return CommandResult.single(ReplyFactory.fileUnavailable());
        }
    }

    private CommandResult handleMdtm(ClientSession session, ControlMessage command) {
        CommandResult authFailure = authenticated(session);
        if (authFailure != null) {
            return authFailure;
        }
        try {
            Path target = session.resolvePath(command.argument());
            if (!Files.exists(target)) {
                return CommandResult.single(ReplyFactory.fileUnavailable());
            }
            String timestamp = MDTM_FORMAT.format(Files.getLastModifiedTime(target).toInstant());
            return CommandResult.single(ReplyFactory.fileStatus(timestamp));
        } catch (IOException | SecurityException exception) {
            return CommandResult.single(ReplyFactory.fileUnavailable());
        }
    }

    private CommandResult handleStat(ClientSession session, ControlMessage command) {
        CommandResult authFailure = authenticated(session);
        if (authFailure != null) {
            return authFailure;
        }
        try {
            Path target = session.resolvePath(command.argument());
            if (Files.isDirectory(target)) {
                return CommandResult.single(ReplyFactory.fileStatus("d " + target.getFileName()));
            }
            if (Files.isRegularFile(target)) {
                return CommandResult.single(
                        ReplyFactory.fileStatus("- " + target.getFileName() + " " + Files.size(target)));
            }
            return CommandResult.single(ReplyFactory.fileUnavailable());
        } catch (IOException | SecurityException exception) {
            return CommandResult.single(ReplyFactory.fileUnavailable());
        }
    }

    private CommandResult handleType(ClientSession session, ControlMessage command) {
        CommandResult authFailure = authenticated(session);
        if (authFailure != null) {
            return authFailure;
        }
        String type = command.argument().trim().toUpperCase(Locale.ROOT);
        if ("A".equals(type)) {
            session.setTransferType(ClientSession.TransferType.ASCII);
            return CommandResult.single(ReplyFactory.ok());
        }
        if ("I".equals(type)) {
            session.setTransferType(ClientSession.TransferType.BINARY);
            return CommandResult.single(ReplyFactory.ok());
        }
        return CommandResult.single(ReplyFactory.unsupportedParameter());
    }

    private CommandResult handleMode(ClientSession session, ControlMessage command) {
        CommandResult authFailure = authenticated(session);
        if (authFailure != null) {
            return authFailure;
        }
        String mode = command.argument().trim().toUpperCase(Locale.ROOT);
        if ("S".equals(mode)) {
            session.setTransferMode(ClientSession.TransferMode.STREAM);
            return CommandResult.single(ReplyFactory.ok());
        }
        return CommandResult.single(ReplyFactory.unsupportedParameter());
    }

    private CommandResult handlePort(ClientSession session, ControlMessage command) {
        CommandResult authFailure = authenticated(session);
        if (authFailure != null) {
            return authFailure;
        }
        try {
            session.replaceActiveDataEndpoint(parsePortEndpoint(command.argument()));
            return CommandResult.single(ReplyFactory.ok());
        } catch (IllegalArgumentException | IOException exception) {
            return CommandResult.single(ReplyFactory.invalidParameter());
        }
    }

    private static InetSocketAddress parsePortEndpoint(String argument) throws IOException {
        String[] parts = argument.split(",");
        if (parts.length != 6) {
            throw new IllegalArgumentException("PORT requires h1,h2,h3,h4,p1,p2");
        }
        byte[] address = new byte[4];
        for (int index = 0; index < address.length; index++) {
            address[index] = (byte) parsePortByte(parts[index]);
        }
        int high = parsePortByte(parts[4]);
        int low = parsePortByte(parts[5]);
        int port = (high * 256) + low;
        if (port == 0) {
            throw new IllegalArgumentException("PORT cannot target UDP port 0");
        }
        return new InetSocketAddress(InetAddress.getByAddress(address), port);
    }

    private static int parsePortByte(String value) {
        int parsed = Integer.parseInt(value.trim());
        if (parsed < 0 || parsed > 255) {
            throw new IllegalArgumentException("PORT byte out of range: " + value);
        }
        return parsed;
    }

    private CommandResult handlePassive(ClientSession session, ControlMessage command) {
        if (session.authState() != ClientSession.AuthState.AUTHENTICATED) {
            return CommandResult.single(ReplyFactory.notLoggedIn());
        }
        try {
            PassiveDataChannel dataChannel = dataChannelService.openPassive(session);
            return CommandResult.single(ReplyFactory.passiveMode(dataChannel.replyTuple()));
        } catch (IOException exception) {
            return CommandResult.single(ReplyFactory.cannotOpenDataConnection());
        }
    }

    private CommandResult handleStor(ClientSession session, ControlMessage command) {
        CommandResult authFailure = authenticated(session);
        if (authFailure != null) {
            return authFailure;
        }
        PacketChannel dataChannel;
        try {
            dataChannel = dataChannelService.openSelectedDataChannel(session);
        } catch (IOException exception) {
            return CommandResult.single(ReplyFactory.cannotOpenDataConnection());
        }
        if (dataChannel == null) {
            return CommandResult.single(ReplyFactory.noDataConnection());
        }
        Path temporaryTarget = null;
        try (PacketChannel closeableDataChannel = dataChannel) {
            if (closeableDataChannel instanceof ActiveDataChannel activeDataChannel) {
                activeDataChannel.signalPeerReady();
            }
            Path target = session.resolvePath(command.argument());
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            temporaryTarget = Files.createTempFile(parent, "upload-", ".part");
            try (OutputStream outputStream = Files.newOutputStream(
                    temporaryTarget,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {
                new RdtReceiver(transferConfig(closeableDataChannel)).receive(closeableDataChannel, outputStream);
            }
            Files.move(temporaryTarget, target, StandardCopyOption.REPLACE_EXISTING);
            return transferComplete();
        } catch (IOException exception) {
            deleteQuietly(temporaryTarget);
            return CommandResult.single(ReplyFactory.localError());
        } catch (SecurityException exception) {
            deleteQuietly(temporaryTarget);
            return CommandResult.single(ReplyFactory.fileUnavailable());
        }
    }

    private CommandResult handleRetr(ClientSession session, ControlMessage command) {
        CommandResult authFailure = authenticated(session);
        if (authFailure != null) {
            return authFailure;
        }
        PacketChannel dataChannel;
        try {
            dataChannel = dataChannelService.openSelectedDataChannel(session);
        } catch (IOException exception) {
            return CommandResult.single(ReplyFactory.cannotOpenDataConnection());
        }
        if (dataChannel == null) {
            return CommandResult.single(ReplyFactory.noDataConnection());
        }
        try (PacketChannel closeableDataChannel = dataChannel) {
            Path source = session.resolvePath(command.argument());
            if (!Files.isRegularFile(source)) {
                return CommandResult.single(ReplyFactory.fileUnavailable());
            }
            if (closeableDataChannel instanceof PassiveDataChannel passiveDataChannel) {
                passiveDataChannel.awaitPeer(rdtConfig.ackTimeout());
            }
            try (InputStream inputStream = Files.newInputStream(source, StandardOpenOption.READ)) {
                new RdtSender(transferConfig(closeableDataChannel)).send(inputStream, closeableDataChannel);
            }
            return transferComplete();
        } catch (TimeoutException | IOException exception) {
            return CommandResult.single(ReplyFactory.localError());
        } catch (SecurityException exception) {
            return CommandResult.single(ReplyFactory.fileUnavailable());
        }
    }

    private CommandResult authenticated(ClientSession session) {
        if (session.authState() != ClientSession.AuthState.AUTHENTICATED) {
            return CommandResult.single(ReplyFactory.notLoggedIn());
        }
        return null;
    }

    private RdtConfig transferConfig(PacketChannel channel) {
        return channel instanceof ActiveDataChannel ? ACTIVE_RDT_CONFIG : rdtConfig;
    }

    private static void deleteQuietly(Path target) {
        if (target == null) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // Best-effort cleanup; the transfer already failed and must still return a control reply.
        }
    }

    private static CommandResult transferComplete() {
        return new CommandResult(List.of(ReplyFactory.openingDataConnection(), ReplyFactory.transferComplete()), false);
    }
}
