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

    @Test
    void cwdRejectsParentTraversalOutsideRootAndKeepsCurrentDirectory() throws Exception {
        Files.createDirectories(rootPath.resolve("projects"));
        ClientSession session = authenticatedSession();
        CommandDispatcher dispatcher = new CommandDispatcher();
        dispatch(dispatcher, session, "CWD", "projects");

        CommandResult result = dispatch(dispatcher, session, "CWD", "..\\..");

        // Why: rejecting the escape is not enough; the failed command must not
        // leave the session pointed at a partially-resolved or unsafe directory.
        assertEquals(550, result.primaryReply().replyCode());
        assertEquals("/projects", session.currentDirectory());
    }

    @Test
    void cwdRejectsHostAbsolutePathOutsideRootAndKeepsCurrentDirectory() throws Exception {
        Files.createDirectories(rootPath.resolve("projects"));
        Path outsideRoot = Files.createDirectory(rootPath.resolveSibling(rootPath.getFileName() + "-outside"));
        ClientSession session = authenticatedSession();
        CommandDispatcher dispatcher = new CommandDispatcher();
        dispatch(dispatcher, session, "CWD", "projects");

        CommandResult result = dispatch(dispatcher, session, "CWD", outsideRoot.toString());

        // Why: a client must not be able to smuggle the server host's absolute
        // filesystem paths into the virtual FTP namespace.
        assertEquals(550, result.primaryReply().replyCode());
        assertEquals("/projects", session.currentDirectory());
    }

    @Test
    void pwdReportsVirtualPathWithoutLeakingHostFilesystemPath() throws Exception {
        Files.createDirectories(rootPath.resolve("projects"));
        ClientSession session = authenticatedSession();
        CommandDispatcher dispatcher = new CommandDispatcher();
        dispatch(dispatcher, session, "CWD", "projects");

        CommandResult result = dispatch(dispatcher, session, "PWD", "");

        // Why: PWD is user-facing protocol state, so exposing the TempDir path
        // would reveal local machine layout and break client portability.
        assertEquals(257, result.primaryReply().replyCode());
        assertTrue(result.primaryReply().replyText().contains("/projects"));
        assertFalse(result.primaryReply().replyText().contains(rootPath.toString()));
    }

    @Test
    void mkdAndRmdRejectHostAbsolutePathsOutsideRoot() throws Exception {
        Path outsideRoot = Files.createDirectory(rootPath.resolveSibling(rootPath.getFileName() + "-outside"));
        Path outsideChild = outsideRoot.resolve("child");
        Path outsideExisting = Files.createDirectory(outsideRoot.resolve("existing"));
        ClientSession session = authenticatedSession();
        CommandDispatcher dispatcher = new CommandDispatcher();

        CommandResult mkd = dispatch(dispatcher, session, "MKD", outsideChild.toString());
        CommandResult rmd = dispatch(dispatcher, session, "RMD", outsideExisting.toString());

        // Why: filesystem-mutating commands must use the same root guard as CWD;
        // otherwise directory navigation is safe but writes/deletes can escape.
        assertEquals(550, mkd.primaryReply().replyCode());
        assertFalse(Files.exists(outsideChild));
        assertEquals(550, rmd.primaryReply().replyCode());
        assertTrue(Files.isDirectory(outsideExisting));
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
