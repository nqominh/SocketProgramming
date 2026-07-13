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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CliBasicScriptTest {
    @TempDir
    Path root;

    @Test
    void scriptedBasicCommandsUploadDownloadAndQuit() throws Exception {
        byte[] payload = "cli upload payload\n".getBytes(StandardCharsets.US_ASCII);
        Path localSource = root.resolve("local-source.txt");
        Path localDownload = root.resolve("local-download.txt");
        Files.write(localSource, payload);
        try (ControlChannelServer server = ControlChannelServer.bind(InetAddress.getLoopbackAddress(), 0, root)) {
            server.start();
            String script = """
                    user alice
                    pass secret
                    stor %s remote.txt
                    retr remote.txt %s
                    quit
                    """.formatted(localSource, localDownload);
            ByteArrayInputStream input = new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8));
            ByteArrayOutputStream output = new ByteArrayOutputStream();

            int exitCode = Cli.run(new String[] {
                    "--host", InetAddress.getLoopbackAddress().getHostAddress(),
                    "--port", Integer.toString(server.port())
            }, input, new PrintStream(output, true, StandardCharsets.UTF_8));

            assertEquals(0, exitCode, output.toString(StandardCharsets.UTF_8));
            assertArrayEquals(payload, Files.readAllBytes(root.resolve("remote.txt")));
            assertArrayEquals(payload, Files.readAllBytes(localDownload));
        }
    }

    @Test
    void retrieveMissingFilePrintsErrorReplyInsteadOfHangingOrCrashing() throws Exception {
        Path localDownload = root.resolve("local-download.txt");
        try (ControlChannelServer server = ControlChannelServer.bind(InetAddress.getLoopbackAddress(), 0, root)) {
            server.start();
            String script = """
                    user alice
                    pass secret
                    retr does-not-exist.txt %s
                    quit
                    """.formatted(localDownload);
            ByteArrayInputStream input = new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8));
            ByteArrayOutputStream output = new ByteArrayOutputStream();

            int exitCode = Cli.run(new String[] {
                    "--host", InetAddress.getLoopbackAddress().getHostAddress(),
                    "--port", Integer.toString(server.port())
            }, input, new PrintStream(output, true, StandardCharsets.UTF_8));

            String printed = output.toString(StandardCharsets.UTF_8);
            assertEquals(0, exitCode, printed);
            assertTrue(printed.contains("550"),
                    "CLI should surface the server's 550 missing-file reply instead of hanging on the data channel: " + printed);
        }
    }
}
