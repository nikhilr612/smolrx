package smolrx.msg;

import java.io.Serializable;

/**
 * Aggregator interface for messages sent by the client.
 */
public abstract sealed class ClientMessage implements Serializable permits JobRequest, JarRequest, PushResult, InspectResult {
    // blank.
}
