package smolrx.msg;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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
    Map<Long, Object> results;

    public BulkPush(HashMap<Long, Object> results, String roleKey) {
        this.roleKey = roleKey;
        if (results == null) {
            throw new IllegalArgumentException("Results map cannot be null.");
        }
        this.results = Collections.unmodifiableMap(results);
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
