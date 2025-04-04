package smolrx.msg;

import java.util.Optional;

/**
 * Terminate session. Server closes it's channel.
 */
public final class Termination extends ServerMessage {
    // Cause for termination, if session was abruptly terminated.
    Optional<String> cause;

    public static Termination abrupt(String cause) {
        Termination t = new Termination();
        t.cause = Optional.of(cause);
        return t;
    }

    public static Termination normal() {
        Termination t = new Termination();
        t.cause = Optional.empty();
        return t;
    }

    public boolean isAbrupt() {
        return this.cause.isPresent();
    }

    public String getCause() {
        return cause.orElse("servlet closed channel");
    }
}
