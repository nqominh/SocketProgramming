package ftp.server.datachannel;

import ftp.server.ClientSession;

import java.io.IOException;

public final class DataChannelService {
    public PassiveDataChannel openPassive(ClientSession session) throws IOException {
        PassiveDataChannel dataChannel = new PassiveDataChannel(session.passiveBindAddress());
        session.replacePassiveDataChannel(dataChannel);
        return dataChannel;
    }
}