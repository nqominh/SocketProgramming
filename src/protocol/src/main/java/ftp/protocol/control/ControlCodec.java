package ftp.protocol.control;

import java.util.Objects;
import java.util.regex.Pattern;

public final class ControlCodec {
    private static final Pattern COMMAND_VERB = Pattern.compile("[A-Za-z]{3,4}");
    private static final Pattern REPLY_LINE = Pattern.compile("\\d{3}( .*)?");

    private ControlCodec() {
    }

    public static ControlMessage parseCommand(String line) {
        String trimmed = stripLineEnding(line);
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException("Command line is empty");
        }
        int split = firstWhitespace(trimmed);
        String verb = split < 0 ? trimmed : trimmed.substring(0, split);
        if (!COMMAND_VERB.matcher(verb).matches()) {
            throw new IllegalArgumentException("Malformed command verb: " + verb);
        }
        String argument = split < 0 ? "" : trimmed.substring(split).trim();
        return ControlMessage.command(verb, argument);
    }

    public static String formatCommand(ControlMessage message) {
        requireDirection(message, ControlMessage.Direction.COMMAND);
        return message.argument().isBlank()
                ? message.verb() + "\r\n"
                : message.verb() + " " + message.argument() + "\r\n";
    }

    public static ControlMessage parseReply(String line) {
        String trimmed = stripLineEnding(line);
        if (!REPLY_LINE.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Malformed reply line: " + trimmed);
        }
        int code = Integer.parseInt(trimmed.substring(0, 3));
        String text = trimmed.length() == 3 ? "" : trimmed.substring(4);
        return ControlMessage.reply(code, text);
    }

    public static String formatReply(ControlMessage message) {
        requireDirection(message, ControlMessage.Direction.REPLY);
        return message.replyText().isBlank()
                ? "%03d\r\n".formatted(message.replyCode())
                : "%03d %s\r\n".formatted(message.replyCode(), message.replyText());
    }

    private static void requireDirection(ControlMessage message, ControlMessage.Direction direction) {
        Objects.requireNonNull(message, "message");
        if (message.direction() != direction) {
            throw new IllegalArgumentException("Expected " + direction + " message");
        }
    }

    private static String stripLineEnding(String line) {
        Objects.requireNonNull(line, "line");
        if (line.endsWith("\r\n")) {
            return line.substring(0, line.length() - 2);
        }
        if (line.endsWith("\n")) {
            return line.substring(0, line.length() - 1);
        }
        return line;
    }

    private static int firstWhitespace(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                return index;
            }
        }
        return -1;
    }
}
