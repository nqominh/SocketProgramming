package ftp.server.command;

import ftp.protocol.control.ControlMessage;

public final class ReplyFactory {
    private ReplyFactory() {
    }

    public static ControlMessage ready() {
        return ControlMessage.reply(220, "Hybrid FTP ready");
    }

    public static ControlMessage ok() {
        return ControlMessage.reply(200, "OK");
    }

    public static ControlMessage goodbye() {
        return ControlMessage.reply(221, "Goodbye");
    }

    public static ControlMessage syntaxError() {
        return ControlMessage.reply(500, "Syntax error");
    }

    public static ControlMessage commandNotImplemented() {
        return ControlMessage.reply(502, "Command not implemented");
    }

    public static ControlMessage usernameRequired() {
        return ControlMessage.reply(501, "Username required");
    }

    public static ControlMessage passwordRequired() {
        return ControlMessage.reply(331, "Password required");
    }

    public static ControlMessage loggedIn() {
        return ControlMessage.reply(230, "Logged in");
    }

    public static ControlMessage loginIncorrect() {
        return ControlMessage.reply(530, "Login incorrect");
    }

    public static ControlMessage notLoggedIn() {
        return ControlMessage.reply(530, "Not logged in");
    }

    public static ControlMessage passiveMode(String tuple) {
        return ControlMessage.reply(227, "Entering Passive Mode (" + tuple + ")");
    }

    public static ControlMessage cannotOpenDataConnection() {
        return ControlMessage.reply(425, "Can't open data connection");
    }

    public static ControlMessage noDataConnection() {
        return ControlMessage.reply(425, "No data connection");
    }
    public static ControlMessage openingDataConnection() {
        return ControlMessage.reply(150, "Opening data connection");
    }

    public static ControlMessage transferComplete() {
        return ControlMessage.reply(226, "Transfer complete");
    }

    public static ControlMessage fileUnavailable() {
        return ControlMessage.reply(550, "File unavailable");
    }

    public static ControlMessage localError() {
        return ControlMessage.reply(451, "Local error");
    }
}
