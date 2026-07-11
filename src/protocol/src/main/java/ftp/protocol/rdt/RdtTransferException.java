package ftp.protocol.rdt;

import java.io.IOException;

public class RdtTransferException extends IOException {
    private static final long serialVersionUID = 1L;
    public RdtTransferException(String message) {
        super(message);
    }

    public RdtTransferException(String message, Throwable cause) {
        super(message, cause);
    }
}
