package ftp.server.command;

import ftp.protocol.control.ControlMessage;
import ftp.server.ClientSession;
import ftp.server.FileSystemRoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CommandDispatcherMetadataCommandsTest {
    @TempDir
    Path rootPath;

    @Test
    void sizeReturnsByteLengthForRegularFile() throws Exception {
        Files.writeString(rootPath.resolve("readme.txt"), "hello");
        ClientSession session = authenticatedSession();
        CommandDispatcher dispatcher = new CommandDispatcher();

        CommandResult result = dispatch(dispatcher, session, "SIZE", "readme.txt");

        assertEquals(213, result.primaryReply().replyCode());
        assertEquals("5", result.primaryReply().replyText());
    }

    @Test
    void sizeRejectsDirectoryAndMissingPath() throws Exception {
        Files.createDirectories(rootPath.resolve("drafts"));
        ClientSession session = authenticatedSession();
        CommandDispatcher dispatcher = new CommandDispatcher();

        assertEquals(550, dispatch(dispatcher, session, "SIZE", "drafts").primaryReply().replyCode());
        assertEquals(550, dispatch(dispatcher, session, "SIZE", "missing.txt").primaryReply().replyCode());
    }

    @Test
    void mdtmReturnsDeterministicTimestampFormat() throws Exception {
        Path file = rootPath.resolve("readme.txt");
        Files.writeString(file, "hello");
        Files.setLastModifiedTime(file, FileTime.from(Instant.parse("2026-07-17T12:34:56Z")));
        ClientSession session = authenticatedSession();
        CommandDispatcher dispatcher = new CommandDispatcher();

        CommandResult result = dispatch(dispatcher, session, "MDTM", "readme.txt");

        assertEquals(213, result.primaryReply().replyCode());
        assertEquals("20260717123456", result.primaryReply().replyText());
    }

    @Test
    void mdtmRejectsMissingPath() throws Exception {
        ClientSession session = authenticatedSession();
        CommandDispatcher dispatcher = new CommandDispatcher();

        assertEquals(550, dispatch(dispatcher, session, "MDTM", "missing.txt").primaryReply().replyCode());
    }

    @Test
    void statReportsFileMetadataWithTypeAndSize() throws Exception {
        Files.writeString(rootPath.resolve("readme.txt"), "hello");
        ClientSession session = authenticatedSession();
        CommandDispatcher dispatcher = new CommandDispatcher();

        CommandResult result = dispatch(dispatcher, session, "STAT", "readme.txt");

        assertEquals(213, result.primaryReply().replyCode());
        assertEquals("- readme.txt 5", result.primaryReply().replyText());
    }

    @Test
    void statReportsDirectoryMetadataWithType() throws Exception {
        Files.createDirectories(rootPath.resolve("drafts"));
        ClientSession session = authenticatedSession();
        CommandDispatcher dispatcher = new CommandDispatcher();

        CommandResult result = dispatch(dispatcher, session, "STAT", "drafts");

        assertEquals(213, result.primaryReply().replyCode());
        assertEquals("d drafts", result.primaryReply().replyText());
    }

    @Test
    void statRejectsMissingPath() throws Exception {
        ClientSession session = authenticatedSession();
        CommandDispatcher dispatcher = new CommandDispatcher();

        assertEquals(550, dispatch(dispatcher, session, "STAT", "missing").primaryReply().replyCode());
    }

    @Test
    void metadataCommandsRejectUnauthenticatedSession() throws Exception {
        Files.writeString(rootPath.resolve("readme.txt"), "hello");
        ClientSession session = new ClientSession("s1", new FileSystemRoot(rootPath));
        CommandDispatcher dispatcher = new CommandDispatcher();

        assertEquals(530, dispatch(dispatcher, session, "SIZE", "readme.txt").primaryReply().replyCode());
        assertEquals(530, dispatch(dispatcher, session, "MDTM", "readme.txt").primaryReply().replyCode());
        assertEquals(530, dispatch(dispatcher, session, "STAT", "readme.txt").primaryReply().replyCode());
    }

    @Test
    void sizeAndMdtmRejectHostAbsolutePathOutsideRoot() throws Exception {
        Path outsideRoot = Files.createDirectory(rootPath.resolveSibling(rootPath.getFileName() + "-outside"));
        Path outsideFile = Files.writeString(outsideRoot.resolve("secret.txt"), "hi");
        ClientSession session = authenticatedSession();
        CommandDispatcher dispatcher = new CommandDispatcher();

        assertEquals(550, dispatch(dispatcher, session, "SIZE", outsideFile.toString()).primaryReply().replyCode());
        assertTrue(dispatch(dispatcher, session, "STAT", outsideFile.toString()).primaryReply().replyCode() == 550);
    }

    private ClientSession authenticatedSession() throws Exception {
        ClientSession session = new ClientSession("s1", new FileSystemRoot(rootPath));
        session.markUserSent("alice");
        session.markAuthenticated();
        return session;
    }

    private static CommandResult dispatch(
            CommandDispatcher dispatcher,
            ClientSession session,
            String verb,
            String argument) {
        return dispatcher.dispatch(session, ControlMessage.command(verb, argument));
    }
}
