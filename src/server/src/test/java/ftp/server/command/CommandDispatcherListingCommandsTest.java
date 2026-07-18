package ftp.server.command;

import ftp.protocol.control.ControlMessage;
import ftp.server.ClientSession;
import ftp.server.FileSystemRoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Decision (T052A): listing replies stay on the control channel as a single-line
// reply (entries joined by ";") rather than opening a data channel. The wire
// format is one physical line per reply (see ControlConnectionHandler/
// ControlChannelClient), so embedding raw newlines in a multi-entry reply would
// desync the client's reader. Course scope does not require RFC-959 multiline
// (xxx-text) replies, so a single delimited line is the simplest correct choice.
final class CommandDispatcherListingCommandsTest {
    @TempDir
    Path rootPath;

    @Test
    void nlstReturnsSortedFileAndDirectoryNamesForCwd() throws Exception {
        Files.createDirectories(rootPath.resolve("drafts"));
        Files.writeString(rootPath.resolve("readme.txt"), "hello");
        ClientSession session = authenticatedSession();
        CommandDispatcher dispatcher = new CommandDispatcher();

        CommandResult result = dispatch(dispatcher, session, "NLST", "");

        assertEquals(226, result.primaryReply().replyCode());
        assertEquals("drafts;readme.txt", result.primaryReply().replyText());
    }

    @Test
    void nlstOnEmptyDirectoryReturnsEmptyListing() throws Exception {
        ClientSession session = authenticatedSession();
        CommandDispatcher dispatcher = new CommandDispatcher();

        CommandResult result = dispatch(dispatcher, session, "NLST", "");

        assertEquals(226, result.primaryReply().replyCode());
        assertEquals("", result.primaryReply().replyText());
    }

    @Test
    void listReturnsEntriesWithFileDirectoryDistinctionAndSize() throws Exception {
        Files.createDirectories(rootPath.resolve("drafts"));
        Files.writeString(rootPath.resolve("readme.txt"), "hello");
        ClientSession session = authenticatedSession();
        CommandDispatcher dispatcher = new CommandDispatcher();

        CommandResult result = dispatch(dispatcher, session, "LIST", "");

        assertEquals(226, result.primaryReply().replyCode());
        assertEquals("d drafts;- readme.txt 5", result.primaryReply().replyText());
    }

    @Test
    void nlstAndListRejectUnauthenticatedSession() throws Exception {
        ClientSession session = new ClientSession("s1", new FileSystemRoot(rootPath));
        CommandDispatcher dispatcher = new CommandDispatcher();

        assertEquals(530, dispatch(dispatcher, session, "NLST", "").primaryReply().replyCode());
        assertEquals(530, dispatch(dispatcher, session, "LIST", "").primaryReply().replyCode());
    }

    @Test
    void nlstOnMissingDirectoryReturnsFileUnavailable() throws Exception {
        ClientSession session = authenticatedSession();
        CommandDispatcher dispatcher = new CommandDispatcher();

        CommandResult result = dispatch(dispatcher, session, "NLST", "missing");

        assertEquals(550, result.primaryReply().replyCode());
        assertTrue(result.closeAfterReply() == false);
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
