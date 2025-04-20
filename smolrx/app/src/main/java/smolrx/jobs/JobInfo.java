package smolrx.jobs;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;

/**
 * Data-class that maintains instance-specific information about a program to be executed.
 */
public class JobInfo implements Serializable, Comparable<JobInfo> {

    private static final long serialVersionUID = 0xb00bcafeL;

    /**
     * The type of job.
     */
    JobType type;

    /**
     * Priority assigned to this job. Not traditional priority, since there is no scheduling model.
     */
    long priority;

    /**
     * Input data for each job.
     */
    Serializable jobData;

    /**
     * Identifier for the JAR / program to execute.
     */
    long programId;
    
    /**
     * Miscellaneous properties associated with the job.
     */
    HashMap<String, String> properties;

    /**
     * The number of submissions to allow before de-listing the job.
     */
    int redundancy_count;

    /**
     * Jobs that must be completed before allowing admission of any results of this job.
     */
    HashSet<Long> prerequisite_jobs;

    /**
     * Indicates that the pre-requisite jobs need not all be completed before this job can be executed.
     * This is primarily intended for allowing reducers to run in parallel (or concurrently) with mappers.
     * This should only be used when Client-side scheduling allows for interruptible jobs.
     */
    boolean relaxed;
    
    /**
     * A URI/Link pointing to the executable JAR required for this job.
     * If link is non-null, then the Client must fetch the JAR specified by the link.
     * The client must treat the file sent in response to JarRequest as file metadata, and verify it with the fetched file.
     */
    String link;

    JobInfo() {}

    /**
     * Clone and remove unnecessary information.
     * @return Cloned object.
     */
    protected JobInfo maskedClone() {
        var jinfo = new JobInfo();
    
        jinfo.redundancy_count = 0;
        jinfo.jobData = null;

        jinfo.programId = this.programId; // Keep programId for client cache.
        jinfo.link = this.link; // Keep link for client cache.

        jinfo.prerequisite_jobs = this.prerequisite_jobs;
        jinfo.type = this.type;
        jinfo.relaxed = this.relaxed;
        jinfo.properties = this.properties;
        jinfo.priority = this.priority;
        return jinfo;
    }

    @Override
    public int compareTo(JobInfo o) {
        if (this.priority < o.priority) {
            return -1;
        } else if (this.priority > o.priority) {
            return 1;
        }
        return 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        JobInfo jobInfo = (JobInfo) obj;
        return programId == jobInfo.programId 
            && type == jobInfo.type 
            && priority == jobInfo.priority
            && redundancy_count == jobInfo.redundancy_count
            && relaxed == jobInfo.relaxed
            && properties.equals(jobInfo.properties)
            && prerequisite_jobs.equals(jobInfo.prerequisite_jobs)
            && (link == null ? jobInfo.link == null : link.equals(jobInfo.link))
            && (jobData == null ? jobInfo.jobData == null : jobData.equals(jobInfo.jobData));
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            programId, type, priority, 
            jobData, redundancy_count, 
            properties, prerequisite_jobs, 
            relaxed, link
        );
    }

    public Optional<String> getLink() {
        return link == null ? Optional.empty() : Optional.of(link);
    }

    public JobType getType() {
        return type;
    }

    public HashMap<String, String> getProperties() {
        return properties;
    }

    public HashSet<Long> getPrerequisiteJobs() {
        return prerequisite_jobs;
    }
}
