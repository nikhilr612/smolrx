package smolrx;

import smolrx.msg.Termination;

/**
 * Generic error class.
 */
public class RXException extends Exception {
    public RXException(String message) {
        super(message);
    }

    public RXException(String message, Throwable t) {
        super(message, t);
    }

    public RXException(Termination t) {
        super("Abrupt termination of connection," + t.getCause());
    }

    public Termination intoTerminationMessage() {
        return Termination.abrupt(this.getMessage() + "\n\tcause:" + this.getCause());
    }
}
