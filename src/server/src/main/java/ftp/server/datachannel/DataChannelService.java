package ftp.server.datachannel;

import ftp.protocol.rdt.PacketChannel;
import ftp.server.ClientSession;

import java.io.IOException;
import java.net.InetSocketAddress;

public final class DataChannelService {
    public PassiveDataChannel openPassive(ClientSession session) throws IOException {
        PassiveDataChannel dataChannel = new PassiveDataChannel(session.passiveBindAddress());
        session.replacePassiveDataChannel(dataChannel);
        return dataChannel;
    }

    public PacketChannel openSelectedDataChannel(ClientSession session) throws IOException {
        PassiveDataChannel passiveDataChannel = session.takePassiveDataChannel();
        if (passiveDataChannel != null) {
            return passiveDataChannel;
        }
        InetSocketAddress activeEndpoint = session.takeActiveDataEndpoint();
        if (activeEndpoint != null) {
            return new ActiveDataChannel(session.passiveBindAddress(), activeEndpoint);
        }
        return null;
    }
}
