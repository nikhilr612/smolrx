package smolrx;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.logging.Logger;

import smolrx.jobs.JobManager;
import smolrx.storage.ObjectStorage;

public class Server extends Thread {

    /**
     * Primary TCP-Server Socket to listen for connections.
     */
    private ServerSocket serverSocket;

    /**
     * Flag to track status of Server.
     */
    private boolean alive;

    /**
     * The server's Job manager.
     */
    private JobManager jobManager;

    /**
     * Object storage responsible for storing pushed results.
     */
    private ObjectStorage storage;

    /**
     * Logger for servers.
     */
    public static Logger LOGGER = Logger.getLogger("smolrx-server");

    /**
     * Create a new SmolRX Server with specified port and backlog. Listen on all addresses.
     * @param port The port to listen on for client connections.
     * @param backlong Maximum number of client connections to queue.
     * @throws IOException If the underlying ServerSocket could not be acquired.
     */
    public Server(int port, int backlong, JobManager manager, ObjectStorage storage) throws IOException {
        this.serverSocket = new ServerSocket(port, port);
        this.alive = true;
    }

    /**
     * Kill this server.
     */
    public void kill() {
        this.alive = false;
    }

    @Override
    public void run() {
        while (this.alive) {
            try {
                Server.LOGGER.fine("Listening for clients at: " + serverSocket.toString());
                var clientConnSocket = this.serverSocket.accept();
                LOGGER.info("Received connection from " + clientConnSocket.getInetAddress());
                var servlet = new Servlet(clientConnSocket, jobManager, storage);
                Thread.ofVirtual().start(servlet); // Start virtual thread.
            } catch (IOException e) {
                Server.LOGGER.warning("Failed to accept client connection. Error occurred while blocking.");
                e.printStackTrace();
            }
        }

        try {
            this.serverSocket.close();
        } catch (IOException e) {
            Server.LOGGER.severe("Failed to close server socket.");
            e.printStackTrace();
        }
    }

}
