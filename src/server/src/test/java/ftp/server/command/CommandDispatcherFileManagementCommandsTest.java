package ftp.server.command;

import ftp.protocol.control.ControlMessage;
import ftp.server.ClientSession;
import ftp.server.FileSystemRoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CommandDispatcherFileManagementCommandsTest {
    @TempDir
    Path rootPath;

    @Test
    void deleRemovesOneRegularFileInsideRoot() throws Exception {
        Files.writeString(rootPath.resolve("remove.txt"), "remove me");
        Files.writeString(rootPath.resolve("keep.txt"), "keep me");
        ClientSession session = authenticatedSession();
        CommandDispatcher dispatcher = new CommandDispatcher();

        CommandResult result = dispatch(dispatcher, session, "DELE", "remove.txt");

        assertEquals(250, result.primaryReply().replyCode());
        assertFalse(Files.exists(rootPath.resolve("remove.txt")));
        assertTrue(Files.exists(rootPath.resolve("keep.txt")));
    }

    @Test
    void deleRejectsHostAbsolutePathOutsideRoot() throws Exception {
        Path outsideRoot = Files.createDirectory(rootPath.resolveSibling(rootPath.getFileName() + "-outside"));
        Path outsideFile = Files.writeString(outsideRoot.resolve("escape.txt"), "outside");
        ClientSession session = authenticatedSession();
        CommandDispatcher dispatcher = new CommandDispatcher();

        CommandResult result = dispatch(dispatcher, session, "DELE", outsideFile.toString());

        assertEquals(550, result.primaryReply().replyCode());
        assertTrue(Files.exists(outsideFile));
    }

    @Test
    void rnfrAndRntoRenameOnlyWithinTheIssuingSession() throws Exception {
        Files.writeString(rootPath.resolve("old.txt"), "payload");
        ClientSession first = authenticatedSession();
        ClientSession second = authenticatedSession();
        CommandDispatcher dispatcher = new CommandDispatcher();

        CommandResult rnfr = dispatch(dispatcher, first, "RNFR", "old.txt");
        CommandResult secondRnto = dispatch(dispatcher, second, "RNTO", "stolen.txt");
        CommandResult firstRnto = dispatch(dispatcher, first, "RNTO", "new.txt");

        assertEquals(350, rnfr.primaryReply().replyCode());
        assertEquals(503, secondRnto.primaryReply().replyCode());
        assertEquals(250, firstRnto.primaryReply().replyCode());
        assertFalse(Files.exists(rootPath.resolve("old.txt")));
        assertEquals("payload", Files.readString(rootPath.resolve("new.txt")));
        assertFalse(Files.exists(rootPath.resolve("stolen.txt")));
    }

    @Test
    void failedRntoClearsPendingRenameState() throws Exception {
        Files.writeString(rootPath.resolve("old.txt"), "payload");
        Path outsideRoot = Files.createDirectory(rootPath.resolveSibling(rootPath.getFileName() + "-outside"));
        ClientSession session = authenticatedSession();
        CommandDispatcher dispatcher = new CommandDispatcher();

        CommandResult rnfr = dispatch(dispatcher, session, "RNFR", "old.txt");
        CommandResult failedRnto = dispatch(dispatcher, session, "RNTO", outsideRoot.resolve("new.txt").toString());
        CommandResult retryRnto = dispatch(dispatcher, session, "RNTO", "new.txt");

        assertEquals(350, rnfr.primaryReply().replyCode());
        assertEquals(550, failedRnto.primaryReply().replyCode());
        assertEquals(503, retryRnto.primaryReply().replyCode());
        assertTrue(Files.exists(rootPath.resolve("old.txt")));
        assertFalse(Files.exists(rootPath.resolve("new.txt")));
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
