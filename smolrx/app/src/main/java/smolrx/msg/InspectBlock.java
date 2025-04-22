package smolrx.msg;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

import smolrx.RXException;
import smolrx.SecureChannel;
import smolrx.jobs.JobManager;
import smolrx.storage.ObjectStorage;

/**
 * Request a "block" of results (from various jobs) from the server.
 */
public final class InspectBlock extends ClientMessage {
    private static final long serialVersionUID = 77678765432L;

    /**
     * 
     */
    private int parentJobId;

    /**
     * The number of redundant results per job to be included in the response.
     */
    private int redLimit;

    /**
     * The start of the range of job IDs
     */
    private long jobid_start;

    /**
     * The end of the range of job IDs.
     */
    private long jobid_end;

    /**
     * Additional job IDs.
     */
    private List<Long> additional_jobs;

    /**
     * The role key of the client.
     */
    private String roleKey;

    /**
     * Create an InspectBlock request with the specified parameters.
     * @param redLimit The number of redundant results to fetch for each job.
     * @param jobid_start The start of the range of jobIDs to query for.
     * @param jobid_end The end of the range of jobIDs to query for.
     * @param additional_jobs Additional jobIDs to query for.
     * @param roleKey The roleKey for this operation.
     */
    public InspectBlock(int redLimit, long jobid_start, long jobid_end, ArrayList<Long> additional_jobs, String roleKey) {
        this.roleKey = roleKey;
        this.additional_jobs = Collections.unmodifiableList(additional_jobs);
        this.jobid_start = jobid_start;
        this.jobid_end = jobid_end;
        this.redLimit = redLimit;
    }

    public int getParentJobId() {
        return parentJobId;
    }

    public int getRedLimit() {
        return redLimit;
    }

    public long getJobRangeStart() {
        return jobid_start;
    }

    public long getJobRangeEnd() {
        return jobid_end;
    }

    public String getRoleKey() {
        return roleKey;
    }

    public List<Long> getAdditionalJobs() {
        return additional_jobs;
    }

    public long getSize() {
        long ret = 0;
        if (this.jobid_start > this.jobid_end) {
            ret += this.jobid_end - this.jobid_start;
        }
        if (this.additional_jobs != null) {
            ret += this.additional_jobs.size();
        }
        return ret;
    }

    @Override
    public void handle(SecureChannel channel, JobManager jobManager, ObjectStorage objectStorage) throws RXException {
        jobManager.validateBlockInspection(this);
        try {
            channel.sendObject(objectStorage.getResultsBlock(this));
        } catch (InvalidKeyException | IllegalBlockSizeException | BadPaddingException
                | InvalidAlgorithmParameterException | IOException e) {
            throw new RXException("Failed to get block of results", e);
        }
    }
}
