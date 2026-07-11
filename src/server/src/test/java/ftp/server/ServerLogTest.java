package ftp.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ServerLogTest {
    @Test
    void commandLineIncludesClientSessionCommandAndReply() {
        String line = ServerLog.commandLine("127.0.0.1", "s1", "USER alice", "331 Password required");

        assertEquals(
                "client=127.0.0.1 session=s1 command=\"USER alice\" reply=\"331 Password required\"",
                line);
    }

    @Test
    void progressLineIncludesClientSessionCommandAndProgress() {
        String line = ServerLog.progressLine("127.0.0.1", "s1", "STOR file.txt", "complete");

        assertEquals("client=127.0.0.1 session=s1 command=\"STOR file.txt\" progress=\"complete\"", line);
    }
}
