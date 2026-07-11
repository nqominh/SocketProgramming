package ftp.server.command;

import ftp.protocol.control.ControlMessage;

import java.util.List;
import java.util.Objects;

public record CommandResult(List<ControlMessage> replies, boolean closeAfterReply) {
    public CommandResult {
        Objects.requireNonNull(replies, "replies");
        if (replies.isEmpty()) {
            throw new IllegalArgumentException("At least one reply is required");
        }
        replies = List.copyOf(replies);
    }

    public static CommandResult single(ControlMessage reply) {
        return new CommandResult(List.of(reply), false);
    }

    public static CommandResult closing(ControlMessage reply) {
        return new CommandResult(List.of(reply), true);
    }

    public ControlMessage primaryReply() {
        return replies.get(0);
    }
}