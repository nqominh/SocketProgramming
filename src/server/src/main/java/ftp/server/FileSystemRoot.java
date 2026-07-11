package ftp.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class FileSystemRoot {
    private final Path root;

    public FileSystemRoot(Path root) throws IOException {
        Objects.requireNonNull(root, "root");
        Files.createDirectories(root);
        this.root = root.toRealPath().normalize();
    }

    public Path resolve(String currentDirectory, String inputPath) throws IOException {
        String current = normalizeVirtualDirectory(currentDirectory);
        String requested = inputPath == null || inputPath.isBlank() ? "." : inputPath.replace('\\', '/');
        Path base = root.resolve(current.substring(1)).normalize();
        Path resolved = requested.startsWith("/")
                ? root.resolve(requested.substring(1)).normalize()
                : base.resolve(requested).normalize();
        if (!resolved.startsWith(root)) {
            throw new SecurityException("Path escapes server root: " + inputPath);
        }
        return resolved;
    }

    public String toVirtualPath(Path path) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        if (Files.exists(normalized)) {
            normalized = normalized.toRealPath().normalize();
        }
        if (!normalized.startsWith(root)) {
            throw new SecurityException("Path escapes server root: " + path);
        }
        Path relative = root.relativize(normalized);
        String value = relative.toString().replace('\\', '/');
        return value.isEmpty() ? "/" : "/" + value;
    }

    public Path root() {
        return root;
    }

    private static String normalizeVirtualDirectory(String currentDirectory) {
        if (currentDirectory == null || currentDirectory.isBlank() || "/".equals(currentDirectory)) {
            return "/";
        }
        String normalized = currentDirectory.replace('\\', '/');
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }
}