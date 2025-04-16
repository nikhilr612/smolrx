package smolrx.msg;

import java.util.HashMap;

/**
 * Server response containing only the program input objects for multiple jobs.
 */
public final class BulkInputs extends ServerMessage {
    private static final long serialVersionUID = 123454321L;

    /**
     * Map Job IDs to their inputs.
     */
    HashMap<Long, Object> inputs = new HashMap<>();

    /**
     * Number of failed fetches for the inputs.
     */
    int fetchFails = 0;
    
    public BulkInputs(HashMap<Long, Object> inputs, int fetchFails) {
        this.inputs = inputs;
        this.fetchFails = fetchFails;
    }

    public HashMap<Long, Object> getInputs() {
        return inputs;
    }
}
