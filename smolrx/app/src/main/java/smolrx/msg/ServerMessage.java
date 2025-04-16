package smolrx.msg;

import java.io.Serializable;

public abstract sealed class ServerMessage implements Serializable permits Joblisting, Termination, BulkInputs, ProtocolConfig {
    // blank
}
