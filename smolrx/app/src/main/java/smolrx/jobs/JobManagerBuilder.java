package smolrx.jobs;

import java.util.HashMap;
import java.util.TreeMap;

/**
 * Builder for creating and configuring instances of JobManager.
 */
public class JobManagerBuilder {
    private final HashMap<Long, String> jarMap = new HashMap<>();
    private final TreeMap<Long, JobInfo> jobInfo = new TreeMap<>();
    private final HashMap<Long, JobMetadata> jobMetas = new HashMap<>();
    private final HashMap<String, JobType> keyMap = new HashMap<>();
    private boolean admitAnySlogger = false;
    private boolean forceRedundance = false;
    private int bulkReqLimit = 100;
    private int bulkPushLimit = 100;
    private int bulkInspLimit = 100;

    /**
     * Set the limit for bulk requests.
     * @param limit The maximum number of job inputs to request in bulk.
     * @return The current instance of JobManagerBuilder for method chaining.
     */
    public JobManagerBuilder setBulkReqLimit(int limit) {
        this.bulkReqLimit = limit;
        return this;
    }

    /**
     * Set the limit for bulk inspection.
     * @param limit The maximum number of job results to request in bulk.
     * @return The current instance of JobManagerBuilder for method chaining.
     */
    public JobManagerBuilder setBulkInspimit(int limit) {
        this.bulkInspLimit = limit;
        return this;
    }

    /**
     * Set the limit for bulk pushes. This limit should be set carefully, since the JobManager is locked for the duration of a Bulk Push operation.
     * @param limit The maximum number of job inputs to push in bulk.
     * @return The current instance of JobManagerBuilder for method chaining.
     */
    public JobManagerBuilder setBulkPushLimit(int limit) {
        this.bulkPushLimit = limit;
        return this;
    }

    /**
     * Add a job to the job manager.
     * @param jobId The ID of the job.
     * @param jobInfo The information about the job.
     * @return The current instance of JobManagerBuilder for method chaining.
     */
    public JobManagerBuilder addJob(long jobId, JobInfo jobInfo) {
        this.jobInfo.put(jobId, jobInfo);
        this.jobMetas.put(jobId, new JobMetadata());
        return this;
    }

    /**
     * Add a job to the job manager with metadata.
     * @param programId The ID of the program.
     * @param jarPath The path to the JAR file (local).
     * @return The current instance of JobManagerBuilder for method chaining.
     */
    public JobManagerBuilder addJar(long programId, String jarPath) {
        this.jarMap.put(programId, jarPath);
        return this;
    }

    /**
     * Add a role key to the job manager.
     * @param key The role key.
     * @param jobType The type of job associated with the key.
     * @return The current instance of JobManagerBuilder for method chaining.
     */
    public JobManagerBuilder withKey(String key, JobType jobType) {
        this.keyMap.put(key, jobType);
        return this;
    }

    /**
     * Job manager admits results from any slogger.
     * @return The current instance of JobManagerBuilder for method chaining.
     */
    public JobManagerBuilder allowAnySlogger() {
        this.admitAnySlogger = true;
        return this;
    }

    /**
     * Force redundancy check for all jobs.
     * @return The current instance of JobManagerBuilder for method chaining.
     */
    public JobManagerBuilder enforceRedundance() {
        this.forceRedundance = true;
        return this;
    }

    /**
     * Build and return a new instance of JobManager with the configured properties.
     * @return A new instance of JobManager.
     */
    public JobManager build() {
        JobManager manager = new JobManager();
        manager.jarMap = this.jarMap;
        manager.jobInfo = this.jobInfo;
        manager.jobMetas = this.jobMetas;
        manager.keyMap = this.keyMap;
        manager.bulkLimit = this.bulkReqLimit;
        manager.bulkPushLimit = this.bulkPushLimit;
        manager.admitAnySlogger = this.admitAnySlogger;
        manager.forceRedundance = this.forceRedundance;
        manager.bulkInspLimit = this.bulkInspLimit;
        return manager;
    }
}
