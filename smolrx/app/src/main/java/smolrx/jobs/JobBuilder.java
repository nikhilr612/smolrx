package smolrx.jobs;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;

/**
 * Builder for creating and configuring instances of JobInfo.
 */
public class JobBuilder {
    private final long priority; // Mandatory field
    private final long programId; // Mandatory field
    private JobType type; // Mandatory field

    // Optional fields with default values.
    private Serializable jobData = null;
    private HashMap<String, String> properties = new HashMap<>();
    private int redundancyCount = 1;
    private HashSet<Long> prerequisiteJobs = new HashSet<>();
    private Optional<String> link = Optional.empty();

    public JobBuilder(long priority, long programId, JobType type) {
        if (priority < 0) {
            throw new IllegalArgumentException("Priority must be non-negative.");
        }
        if (programId <= 0) {
            throw new IllegalArgumentException("ProgramId must be positive.");
        }
        this.type = type;
        this.priority = priority;
        this.programId = programId;
    }

    public JobBuilder setJobData(Serializable jobData) {
        this.jobData = jobData;
        return this;
    }

    public JobBuilder setProperty(String key, String value) {
        this.properties.put(key, value);
        return this;
    }

    public JobBuilder addPrerequisiteJob(long jobId) {
        this.prerequisiteJobs.add(jobId);
        return this;
    }

    public JobBuilder setRedundancyCount(int redundancyCount) {
        this.redundancyCount = redundancyCount;
        return this;
    }

    public JobBuilder setLink(String link) {
        this.link = Optional.ofNullable(link);
        return this;
    }

    public JobInfo build() {
        JobInfo jobInfo = new JobInfo();
        jobInfo.type = this.type;
        jobInfo.priority = this.priority;
        jobInfo.jobData = this.jobData;
        jobInfo.programId = this.programId;
        jobInfo.properties = this.properties;
        jobInfo.redundancy_count = this.redundancyCount;
        jobInfo.prerequisite_jobs = this.prerequisiteJobs;
        jobInfo.link = this.link;
        return jobInfo;
    }
}