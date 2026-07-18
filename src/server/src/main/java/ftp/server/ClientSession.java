package ftp.server;

import ftp.server.datachannel.PassiveDataChannel;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class ClientSession implements AutoCloseable {
    public enum AuthState {
        UNAUTHENTICATED,
        USER_SENT,
        AUTHENTICATED
    }

    public enum TransferType {
        ASCII,
        BINARY
    }

    public enum TransferMode {
        STREAM
    }

    private final String sessionId;
    private final FileSystemRoot fileSystemRoot;
    private final InetAddress passiveBindAddress;
    private String currentDirectory = "/";
    private TransferType transferType = TransferType.ASCII;
    private TransferMode transferMode = TransferMode.STREAM;
    private AuthState authState = AuthState.UNAUTHENTICATED;
    private String username = "";
    private PassiveDataChannel passiveDataChannel;

    public ClientSession(String sessionId, FileSystemRoot fileSystemRoot) {
        this(sessionId, fileSystemRoot, InetAddress.getLoopbackAddress());
    }

    public ClientSession(String sessionId, FileSystemRoot fileSystemRoot, InetAddress passiveBindAddress) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.fileSystemRoot = Objects.requireNonNull(fileSystemRoot, "fileSystemRoot");
        this.passiveBindAddress = Objects.requireNonNull(passiveBindAddress, "passiveBindAddress");
    }

    public String sessionId() {
        return sessionId;
    }

    public String currentDirectory() {
        return currentDirectory;
    }

    public TransferType transferType() {
        return transferType;
    }

    public TransferMode transferMode() {
        return transferMode;
    }

    public AuthState authState() {
        return authState;
    }

    public String username() {
        return username;
    }

    public InetAddress passiveBindAddress() {
        return passiveBindAddress;
    }

    public void markUserSent(String username) {
        this.username = Objects.requireNonNull(username, "username");
        authState = AuthState.USER_SENT;
    }

    public void markAuthenticated() {
        authState = AuthState.AUTHENTICATED;
    }

    public void setTransferType(TransferType transferType) {
        this.transferType = Objects.requireNonNull(transferType, "transferType");
    }

    public void setTransferMode(TransferMode transferMode) {
        this.transferMode = Objects.requireNonNull(transferMode, "transferMode");
    }

    public void replacePassiveDataChannel(PassiveDataChannel dataChannel) {
        closePassiveDataChannel();
        passiveDataChannel = Objects.requireNonNull(dataChannel, "dataChannel");
    }

    public PassiveDataChannel takePassiveDataChannel() {
        PassiveDataChannel dataChannel = passiveDataChannel;
        passiveDataChannel = null;
        return dataChannel;
    }

    public Path resolvePath(String inputPath) throws IOException {
        return fileSystemRoot.resolve(currentDirectory, inputPath);
    }

    public void changeDirectory(String inputPath) throws IOException {
        Path resolved = resolvePath(inputPath);
        if (!Files.isDirectory(resolved)) {
            throw new IOException("Directory does not exist: " + inputPath);
        }
        currentDirectory = fileSystemRoot.toVirtualPath(resolved);
    }

    @Override
    public void close() {
        closePassiveDataChannel();
    }

    private void closePassiveDataChannel() {
        if (passiveDataChannel != null) {
            passiveDataChannel.close();
            passiveDataChannel = null;
        }
    }
}
