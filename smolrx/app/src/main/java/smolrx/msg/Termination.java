package smolrx.msg;

/**
 * Terminate session. Server closes it's channel.
 */
public final class Termination extends ServerMessage {

    private static final long serialVersionUID = 0xffeffeffefL;

    // Cause for termination, if session was abruptly terminated.
    String cause;

    public static Termination abrupt(String cause) {
        Termination t = new Termination();
        t.cause = cause;
        return t;
    }

    public static Termination normal() {
        Termination t = new Termination();
        t.cause = null;
        return t;
    }

    public boolean isAbrupt() {
        return this.cause != null;
    }

    public String getCause() {
        return cause == null ? "servlet closed channel" : cause;
    }
}
