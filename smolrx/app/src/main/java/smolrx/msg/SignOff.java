package smolrx.msg;

import smolrx.RXException;
import smolrx.SecureChannel;
import smolrx.Server;
import smolrx.jobs.JobManager;
import smolrx.storage.ObjectStorage;

/**
 * Message to signal the end of a session. The server will close the channel after this message.
 */
public final class SignOff extends ClientMessage {

    private static final long serialVersionUID = 7564534231L;

    public SignOff() {}

    @Override
    public void handle(SecureChannel channel, JobManager jobManager, ObjectStorage objectStorage) throws RXException {
        // Do nothing here; other than just logging the client disconnection .
        Server.LOGGER.info("Channel " + channel.toString() + " closed by client.");
    }
}
