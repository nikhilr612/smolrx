package smolrx.msg;

import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Set;

import smolrx.RXException;
import smolrx.SecureChannel;
import smolrx.jobs.JobManager;
import smolrx.storage.ObjectStorage;

/**
 * Push multiple results in Bulk.
 */
public final class BulkPush extends ClientMessage{
    /**
     * Map job IDs to their results.
     */
    HashMap<Long, Object> results = new HashMap<>();

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
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'handle'");
    }

    public String getRoleKey() {
        return this.roleKey;
    }
}
