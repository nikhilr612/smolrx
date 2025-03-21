package smolrx;
import java.io.IOException;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.logging.Logger;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class Client implements Runnable {

    /**
     * Host Name of the Server.
     */
    private String hostName;

    /**
     * Server port.
     */
    private int serverPort;

    private static Logger LOGGER = Logger.getLogger("smolrx-client");

    public Client(String hostName, int serverPort) {
        this.hostName = hostName;
        this.serverPort = serverPort;
    }

    @Override
    public void run() {
        Socket socket = null;
        try {
            socket = new Socket(this.hostName, this.serverPort);
        } catch (IOException e) {
            Client.LOGGER.severe("Failed to connect to remote server " + this.hostName + ":" + this.serverPort);
            e.printStackTrace();
            return;
        }

        SecureChannel channel = null;
        try {
            channel = SecureChannel.openServerChannel(socket);
        } catch (InvalidKeyException | InvalidKeySpecException | NoSuchAlgorithmException | NoSuchPaddingException
                | IllegalBlockSizeException | BadPaddingException | IOException e) {
            Client.LOGGER.severe("Failed to open secure channel to server.");
            e.printStackTrace();
            try {
                socket.close();
            } catch (IOException e1) {
                Client.LOGGER.severe("Failed to close connection socket.");
                e1.printStackTrace();
            }
            return;
        }

        Client.LOGGER.info("Channel opened!");

        try {
            System.out.println(channel.readObject());
        } catch (InvalidKeyException | ClassNotFoundException | IllegalBlockSizeException | BadPaddingException
                | IOException e) {
            Client.LOGGER.warning("Failed to read object from server.");
            e.printStackTrace();
        }

        try {
            channel.close();
        } catch (IOException e) {
            Client.LOGGER.severe("Failed to close channel to server");
            e.printStackTrace();
        }
    }
}
