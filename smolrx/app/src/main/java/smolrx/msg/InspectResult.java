package smolrx.msg;

public final class InspectResult extends ClientMessage {
    /**
     * The job ID whose result is requested.
     */
    long forJobId;

    /**
     * The Aggregator JobID which requires this.
     */
    long parentJobId;
    
    /**
     * Key that determines if the client can play the role of aggregator.
     */
    String roleKey;

    /**
     * Limit the result objects sent over channel to this number, in case of redundancies.
     */
    int limit;
    
    public int getLimit() {
        return limit;
    }

    public long getJobId() {
        return forJobId;
    }

    public String getRoleKey() {
        return this.roleKey;
    }

    public long getParentJobId() {
        return parentJobId;
    }
}
