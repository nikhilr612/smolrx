package smolrx.jobs;

import java.io.Serializable;

/**
 * Book-keeping and tracking completion of Job.
 */
public class JobMetadata implements Serializable {

    private static final long serialVersionUID = 0xdeadbeefL;

    /**
     * The number of times an aggregator has requested the result(s) of this job.
     */
    int inspect_count = 0;

    /**
     * The number of times the job has been completed.
     */
    int completion_count = 0;

    public JobMetadata() {
        // Default constructor.
    }

}
