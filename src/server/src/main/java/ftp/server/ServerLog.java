package ftp.server;

public final class ServerLog {
    private ServerLog() {
    }

    public static String commandLine(String clientIp, String sessionId, String command, String reply) {
        return "client=%s session=%s command=\"%s\" reply=\"%s\"".formatted(
                clientIp,
                sessionId,
                command,
                reply);
    }

    public static String progressLine(String clientIp, String sessionId, String command, String progress) {
        return "client=%s session=%s command=\"%s\" progress=\"%s\"".formatted(
                clientIp,
                sessionId,
                command,
                progress);
    }
}
