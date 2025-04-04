package smolrx.msg;

public final class PushResult extends ClientMessage {
    /**
     * An identifier which specifies the role of the client.
     */
    String roleKey;

    /**
     * ID of the JOB whose result has been computed.
     */
    long job_id;

    /**
     * The result computed.
     */
    Object resultObject;

    public String getRoleKey() {
        return roleKey;
    }

    public long getJobId() {
        return job_id;
    }

    public Object getResultObject() {
        return resultObject;
    }
}
