package smolrx.msg;

import java.io.IOException;
import java.security.InvalidKeyException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

import smolrx.RXException;
import smolrx.SecureChannel;
import smolrx.jobs.JobManager;
import smolrx.storage.ObjectStorage;

public final class InspectResult extends ClientMessage {
    long serialVersionUID = 3123454321L;

    /**
     * The job ID whose result is requested.
     */
    long forJobId;

    /**
     * The Aggregator JobID which requires this.
     */
    long parentJobId;
    
    /**
     * Key that determines if the client can play the role of aggregator.
     */
    String roleKey;

    /**
     * Limit the result objects sent over channel to this number, in case of redundancies.
     */
    int limit;

    public InspectResult(long forJobId, long parentJobId, String roleKey, int limit) {
        this.forJobId = forJobId;
        this.parentJobId = parentJobId;
        this.roleKey = roleKey;
        this.limit = limit;
    }
    
    public int getLimit() {
        return limit;
    }

    public long getJobId() {
        return forJobId;
    }

    public String getRoleKey() {
        return this.roleKey;
    }

    public long getParentJobId() {
        return parentJobId;
    }

    @Override
    public void handle(SecureChannel channel, JobManager jobManager, ObjectStorage objectStorage) throws RXException {
        jobManager.validateInspection(this);
        try {
            var results = objectStorage.getResults(this);
            channel.sendObject(results);
        } catch (ClassNotFoundException | IOException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
            throw new RXException("Failed to fetch and send results from object storage", e);
        }
    }
}
