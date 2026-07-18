package ftp.server.command;

import ftp.protocol.control.ControlMessage;
import ftp.server.ClientSession;
import ftp.server.FileSystemRoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CommandDispatcherTransferModeCommandsTest {
    @TempDir
    Path rootPath;

    @Test
    void typeISwitchesSessionTransferTypeToBinary() throws Exception {
        ClientSession session = authenticatedSession();
        CommandDispatcher dispatcher = new CommandDispatcher();

        CommandResult result = dispatch(dispatcher, session, "TYPE", "I");

        // Why: binary transfer is session state; later STOR/RETR must be able to
        // decide byte handling from the control command that came before it.
        assertEquals(200, result.primaryReply().replyCode());
        assertEquals(ClientSession.TransferType.BINARY, session.transferType());
    }

    @Test
    void typeARestoresAsciiTransferType() throws Exception {
        ClientSession session = authenticatedSession();
        CommandDispatcher dispatcher = new CommandDispatcher();
        dispatch(dispatcher, session, "TYPE", "I");

        CommandResult result = dispatch(dispatcher, session, "TYPE", "A");

        // Why: ASCII remains the default/basic mode, so users need an explicit
        // way to return from binary mode without opening a new session.
        assertEquals(200, result.primaryReply().replyCode());
        assertEquals(ClientSession.TransferType.ASCII, session.transferType());
    }

    @Test
    void unsupportedTypeFailsWithoutChangingTransferType() throws Exception {
        ClientSession session = authenticatedSession();
        CommandDispatcher dispatcher = new CommandDispatcher();
        dispatch(dispatcher, session, "TYPE", "I");

        CommandResult result = dispatch(dispatcher, session, "TYPE", "E");

        // Why: unsupported representation types must fail cleanly and must not
        // silently reset the session's last known transfer type.
        assertEquals(504, result.primaryReply().replyCode());
        assertEquals(ClientSession.TransferType.BINARY, session.transferType());
    }

    @Test
    void modeSAcceptsStreamModeAndUnsupportedModesFailWithoutChangingMode() throws Exception {
        ClientSession session = authenticatedSession();
        CommandDispatcher dispatcher = new CommandDispatcher();

        CommandResult stream = dispatch(dispatcher, session, "MODE", "S");
        CommandResult block = dispatch(dispatcher, session, "MODE", "B");
        CommandResult compressed = dispatch(dispatcher, session, "MODE", "C");

        // Why: this project only implements stream mode, but the session should
        // still record that mode explicitly for transfer code and demo logs.
        assertEquals(200, stream.primaryReply().replyCode());
        assertEquals(ClientSession.TransferMode.STREAM, session.transferMode());
        assertEquals(504, block.primaryReply().replyCode());
        assertEquals(ClientSession.TransferMode.STREAM, session.transferMode());
        assertEquals(504, compressed.primaryReply().replyCode());
        assertEquals(ClientSession.TransferMode.STREAM, session.transferMode());
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
