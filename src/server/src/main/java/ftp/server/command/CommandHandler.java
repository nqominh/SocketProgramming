package ftp.server.command;

import ftp.protocol.control.ControlMessage;
import ftp.server.ClientSession;

@FunctionalInterface
public interface CommandHandler {
    CommandResult handle(ClientSession session, ControlMessage command);
}