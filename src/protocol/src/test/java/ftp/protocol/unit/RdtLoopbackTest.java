package ftp.protocol.unit;

import ftp.protocol.rdt.PacketEndpoint;
import ftp.protocol.rdt.RdtConfig;
import ftp.protocol.rdt.RdtReceiver;
import ftp.protocol.rdt.RdtSender;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

final class RdtLoopbackTest {
    @Test
    void transfersOnePayloadInMemory() throws Exception {
        InMemoryLink link = InMemoryLink.clean();
        byte[] payload = "one in-memory payload transfer".getBytes(StandardCharsets.UTF_8);
        RdtConfig config = new RdtConfig(8, Duration.ofMillis(40), 8, 50);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<byte[]> received = executor.submit(() -> new RdtReceiver(config).receiveBytes(link.receiver()));

            new RdtSender(config).send(payload, link.sender());

            assertArrayEquals(payload, received.get(2, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    static final class InMemoryLink {
        private final LinkEndpoint sender;
        private final LinkEndpoint receiver;

        private InMemoryLink(FaultPlan faultPlan) {
            BlockingQueue<byte[]> senderInbound = new ArrayBlockingQueue<>(64);
            BlockingQueue<byte[]> receiverInbound = new ArrayBlockingQueue<>(64);
            sender = new LinkEndpoint(senderInbound, receiverInbound, faultPlan);
            receiver = new LinkEndpoint(receiverInbound, senderInbound, faultPlan);
        }

        static InMemoryLink clean() {
            return new InMemoryLink(FaultPlan.none());
        }

        static InMemoryLink unreliable() {
            return new InMemoryLink(FaultPlan.controlledFaults());
        }

        PacketEndpoint sender() {
            return sender;
        }

        PacketEndpoint receiver() {
            return receiver;
        }
    }

    static final class LinkEndpoint implements PacketEndpoint {
        private final BlockingQueue<byte[]> inbound;
        private final BlockingQueue<byte[]> peerInbound;
        private final FaultPlan faultPlan;

        LinkEndpoint(BlockingQueue<byte[]> inbound, BlockingQueue<byte[]> peerInbound, FaultPlan faultPlan) {
            this.inbound = inbound;
            this.peerInbound = peerInbound;
            this.faultPlan = faultPlan;
        }

        @Override
        public void send(byte[] datagram) throws IOException {
            try {
                faultPlan.deliver(datagram, peerInbound);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while sending datagram", exception);
            }
        }

        @Override
        public byte[] receive(Duration timeout) throws IOException, TimeoutException {
            try {
                byte[] datagram = inbound.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
                if (datagram == null) {
                    throw new TimeoutException("Timed out waiting for datagram");
                }
                return datagram;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while receiving datagram", exception);
            }
        }
    }

    static class FaultPlan {
        static FaultPlan none() {
            return new FaultPlan();
        }

        static FaultPlan controlledFaults() {
            return new ControlledFaultPlan();
        }

        void deliver(byte[] datagram, BlockingQueue<byte[]> peerInbound) throws InterruptedException {
            peerInbound.put(Arrays.copyOf(datagram, datagram.length));
        }
    }

    static final class ControlledFaultPlan extends FaultPlan {
        private boolean dropped;
        private boolean corrupted;
        private boolean duplicated;
        private byte[] delayedDuplicate;

        @Override
        void deliver(byte[] datagram, BlockingQueue<byte[]> peerInbound) throws InterruptedException {
            if (delayedDuplicate != null) {
                peerInbound.put(delayedDuplicate);
                delayedDuplicate = null;
            }

            if (!dropped) {
                dropped = true;
                return;
            }

            if (!corrupted) {
                corrupted = true;
                byte[] corruptedDatagram = Arrays.copyOf(datagram, datagram.length);
                corruptedDatagram[corruptedDatagram.length - 1] ^= 0x01;
                peerInbound.put(corruptedDatagram);
                return;
            }

            byte[] copy = Arrays.copyOf(datagram, datagram.length);
            peerInbound.put(copy);
            if (!duplicated) {
                duplicated = true;
                delayedDuplicate = Arrays.copyOf(datagram, datagram.length);
            }
        }
    }
}
