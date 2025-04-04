package smolrx.jobs;

/**
 * Book-keeping and tracking completion of Job.
 */
public class JobMetadata {
    /**
     * The number of times an aggregator has requested the result(s) of this job.
     */
    int inspect_count;

    /**
     * The number of times the job has been completed.
     */
    int completion_count;

}
