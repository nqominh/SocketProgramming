package ftp.server.datachannel;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertThrows;

final class PassiveDataChannelTest {
    @Test
    void rejectsNonIpv4PassiveBindAddress() throws Exception {
        InetAddress ipv6Loopback = InetAddress.getByAddress("localhost", new byte[] {
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1
        });

        assertThrows(IllegalArgumentException.class, () -> new PassiveDataChannel(ipv6Loopback));
    }
}