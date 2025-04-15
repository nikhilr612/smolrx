package smolrx.msg;

import java.io.Serializable;

import smolrx.RXException;
import smolrx.SecureChannel;
import smolrx.jobs.JobManager;
import smolrx.storage.ObjectStorage;

/**
 * Aggregator interface for messages sent by the client.
 */
public abstract sealed class ClientMessage implements Serializable 
    permits JobRequest, JarRequest, PushResult, InspectResult, InputRequest, SignOff, BulkPush {
    public abstract void handle(SecureChannel channel, JobManager jobManager, ObjectStorage objectStorage) throws RXException;
}
