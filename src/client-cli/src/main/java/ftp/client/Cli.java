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
    private static final Duration TRANSFER_JOIN_TIMEOUT = Duration.ofSeconds(2);

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
                String[] commandTokens = line.trim().split("\\s+");
                String command = commandTokens[0].toLowerCase(Locale.ROOT);
                switch (command) {
                    case "user" -> sendAndPrint(client, output, "USER", argument(commandTokens, 1));
                    case "pass" -> sendAndPrint(client, output, "PASS", argument(commandTokens, 1));
                    case "noop" -> sendAndPrint(client, output, "NOOP", "");
                    case "pwd" -> sendAndPrint(client, output, "PWD", "");
                    case "cd" -> sendAndPrint(client, output, "CWD", argument(commandTokens, 1));
                    case "cdup" -> sendAndPrint(client, output, "CDUP", "");
                    case "mkdir" -> sendAndPrint(client, output, "MKD", argument(commandTokens, 1));
                    case "rmdir" -> sendAndPrint(client, output, "RMD", argument(commandTokens, 1));
                    case "ls" -> sendAndPrint(client, output, "LIST", argument(commandTokens, 1));
                    case "nlst" -> sendAndPrint(client, output, "NLST", argument(commandTokens, 1));
                    case "stat" -> sendAndPrint(client, output, "STAT", argument(commandTokens, 1));
                    case "size" -> sendAndPrint(client, output, "SIZE", argument(commandTokens, 1));
                    case "mdtm" -> sendAndPrint(client, output, "MDTM", argument(commandTokens, 1));
                    case "type" -> sendAndPrint(client, output, "TYPE", argument(commandTokens, 1));
                    case "mode" -> sendAndPrint(client, output, "MODE", argument(commandTokens, 1));
                    case "pasv" -> sendAndPrint(client, output, "PASV", "");
                    case "port" -> sendAndPrint(client, output, "PORT", argument(commandTokens, 1));
                    case "dele" -> sendAndPrint(client, output, "DELE", argument(commandTokens, 1));
                    case "rename" -> rename(client, output, commandTokens);
                    case "stor" -> store(client, output, commandTokens);
                    case "retr" -> retrieve(client, output, commandTokens);
                    case "quit" -> {
                        sendAndPrint(client, output, "QUIT", "");
                        return 0;
                    }
                    default -> {
                        output.printf("Unknown local command: %s%n", commandTokens[0]);
                        return 1;
                    }
                }
            }
            return 0;
        }
    }

    private static void store(ControlChannelClient client, PrintStream output, String[] commandTokens) throws Exception {
        if (commandTokens.length != 3) {
            throw new IllegalArgumentException("stor requires local and remote paths");
        }
        DataChannelClient data = openPassive(client, output);
        client.sendCommand("STOR", commandTokens[2]);
        Path localSource = Path.of(commandTokens[1]);
        runDataChannelTransfer(client, output, data, () -> {
            data.upload(Files.readAllBytes(localSource));
            return null;
        });
    }

    private static void rename(ControlChannelClient client, PrintStream output, String[] commandTokens) throws Exception {
        if (commandTokens.length != 3) {
            throw new IllegalArgumentException("rename requires old and new remote paths");
        }
        client.sendCommand("RNFR", commandTokens[1]);
        ControlMessage rnfr = client.readReply();
        printReply(output, rnfr);
        if (rnfr.replyCode() == 350) {
            client.sendCommand("RNTO", commandTokens[2]);
            printReply(output, client.readReply());
        }
    }

    private static void retrieve(ControlChannelClient client, PrintStream output, String[] parts) throws Exception {
        if (parts.length != 3) {
            throw new IllegalArgumentException("retr requires remote and local paths");
        }
        DataChannelClient data = openPassive(client, output);
        client.sendCommand("RETR", parts[1]);
        TransferOutcome outcome = runDataChannelTransfer(client, output, data, data::download);
        if (outcome.success) {
            Files.write(Path.of(parts[2]), outcome.downloaded);
        }
    }

    /**
     * The server runs STOR/RETR synchronously inside its command dispatch and only
     * writes control replies afterward: on success it sends two lines (150 then 226)
     * once the whole transfer is done; on early failure (auth, missing file, no data
     * connection) it sends exactly one line and never touches the data channel. So the
     * data-channel operation must already be running before we know which shape to
     * expect, and reply-reading must stay in step with the reply count that actually
     * shows up rather than a fixed count of two.
     */
    private static TransferOutcome runDataChannelTransfer(
            ControlChannelClient client, 
            PrintStream output, 
            DataChannelClient data, 
            DataChannelTask task)
            throws Exception {
        TransferOutcome outcome = new TransferOutcome();
        Thread worker = new Thread(() -> {
            try {
                outcome.downloaded = task.run();
                outcome.success = true;
            } catch (Exception exception) {
                outcome.failure = exception;
            }
        });
        worker.setDaemon(true);
        worker.start();

        ControlMessage firstReply = client.readReply();
        printReply(output, firstReply);

        if (firstReply.replyCode() == 150) {
            printReply(output, client.readReply());
            worker.join(TRANSFER_JOIN_TIMEOUT.toMillis());
            if (worker.isAlive()) {
                output.println("Data transfer did not complete in time.");
                outcome.success = false;
            } else if (outcome.failure != null) {
                output.printf("Data transfer failed: %s%n", outcome.failure.getMessage());
            }
        } else {
            // Single-reply failure: the server never touched the data channel, so don't
            // wait out the RDT timeout for data that is never coming.
            data.close();
            worker.join(TRANSFER_JOIN_TIMEOUT.toMillis());
        }
        return outcome;
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

    @FunctionalInterface
    private interface DataChannelTask {
        byte[] run() throws Exception;
    }

    private static final class TransferOutcome {
        private volatile boolean success;
        private volatile byte[] downloaded;
        private volatile Exception failure;
    }
}
