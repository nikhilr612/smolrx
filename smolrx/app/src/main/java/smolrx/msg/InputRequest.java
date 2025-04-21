package smolrx.msg;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

import smolrx.RXException;
import smolrx.SecureChannel;
import smolrx.Servlet;
import smolrx.jobs.JobManager;
import smolrx.storage.ObjectStorage;

/**
 * Request only the program input objects for various jobs in bulk.
 */
public final class InputRequest extends ClientMessage {
    private static final long serialVersionUID = 2123454321L;

    /**
     * The start of the range of job IDs to request input for.
     */
    long jobid_start = 0;

    /**
     * The end of the range of job IDs to request input for.
     */
    long jobid_end = -1;

    /**
     * The role key of the client.
     */
    String roleKey = "";

    /**
     * Additional jobs to request input for. This is a list of job IDs that are not in the range of jobid_start and jobid_end.
     */
    List<Long> additional_jobs;

    /**
     * Constructor for InputRequest.
     * @param roleKey The role key of the client, required to request inputs.
     * @param jobid_start The start of the range of job IDs to request input for.
     * @param jobid_end The end of the range of job IDs to request input for. 
     * @param additional_jobs A list of additional job IDs to request input for. This is a list of job IDs that are not in the range of jobid_start and jobid_end.
     */
    public InputRequest( String roleKey, long jobid_start, long jobid_end, ArrayList<Long> additional_jobs) {
        this.jobid_start = jobid_start;
        this.jobid_end = jobid_end;
        this.roleKey = roleKey;
        this.additional_jobs = Collections.unmodifiableList(additional_jobs);
    }

    @Override
    public void handle(SecureChannel channel, JobManager jobManager, ObjectStorage objectStorage) throws RXException {
        var response = jobManager.getJobInputs(this);
        if (response.fetchFails > 0) {
            Servlet.LOGGER.log(Level.WARNING, "Client requested " + this.getSize() + " inputs, but " + response.fetchFails + " failed to fetch. Channel=" + channel.toString());
        }
        try {
            channel.sendObject(response);
        } catch (InvalidKeyException | IllegalBlockSizeException | BadPaddingException | IOException | InvalidAlgorithmParameterException e) {
            Servlet.LOGGER.log(Level.WARNING, "Failed to send program inputs", e);
            throw new RXException("Failed to send program inputs", e);
        }
    }

    public long getJobRangeStart() {
        return jobid_start;
    }

    public long getJobRangeEnd() {
        return jobid_end;
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

    public String getRoleKey() {
        return roleKey;
    }

    public List<Long> getAdditionalJobs() {
        return additional_jobs;
    }
}
