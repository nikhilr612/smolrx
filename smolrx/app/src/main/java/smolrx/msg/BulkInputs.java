package smolrx.msg;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Server response containing only the program input objects for multiple jobs.
 */
public final class BulkInputs extends ServerMessage {
    private static final long serialVersionUID = 123454321L;

    /**
     * Map Job IDs to their inputs.
     */
    Map<Long, Object> inputs;

    /**
     * Number of failed fetches for the inputs.
     */
    int fetchFails = 0;
    
    public BulkInputs(HashMap<Long, Object> inputs, int fetchFails) {
        this.inputs = Collections.unmodifiableMap(inputs);
        this.fetchFails = fetchFails;
    }

    public Map<Long, Object> getInputs() {
        return inputs;
    }
}
