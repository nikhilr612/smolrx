package smolrx.msg;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class BulkResults extends ServerMessage {
    private static final long serialVersionUID = 87878781234L;

    /**
     * Map Job IDs to their results.
     */
    private final Map<Long, Object[]> results;

    /**
     * Number of failed fetches for the results.
     */
    private final int fetchFails;

    public BulkResults(HashMap<Long, Object[]> results, int fetchFails) {
        this.results = Collections.unmodifiableMap(results);
        this.fetchFails = fetchFails;
    }

    public Map<Long, Object[]> getResults() {
        return results;
    }

    public int getFetchFails() {
        return fetchFails;
    }
}
