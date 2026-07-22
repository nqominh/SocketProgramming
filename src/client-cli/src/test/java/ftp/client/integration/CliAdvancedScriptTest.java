package ftp.client.integration;

import ftp.client.Cli;
import ftp.server.ControlChannelServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CliAdvancedScriptTest {
    @TempDir
    Path root;

    @Test
    void scriptedDirectoryCommandsMapToControlCommands() throws Exception {
        String script = """
                user alice
                pass secret
                pwd
                mkdir docs
                cd docs
                pwd
                cdup
                rmdir docs
                quit
                """;

        String printed = runScript(script);

        assertTrue(printed.contains("257 \"/\""), printed);
        assertTrue(printed.contains("257 \"docs\""), printed);
        assertFalse(Files.exists(root.resolve("docs")));
    }

    @Test
    void scriptedListingAndMetadataCommandsMapToControlCommands() throws Exception {
        Files.writeString(root.resolve("info.txt"), "abc");
        String script = """
                user alice
                pass secret
                ls
                nlst
                stat info.txt
                size info.txt
                mdtm info.txt
                quit
                """;

        String printed = runScript(script);

        assertTrue(printed.contains("info.txt"), printed);
        assertTrue(printed.contains("213 3"), printed);
    }

    @Test
    void scriptedTransferModeCommandsMapToControlCommands() throws Exception {
        String script = """
                user alice
                pass secret
                type I
                mode S
                pasv
                port 127,0,0,1,195,88
                quit
                """;

        String printed = runScript(script);

        assertTrue(printed.contains("200 OK"), printed);
        assertTrue(printed.contains("227 Entering Passive Mode"), printed);
    }

    @Test
    void scriptedDeleteAndRenameCommandsMapToControlCommands() throws Exception {
        Files.writeString(root.resolve("remove.txt"), "remove");
        Files.writeString(root.resolve("old.txt"), "payload");
        String script = """
                user alice
                pass secret
                dele remove.txt
                rename old.txt new.txt
                quit
                """;

        String printed = runScript(script);

        assertTrue(printed.contains("350"), printed);
        assertFalse(Files.exists(root.resolve("remove.txt")));
        assertFalse(Files.exists(root.resolve("old.txt")));
        assertEquals("payload", Files.readString(root.resolve("new.txt")));
    }

    private String runScript(String script) throws Exception {
        try (ControlChannelServer server = ControlChannelServer.bind(InetAddress.getLoopbackAddress(), 0, root)) {
            server.start();
            ByteArrayInputStream input = new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8));
            ByteArrayOutputStream output = new ByteArrayOutputStream();

            int exitCode = Cli.run(new String[] {
                    "--host", InetAddress.getLoopbackAddress().getHostAddress(),
                    "--port", Integer.toString(server.port())
            }, input, new PrintStream(output, true, StandardCharsets.UTF_8));

            String printed = output.toString(StandardCharsets.UTF_8);
            assertEquals(0, exitCode, printed);
            return printed;
        }
    }
}
