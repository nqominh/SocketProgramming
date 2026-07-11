package ftp.client;

import ftp.protocol.control.ControlMessage;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;

public final class Cli {
    private Cli() {
    }

    public static void main(String[] args) throws Exception {
        int exitCode = run(args, System.in, System.out);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    public static int run(String[] args, InputStream input, PrintStream output) throws Exception {
        String host = option(args, "--host", "127.0.0.1");
        int port = Integer.parseInt(option(args, "--port", "2121"));
        try (ControlChannelClient client = ControlChannelClient.connect(host, port, Duration.ofSeconds(3));
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            printReply(output, client.readReply());
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.trim().split("\\s+");
                String command = parts[0].toLowerCase(Locale.ROOT);
                switch (command) {
                    case "user" -> sendAndPrint(client, output, "USER", argument(parts, 1));
                    case "pass" -> sendAndPrint(client, output, "PASS", argument(parts, 1));
                    case "noop" -> sendAndPrint(client, output, "NOOP", "");
                    case "stor" -> store(client, output, parts);
                    case "retr" -> retrieve(client, output, parts);
                    case "quit" -> {
                        sendAndPrint(client, output, "QUIT", "");
                        return 0;
                    }
                    default -> {
                        output.printf("Unknown local command: %s%n", parts[0]);
                        return 1;
                    }
                }
            }
            return 0;
        }
    }

    private static void store(ControlChannelClient client, PrintStream output, String[] parts) throws Exception {
        if (parts.length != 3) {
            throw new IllegalArgumentException("stor requires local and remote paths");
        }
        DataChannelClient data = openPassive(client, output);
        client.sendCommand("STOR", parts[2]);
        data.upload(Files.readAllBytes(Path.of(parts[1])));
        printReply(output, client.readReply());
        printReply(output, client.readReply());
    }

    private static void retrieve(ControlChannelClient client, PrintStream output, String[] parts) throws Exception {
        if (parts.length != 3) {
            throw new IllegalArgumentException("retr requires remote and local paths");
        }
        DataChannelClient data = openPassive(client, output);
        client.sendCommand("RETR", parts[1]);
        byte[] payload = data.download();
        printReply(output, client.readReply());
        printReply(output, client.readReply());
        Files.write(Path.of(parts[2]), payload);
    }

    private static DataChannelClient openPassive(ControlChannelClient client, PrintStream output) throws Exception {
        client.sendCommand("PASV", "");
        ControlMessage passiveReply = client.readReply();
        printReply(output, passiveReply);
        return DataChannelClient.fromPassiveReply(passiveReply, Duration.ofSeconds(3));
    }

    private static void sendAndPrint(ControlChannelClient client, PrintStream output, String verb, String argument)
            throws Exception {
        client.sendCommand(verb, argument);
        printReply(output, client.readReply());
    }

    private static String argument(String[] parts, int index) {
        if (parts.length <= index) {
            return "";
        }
        return parts[index];
    }

    private static void printReply(PrintStream output, ControlMessage reply) {
        output.printf("%03d %s%n", reply.replyCode(), reply.replyText());
    }

    private static String option(String[] args, String name, String defaultValue) {
        for (int index = 0; index + 1 < args.length; index++) {
            if (name.equals(args[index])) {
                return args[index + 1];
            }
        }
        return defaultValue;
    }
}