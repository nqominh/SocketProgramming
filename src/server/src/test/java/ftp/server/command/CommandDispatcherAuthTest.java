package ftp.server.command;

import ftp.protocol.control.ControlMessage;
import ftp.server.ClientSession;
import ftp.server.FileSystemRoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CommandDispatcherAuthTest {
    @TempDir
    Path rootPath;

    @Test
    void userPassAuthenticatesAndTransferCommandsRequireLogin() throws Exception {
        ClientSession session = new ClientSession("s1", new FileSystemRoot(rootPath));
        CommandDispatcher dispatcher = new CommandDispatcher();

        assertEquals(530, dispatcher.dispatch(session, ControlMessage.command("STOR", "a.txt")).primaryReply().replyCode());
        assertEquals(530, dispatcher.dispatch(session, ControlMessage.command("RETR", "a.txt")).primaryReply().replyCode());
        assertEquals(331, dispatcher.dispatch(session, ControlMessage.command("USER", "alice")).primaryReply().replyCode());
        assertEquals(ClientSession.AuthState.USER_SENT, session.authState());
        assertEquals(230, dispatcher.dispatch(session, ControlMessage.command("PASS", "secret")).primaryReply().replyCode());
        assertEquals(ClientSession.AuthState.AUTHENTICATED, session.authState());
    }
}