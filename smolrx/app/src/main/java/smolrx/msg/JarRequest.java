package smolrx.msg;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.util.logging.Level;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

import smolrx.RXException;
import smolrx.SecureChannel;
import smolrx.Servlet;
import smolrx.jobs.JobManager;
import smolrx.storage.ObjectStorage;

/**
 * Application message to request a Jar associated with a jobId.
 */
public final class JarRequest extends ClientMessage {

    private static final long serialVersionUID = 3141592653589793L;

    /**
     * A valid Job ID.
     */
    long jobId;

    /**
     * Must be a valid slogger or aggregator key.
     */
    String roleKey;

    /**
     * If true, the Jar file will not be fetched from the object storage.
     */
    boolean noFetch;

    public JarRequest(long jobId, String roleKey) {
        this.jobId = jobId;
        this.roleKey = roleKey;
        this.noFetch = false;
    }

    public long getJobId() {
        return jobId;
    }

    public JarRequest noFetch() {
        this.noFetch = true;
        return this;
    }

    public String getRoleKey() {
        return roleKey;
    }

    @Override
    public void handle(SecureChannel channel, JobManager jobManager, ObjectStorage objectStorage) throws RXException {
        var jobInfo = jobManager.fetchJobInfoPair(this);
        var jarPath = jobInfo.getKey();
        var programInput = jobInfo.getValue();

        // Send program input first.
        try {
            channel.sendObject(programInput);
        } catch (InvalidKeyException | IllegalBlockSizeException | BadPaddingException | IOException e) {
            Servlet.LOGGER.log(Level.WARNING, "Failed to send program input", e);
            throw new RXException("Failed to send program input", e);
        }

        // No need to fetch the jar file.
        if (this.noFetch) return;

        // Send the associated program file next.
        try (FileInputStream fis = new FileInputStream(jarPath)) {
            channel.sendStream(fis);
        } catch (FileNotFoundException e) {
            Servlet.LOGGER.log(Level.SEVERE, "Jar file not found: " + jarPath, e);
            throw new RXException("Could not find local Jar file", e);
        } catch (InvalidKeyException | IllegalBlockSizeException | BadPaddingException | IOException e1) {
            Servlet.LOGGER.log(Level.SEVERE, "Failed to send jar file: " + jarPath, e1);
            throw new RXException("Failed to stream file.", e1);
        }

        Servlet.LOGGER.log(Level.INFO, "Sent jar file: " + jarPath + " to client: " + channel.toString());

    }
}
