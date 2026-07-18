package ftp.server.command;

import ftp.protocol.control.ControlMessage;
import ftp.server.ClientSession;
import ftp.server.FileSystemRoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

final class CommandDispatcherActiveModeCommandsTest {
    @TempDir
    Path rootPath;

    @Test
    void portStoresActiveUdpEndpointInSession() throws Exception {
        ClientSession session = authenticatedSession();
        CommandDispatcher dispatcher = new CommandDispatcher();

        CommandResult result = dispatch(dispatcher, session, "PORT", "127,0,0,1,195,80");

        // Why: active transfers need the server to remember the client-provided
        // UDP endpoint before the later STOR/RETR command selects its data path.
        assertEquals(200, result.primaryReply().replyCode());
        InetSocketAddress endpoint = activeDataEndpoint(session);
        assertEquals("127.0.0.1", endpoint.getAddress().getHostAddress());
        assertEquals(50000, endpoint.getPort());
    }

    @Test
    void invalidPortReplyDoesNotSetActiveEndpoint() throws Exception {
        ClientSession session = authenticatedSession();
        CommandDispatcher dispatcher = new CommandDispatcher();

        CommandResult result = dispatch(dispatcher, session, "PORT", "127,0,0,1,999,1");

        // Why: malformed active-mode endpoints must fail before becoming session
        // state, otherwise the next transfer would fail later and less clearly.
        assertEquals(501, result.primaryReply().replyCode());
        assertNull(activeDataEndpoint(session));
    }

    @Test
    void pasvClearsPreviouslyStoredActiveEndpoint() throws Exception {
        try (ClientSession session = authenticatedSession()) {
            CommandDispatcher dispatcher = new CommandDispatcher();
            assertEquals(200, dispatch(dispatcher, session, "PORT", "127,0,0,1,195,81").primaryReply().replyCode());

            CommandResult passive = dispatch(dispatcher, session, "PASV", "");

            // Why: a session should have one selected data-channel mode at a time;
            // PASV replaces stale active endpoint state with a passive listener.
            assertEquals(227, passive.primaryReply().replyCode());
            assertNull(activeDataEndpoint(session));
        }
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

    private static InetSocketAddress activeDataEndpoint(ClientSession session) {
        try {
            Method accessor = ClientSession.class.getMethod("activeDataEndpoint");
            return (InetSocketAddress) accessor.invoke(session);
        } catch (NoSuchMethodException exception) {
            fail("ClientSession must expose activeDataEndpoint() for active-mode state.");
        } catch (IllegalAccessException exception) {
            fail("ClientSession.activeDataEndpoint() must be public.");
        } catch (InvocationTargetException exception) {
            fail("ClientSession.activeDataEndpoint() threw: " + exception.getCause());
        }
        throw new AssertionError("unreachable");
    }
}
