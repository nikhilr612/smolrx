package smolrx.msg;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;

import smolrx.RXException;
import smolrx.SecureChannel;
import smolrx.Servlet;
import smolrx.jobs.JobManager;
import smolrx.storage.ObjectStorage;

/**
 * Push multiple results in Bulk.
 */
public final class BulkPush extends ClientMessage {

    private static final long serialVersionUID = 123456789L;

    /**
     * Map job IDs to their results.
     */
    HashMap<Long, Object> results = new HashMap<>();

    public void push(long jobId, Object result) {

        if (this.results == null) {
            this.results = new HashMap<>();
        }
        if (this.results.containsKey(jobId)) {
            Servlet.LOGGER.log(Level.WARNING, "Job ID {0} already exists in the results map. Overwriting.", jobId);
        }
        this.results.put(jobId, result);
    }

    public BulkPush(HashMap<Long, Object> results) {
        if (this.results == null) {
            this.results = new HashMap<>();
        }
        if (results == null) {
            throw new IllegalArgumentException("Results map cannot be null.");
        }
        this.results = results;
    }

    public Set<Entry<Long, Object>> getResults() {
        return this.results.entrySet();
    }

    public Set<Long> getJobs() {
        return this.results.keySet();
    }
    /**
     * The role key identifying suitable tasks.
     */
     String roleKey = "";

    @Override
    public void handle(SecureChannel channel, JobManager jobManager, ObjectStorage objectStorage) throws RXException {
        jobManager.registerJobResults(this);
        try {
            objectStorage.putResultsBulk(this);
        } catch (IOException e) {
            throw new RXException("Failed to store bulk results", e);    
        }
        Servlet.LOGGER.log(Level.INFO, "Recieved BulkPush from client: {0}", channel.toString());
    }

    public String getRoleKey() {
        return this.roleKey;
    }
}
