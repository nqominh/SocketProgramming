package ftp.protocol.unit;

import ftp.protocol.control.ControlCodec;
import ftp.protocol.control.ControlMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ControlCodecTest {
    @Test
    void parsesCommandVerbAndArgument() {
        ControlMessage message = ControlCodec.parseCommand("USER alice\r\n");

        assertEquals(ControlMessage.Direction.COMMAND, message.direction());
        assertEquals("USER", message.verb());
        assertEquals("alice", message.argument());
    }

    @Test
    void formatsAndParsesReplyLine() {
        ControlMessage reply = ControlMessage.reply(230, "Logged in");

        String encoded = ControlCodec.formatReply(reply);
        ControlMessage decoded = ControlCodec.parseReply(encoded);

        assertEquals("230 Logged in\r\n", encoded);
        assertEquals(230, decoded.replyCode());
        assertEquals("Logged in", decoded.replyText());
    }

    @Test
    void rejectsMalformedLine() {
        assertThrows(IllegalArgumentException.class, () -> ControlCodec.parseCommand("\r\n"));
        assertThrows(IllegalArgumentException.class, () -> ControlCodec.parseReply("23 Logged in\r\n"));
    }
}
