package ftp.server.integration;

import ftp.server.ClientSession;
import ftp.server.FileSystemRoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class FileSystemRootTest {
    @TempDir
    Path tempDir;

    @Test
    void resolvesNormalPathInsideRoot() throws IOException {
        Files.createDirectories(tempDir.resolve("docs"));
        Files.createFile(tempDir.resolve("docs/readme.txt"));
        FileSystemRoot root = new FileSystemRoot(tempDir);
        ClientSession session = new ClientSession("s1", root);

        Path resolved = session.resolvePath("docs/readme.txt");

        assertEquals(tempDir.resolve("docs/readme.txt").toRealPath().normalize(), resolved);
    }

    @Test
    void rejectsPathOutsideRoot() throws IOException {
        FileSystemRoot root = new FileSystemRoot(tempDir);
        ClientSession session = new ClientSession("s1", root);

        assertThrows(SecurityException.class, () -> session.resolvePath("..\\outside.txt"));
    }
}
