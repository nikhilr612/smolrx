package smolrx;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Logger;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class Servlet implements Runnable {

    /**
     * Connection socket for the client handled by this servlet.
     */
    private Socket conn; // Un-tracked by Servlet. Managed by channel.

    /**
     * Secure channel to client.
     */
    private SecureChannel channel;

    private static Logger LOGGER = Logger.getLogger("smolrx-servlet");

    /**
     * Create a new Servlet to handle connection from a client.
     * @param clientConnSocket Connection socket for the new client.
     */
    protected Servlet(Socket clientConnSocket) {
        this.conn = clientConnSocket;
    }

    @Override
    public void run() {
        try {
            this.channel = SecureChannel.openClientChannel(this.conn);
        } catch (IOException | InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException | IllegalBlockSizeException | BadPaddingException e) {
            Servlet.LOGGER.warning("Failed to open secure channel with client: " + this.conn.toString());
            e.printStackTrace();
            try {
                this.conn.close();
            } catch (IOException e1) {
                Servlet.LOGGER.warning("Failed to close client connection");
                e1.printStackTrace();
            }
            return;
        }

        Servlet.LOGGER.info("Channel opened for client " + this.conn.toString());

        try {
            this.channel.sendObject("Hello!");
            var f = new File("test.txt"); // Send file to test channel.
            var fis = new FileInputStream(f);
            this.channel.sendStream(fis);
            fis.close();
        } catch (InvalidKeyException | IllegalBlockSizeException | BadPaddingException | IOException e) {
            Servlet.LOGGER.warning("Failed to send object message.");
            e.printStackTrace();
        }

        try {
            this.channel.close();
        } catch (IOException e) {
            Servlet.LOGGER.warning("Failed to close channel to client");
            e.printStackTrace();
        }
    }
}
