package smolrx.msg;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.util.logging.Level;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

import smolrx.RXException;
import smolrx.SecureChannel;
import smolrx.Servlet;
import smolrx.jobs.JobManager;
import smolrx.storage.ObjectStorage;

/**
 * Application message to request a list of jobs up for grabs.
 */
public final class JobRequest extends ClientMessage {

    private static final long serialVersionUID = 432123456789L;

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
    String roleKey;

    public JobRequest(long min_priority, int limit, String roleKey) {
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

    public String getRoleKey() {
        return roleKey;
    }

    @Override
    public void handle(SecureChannel channel, JobManager jobManager, ObjectStorage objectStorage) throws RXException {
        try {
            channel.sendObject(jobManager.listJobs(this));
            Servlet.LOGGER.log(Level.INFO, "Sent job listing to client: " + channel.toString());
        } catch (InvalidKeyException | IllegalBlockSizeException | BadPaddingException | IOException | RXException e) {
            Servlet.LOGGER.log(Level.WARNING, "Failed to send job listing to client: " + channel.toString(), e);
            throw new RXException("Failed to send job listing.", e);
        }
    }
}
