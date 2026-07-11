package ftp.protocol.control;

import java.util.Objects;

public record ControlMessage(
        Direction direction,
        String verb,
        String argument,
        int replyCode,
        String replyText) {
    public enum Direction {
        COMMAND,
        REPLY
    }

    public ControlMessage {
        Objects.requireNonNull(direction, "direction");
        if (direction == Direction.COMMAND) {
            if (verb == null || verb.isBlank()) {
                throw new IllegalArgumentException("Command verb is required");
            }
            verb = verb.toUpperCase();
            replyText = "";
            replyCode = 0;
        } else {
            if (replyCode < 100 || replyCode > 999) {
                throw new IllegalArgumentException("Reply code must be exactly three digits");
            }
            verb = "";
            argument = "";
            replyText = Objects.requireNonNull(replyText, "replyText");
        }
        argument = argument == null ? "" : argument;
    }

    public static ControlMessage command(String verb, String argument) {
        return new ControlMessage(Direction.COMMAND, verb, argument, 0, "");
    }

    public static ControlMessage reply(int replyCode, String replyText) {
        return new ControlMessage(Direction.REPLY, "", "", replyCode, replyText);
    }
}
