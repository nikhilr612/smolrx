package smolrx.jobs;

/**
 * Enum for various job types.
 */
public enum JobType {
    /**
     * Node performs computation pertaining to a sub-problem.
     */
    SLOG,
    /**
     * Authorized node requests results of slogers.
     */
    COLLECT,
    /**
     * Authorized node can access logs. No jobs can exist of this type; enum variant reserved.
     */
    AUDIT,
}
