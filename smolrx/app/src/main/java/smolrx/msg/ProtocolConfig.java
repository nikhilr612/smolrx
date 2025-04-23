package smolrx.msg;

/**
 * Data-class for server-side configuration details that should be known to the client.
 */
public final class ProtocolConfig extends ServerMessage {
    private static final long serialVersionUID = 9876543231L;

    int bulkRequestLimit;
    int bulkPushLimit;
    int bulkInspLimit;

    public int getBulkPushLimit() {
        return bulkPushLimit;
    }

    public int getBulkRequestLimit() {
        return bulkRequestLimit;
    }

    public int getBulkInspectLimit() {
        return bulkInspLimit;
    }
    
    public ProtocolConfig(int bulkRequestLimit, int bulkPushLimit, int bulkInspLimit) {
        this.bulkRequestLimit = bulkRequestLimit;
        this.bulkPushLimit = bulkPushLimit;
        this.bulkInspLimit = bulkInspLimit; // Default to the same as bulkRequestLimit
    }

    @Override
    public String toString() {
        return "ProtocolConfig{" +
                "bulkRequestLimit=" + bulkRequestLimit +
                ", bulkPushLimit=" + bulkPushLimit +
                '}';
    }
    
}
