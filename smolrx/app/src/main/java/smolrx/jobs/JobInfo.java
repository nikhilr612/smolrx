package smolrx.jobs;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Data-class that maintains instance-specific information about a program to be executed.
 */
public class JobInfo implements Serializable, Comparable<JobInfo> {
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

    private JobInfo() {}

    /**
     * Clone and remove unnecessary information.
     * @return Cloned object.
     */
    protected JobInfo maskedClone() {
        var jinfo = new JobInfo();
    
        jinfo.redundancy_count = 0;
        jinfo.jobData = null;
        jinfo.programId = -1;

        jinfo.prerequisite_jobs = this.prerequisite_jobs;
        jinfo.type = this.type;
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
}
