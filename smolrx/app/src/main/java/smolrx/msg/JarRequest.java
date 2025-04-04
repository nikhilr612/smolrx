package smolrx.msg;

/**
 * Application message to request a Jar associated with a jobId.
 */
public final class JarRequest extends ClientMessage {
    /**
     * A valid Job ID.
     */
    long jobId;

    /**
     * Must be a valid slogger or aggregator key.
     */
    String roleKey;

    public long getJobId() {
        return jobId;
    }

    public String getRoleKey() {
        return roleKey;
    }
}
