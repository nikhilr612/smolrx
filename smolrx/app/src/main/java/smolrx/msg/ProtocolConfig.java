package smolrx.msg;

/**
 * Data-class for server-side configuration details that should be known to the client.
 */
public final class ProtocolConfig extends ServerMessage {
    private static final long serialVersionUID = 9876543231L;

    int bulkRequestLimit;
    int bulkPushLimit;

    public int getBulkPushLimit() {
        return bulkPushLimit;
    }

    public int getBulkRequestLimit() {
        return bulkRequestLimit;
    }
    
    public ProtocolConfig(int bulkRequestLimit, int bulkPushLimit) {
        this.bulkRequestLimit = bulkRequestLimit;
        this.bulkPushLimit = bulkPushLimit;
    }

    @Override
    public String toString() {
        return "ProtocolConfig{" +
                "bulkRequestLimit=" + bulkRequestLimit +
                ", bulkPushLimit=" + bulkPushLimit +
                '}';
    }
    
}
