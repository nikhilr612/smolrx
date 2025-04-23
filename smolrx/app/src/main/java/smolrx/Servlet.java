package smolrx;

import java.io.IOException;
import java.net.Socket;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import smolrx.jobs.JobManager;
import smolrx.msg.ClientMessage;
import smolrx.msg.ProtocolConfig;
import smolrx.msg.SignOff;
import smolrx.storage.ObjectStorage;

public class Servlet implements Runnable {

    /**
     * Connection socket for the client handled by this servlet.
     */
    private final Socket conn; // Un-tracked by Servlet. Managed by channel.

    /**
     * Secure channel to client.
     */
    private SecureChannel channel;

    /**
     * Reference to server's job manager.
     */
    private final JobManager sJobManager;

    /**
     * Reference to server's object storage.
     */
    private final ObjectStorage sObjectStorage;

    public static final Logger LOGGER = Logger.getLogger("smolrx-servlet");

    /**
     * Create a new Servlet to handle connection from a client.
     * @param clientConnSocket Connection socket for the new client.
     */
    protected Servlet(Socket clientConnSocket, JobManager jobManager, ObjectStorage objectStorage) {
        this.conn = clientConnSocket;
        this.sJobManager = jobManager;
        this.sObjectStorage = objectStorage;
    }

    @Override
    public void run() {
        try {
            this.channel = SecureChannel.openClientChannel(this.conn);
        } catch (IOException | InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException | IllegalBlockSizeException | BadPaddingException e) {
            Servlet.LOGGER.log(Level.WARNING, "Failed to open secure channel with client: " + this.conn.toString(), e);
            try {
                this.conn.close();
            } catch (IOException e1) {
                Servlet.LOGGER.log(Level.WARNING, "Failed to close client connection", e1);
            }
            return;
        }

        Servlet.LOGGER.log(Level.INFO, "Channel opened for client {0}", this.conn.toString());

        // For now, if something goes wrong here; bee-line to servlet end.
        // Try-catch gore occurs because RXExceptions can re-throw channel errors.
        try {
            // -- 
            try {
                this.channel.sendObject(
                    new ProtocolConfig(this.sJobManager.getBulkRequestLimit(), this.sJobManager.getBulkPushLimit())
                );
                while (true){
                    // it MUST be a client message.
                    var clientMessage = (ClientMessage)this.channel.readObject();
                    clientMessage.handle(this.channel, this.sJobManager, this.sObjectStorage);
                    if (clientMessage instanceof SignOff) {
                        break; // End of session.
                    }
                }
            } catch (RXException e) {
                this.channel.sendObject(e.intoTerminationMessage());
            }
            // --
        } catch (InvalidKeyException | IllegalBlockSizeException | BadPaddingException | IOException | InvalidAlgorithmParameterException e) {
            Servlet.LOGGER.log(Level.WARNING, "Failed to send object message.", e);
        } catch (ClassNotFoundException e) {
            Servlet.LOGGER.log(Level.WARNING, "Received unknown object from " + this.channel, e);
        }

        try {
            this.channel.close();
        } catch (IOException e) {
            Servlet.LOGGER.log(Level.WARNING, "Failed to close channel to client");
        }
    }
}
