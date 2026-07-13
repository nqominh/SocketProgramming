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

final class CommandDispatcherDirectoryCommandsTest {
    @TempDir
    Path rootPath;

    @Test
    void directoryCommandsMutateAndReportVirtualWorkingDirectory() throws Exception {
        Files.createDirectories(rootPath.resolve("projects"));
        ClientSession session = authenticatedSession();
        CommandDispatcher dispatcher = new CommandDispatcher();

        CommandResult initialPwd = dispatch(dispatcher, session, "PWD", "");

        // Why: clients need PWD to expose the server-root-relative virtual path,
        // not the host machine's temporary filesystem path.
        assertEquals(257, initialPwd.primaryReply().replyCode());
        assertTrue(initialPwd.primaryReply().replyText().contains("/"));

        CommandResult cwd = dispatch(dispatcher, session, "CWD", "projects");

        // Why: CWD must update session state so later relative commands resolve
        // from the selected directory instead of always using the server root.
        assertEquals(250, cwd.primaryReply().replyCode());
        assertEquals("/projects", session.currentDirectory());

        CommandResult mkd = dispatch(dispatcher, session, "MKD", "drafts");

        // Why: MKD is observable both through its reply and through the sandboxed
        // filesystem; the test proves it creates under the current virtual cwd.
        assertEquals(257, mkd.primaryReply().replyCode());
        assertTrue(Files.isDirectory(rootPath.resolve("projects/drafts")));

        CommandResult cdup = dispatch(dispatcher, session, "CDUP", "");

        // Why: CDUP is the protocol shortcut for moving to the parent directory,
        // and it must keep the session inside the virtual root.
        assertEquals(250, cdup.primaryReply().replyCode());
        assertEquals("/", session.currentDirectory());

        CommandResult rmd = dispatch(dispatcher, session, "RMD", "projects/drafts");

        // Why: RMD should remove only the addressed empty directory, giving the
        // client a deterministic way to clean up directories it created.
        assertEquals(250, rmd.primaryReply().replyCode());
        assertFalse(Files.exists(rootPath.resolve("projects/drafts")));
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
