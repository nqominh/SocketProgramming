package ftp.protocol.unit;

import ftp.protocol.rdt.RdtConfig;
import ftp.protocol.rdt.RdtReceiver;
import ftp.protocol.rdt.RdtSender;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

final class RdtFaultInjectionTest {
    @Test
    void transferCompletesUnderControlledPacketFaults() throws Exception {
        RdtLoopbackTest.InMemoryLink link = RdtLoopbackTest.InMemoryLink.unreliable();
        byte[] payload = "loss duplicate corruption reorder proof".repeat(20).getBytes(StandardCharsets.UTF_8);
        RdtConfig config = new RdtConfig(23, Duration.ofMillis(25), 12, 200);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<byte[]> received = executor.submit(() -> new RdtReceiver(config).receiveBytes(link.receiver()));

            new RdtSender(config).send(payload, link.sender());

            assertArrayEquals(payload, received.get(4, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }
}
