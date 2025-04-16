package smolrx.msg;

import java.io.IOException;

import smolrx.RXException;
import smolrx.SecureChannel;
import smolrx.jobs.JobManager;
import smolrx.storage.ObjectStorage;

public final class PushResult extends ClientMessage {

    private static final long serialVersionUID = 65432123456L;

    /**
     * An identifier which specifies the role of the client.
     */
    String roleKey;

    /**
     * ID of the JOB whose result has been computed.
     */
    long job_id;

    /**
     * The result computed.
     */
    Object resultObject;

    public PushResult(long job_id, String roleKey, Object resultObject) {
        this.job_id = job_id;
        this.roleKey = roleKey;
        this.resultObject = resultObject;
    }

    public String getRoleKey() {
        return roleKey;
    }

    public long getJobId() {
        return job_id;
    }

    public Object getResultObject() {
        return resultObject;
    }

    @Override
    public String toString() {
        return "PushResult{" +
                "roleKey='" + roleKey + '\'' +
                ", job_id=" + job_id +
                ", resultObject={" + resultObject.toString() +
                "}}";
    }

    @Override
    public void handle(SecureChannel channel, JobManager jobManager, ObjectStorage objectStorage) throws RXException {
        jobManager.registerJobResult(this);
        try {
            objectStorage.putResult(this);
        } catch (IOException e) {
            // This re-throw is correct.
            throw new RXException("Failed to store result object", e);
        }
    }
}
