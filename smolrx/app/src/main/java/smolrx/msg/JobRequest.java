package smolrx.msg;

import java.util.Optional;

import smolrx.jobs.JobType;

/**
 * Application message to request a list of jobs up for grabs.
 */
public final class JobRequest extends ClientMessage {
    /**
     * Minimum priority of jobs to be listed.
     */
    long min_priority;

    /**
     * Filter jobs by this type.
     */
    JobType type;

    /**
     * List at most this number of jobs.
     */
    int limit;

    /**
     * If auditing, specify an audit key.
     */
    Optional<String> roleKey;

    public int getLimit() {
        return limit;
    }

    public long getMinPriority() {
        return min_priority;
    }

    public JobType getType() {
        return type;
    }

    public Optional<String> getRoleKey() {
        return roleKey;
    }
}
