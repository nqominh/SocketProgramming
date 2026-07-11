package ftp.server.command;

import ftp.protocol.control.ControlMessage;
import ftp.server.ClientSession;
import ftp.server.FileSystemRoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CommandDispatcherSessionCommandsTest {
    @TempDir
    Path rootPath;

    @Test
    void unknownCommandReturnsNotImplemented() throws Exception {
        ClientSession session = new ClientSession("s1", new FileSystemRoot(rootPath));
        CommandDispatcher dispatcher = new CommandDispatcher();

        CommandResult result = dispatcher.dispatch(session, ControlMessage.command("FEAT", ""));

        assertEquals(502, result.primaryReply().replyCode());
    }

    @Test
    void noopAndQuitReturnSessionReplyCodes() throws Exception {
        ClientSession session = new ClientSession("s1", new FileSystemRoot(rootPath));
        CommandDispatcher dispatcher = new CommandDispatcher();

        assertEquals(200, dispatcher.dispatch(session, ControlMessage.command("NOOP", "")).primaryReply().replyCode());
        CommandResult quit = dispatcher.dispatch(session, ControlMessage.command("QUIT", ""));
        assertEquals(221, quit.primaryReply().replyCode());
        assertTrue(quit.closeAfterReply());
    }
}
