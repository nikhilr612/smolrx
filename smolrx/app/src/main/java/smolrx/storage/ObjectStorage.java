package smolrx.storage;

import java.io.IOException;

import smolrx.msg.BulkPush;
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
}
