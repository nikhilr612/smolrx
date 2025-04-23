package smolrx.storage;

import java.io.IOException;
import java.util.HashMap;

import smolrx.msg.BulkPush;
import smolrx.msg.BulkResults;
import smolrx.msg.InspectBlock;
import smolrx.msg.InspectResult;
import smolrx.msg.PushResult;

/**
 * Abstract over mechanisms to store and retrieve the result data sent by clients.
 */
public abstract class ObjectStorage {
    /**
     * For a given request to inspect results, return the results stored thus far.
     * Precondition: The request should be authorized and valid.
     * @param iResult The request to inspect results.
     * @return The list of objects corresponding to the results.
     * @throws IOException 
     * @throws ClassNotFoundException 
     */
    public abstract Object[] getResults(InspectResult iResult) throws IOException, ClassNotFoundException;

    /**
     * Store a result
     * Precondition: The operation should be authorized and valid.
     * @param pResult The result to store.
     * @throws IOException 
     */
    public abstract void putResult(PushResult pResult) throws IOException;

    /**
     * Store multiple results in bulk.
     * Precondition: The operation should be authorized and valid.
     * @param bulkPush The bulk push request containing the results to store.
     * @throws IOException 
     */
    public void putResultsBulk(BulkPush bulkPush) throws IOException {
        for (var entry : bulkPush.getResults()) {
            var jobId = entry.getKey();
            var result = entry.getValue();
            putResult(new PushResult(jobId, bulkPush.getRoleKey(), result));
        }
    }

    public BulkResults getResultsBlock(InspectBlock blockRequest) {
        int fetchFails = 0;
        HashMap<Long, Object[]> blockmap = new HashMap<>();
        var parent = blockRequest.getParentJobId();
        var roleKey = blockRequest.getRoleKey();
        var redLimit = blockRequest.getRedLimit();

        // Let's get the range first.
        for (long i = blockRequest.getJobRangeStart(); i < blockRequest.getJobRangeEnd(); i++) {
            try {
                var results = getResults(new InspectResult(i, parent, roleKey, redLimit));
                blockmap.put(i, results);
            } catch (ClassNotFoundException | IOException e) {
                fetchFails += 1;
            }
        }

        // Now for the additional jobs.
        for (long job_id : blockRequest.getAdditionalJobs()) {
            try {
                var results = getResults(new InspectResult(job_id, parent, roleKey, redLimit));
                blockmap.put(job_id, results);
            } catch (ClassNotFoundException | IOException e) {
                fetchFails += 1;
            }
        }

        return new BulkResults(blockmap, fetchFails);
    }
}
