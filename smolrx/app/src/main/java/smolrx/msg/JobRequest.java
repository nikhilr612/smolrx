package smolrx.msg;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.util.Optional;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

import smolrx.RXException;
import smolrx.SecureChannel;
import smolrx.jobs.JobManager;
import smolrx.storage.ObjectStorage;

/**
 * Application message to request a list of jobs up for grabs.
 */
public final class JobRequest extends ClientMessage {
    /**
     * Minimum priority of jobs to be listed.
     */
    long min_priority;

    // /**
    //  * Filter jobs by this type.
    //  */
    // JobType type;

    /**
     * List at most this number of jobs.
     */
    int limit;

    /**
     * If auditing, specify an audit key.
     */
    Optional<String> roleKey;

    public JobRequest(long min_priority, int limit, Optional<String> roleKey) {
        this.min_priority = min_priority;
        this.limit = limit;
        this.roleKey = roleKey;
    }

    public int getLimit() {
        return limit;
    }

    public long getMinPriority() {
        return min_priority;
    }

    public Optional<String> getRoleKey() {
        return roleKey;
    }

    @Override
    public void handle(SecureChannel channel, JobManager jobManager, ObjectStorage objectStorage) throws RXException {
        try {
            channel.sendObject(jobManager.listJobs(this));
        } catch (InvalidKeyException | IllegalBlockSizeException | BadPaddingException | IOException | RXException e) {
            throw new RXException("Failed to send job listing.", e);
        }
    }
}
