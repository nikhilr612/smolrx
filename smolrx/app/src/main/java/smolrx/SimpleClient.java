package smolrx;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.Socket;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import smolrx.msg.InspectResult;
import smolrx.msg.JarRequest;
import smolrx.msg.JobRequest;
import smolrx.msg.Joblisting;
import smolrx.msg.ProtocolConfig;
import smolrx.msg.PushResult;
import smolrx.msg.SignOff;
import smolrx.msg.Termination;

/**
 * A simple client implementation, that simply performs only 1 job- the highest priority job available for its role.
 */
public class SimpleClient implements Runnable {

    /**
     * Host Name of the Server.
     */
    private String hostName;

    /**
     * Server port.
     */
    private int serverPort;

    private int min_priority;
    private String roleKey;

    static Logger LOGGER = Logger.getLogger("smolrx-client");

    public SimpleClient(String hostName, int serverPort, int min_priority, String roleKey) {
        this.hostName = hostName;
        this.serverPort = serverPort;
        this.roleKey = roleKey;
    }

    @Override
    public void run() {
        Socket socket = null;
        try {
            socket = new Socket(this.hostName, this.serverPort);
        } catch (IOException e) {
            SimpleClient.LOGGER.severe("Failed to connect to remote server " + this.hostName + ":" + this.serverPort);
            e.printStackTrace();
            return;
        }

        SecureChannel channel = null;
        try {
            channel = SecureChannel.openServerChannel(socket);
        } catch (InvalidKeyException | InvalidKeySpecException | NoSuchAlgorithmException | NoSuchPaddingException
                | IllegalBlockSizeException | BadPaddingException | IOException e) {
            SimpleClient.LOGGER.severe("Failed to open secure channel to server.");
            e.printStackTrace();
            try {
                socket.close();
            } catch (IOException e1) {
                SimpleClient.LOGGER.severe("Failed to close connection socket.");
                e1.printStackTrace();
            }
            return;
        }

        SimpleClient.LOGGER.info("Channel opened!");

        try {
            var _config_ignore = channel.readObject(); // read the config object.
            if (!(_config_ignore instanceof ProtocolConfig)) {
                throw new RuntimeException("Invalid protocol config object received.");
            }

            var jobreq = new JobRequest(this.min_priority, 5, this.roleKey);
            channel.sendObject(jobreq);

            var readObj = channel.readObject();
            if (readObj instanceof Termination t) {
                throw new RuntimeException("Server terminated session: " + t.getCause());
            }

            var jobl = (Joblisting)readObj;

            SimpleClient.LOGGER.info("Received job listing");
            jobl.printTable();

            var jobId = jobl.getJobIDs().get(0);
            var jobInfo = jobl.getJobInfos().get(0);
            
            SimpleClient.LOGGER.info("Job ID: " + jobId + ", Job Type: " + jobInfo.getType());
            SimpleClient.LOGGER.fine("Requesting jar file for job ID: " + jobId);

            var jarreq = new JarRequest(jobId, this.roleKey);
            channel.sendObject(jarreq);

            SimpleClient.LOGGER.info("Jar file requested. Waiting for jar file.");

            var programInput = channel.readObject();
            if(programInput instanceof Termination) {
                throw new RuntimeException("Server terminated session: " + ((Termination) programInput).getCause());
            }
            SimpleClient.LOGGER.info("Received program input: " + programInput + " . Creating temporary file.");

            var tmpf = File.createTempFile("smolrx", ".jar");
            tmpf.deleteOnExit();

            FileOutputStream fos = new FileOutputStream(tmpf);
            channel.readStream(fos);
            fos.close();

            SimpleClient.LOGGER.info("Read jar file.");

            var className = jobInfo.getProperties().getOrDefault("Xclass", "Main");

            switch (jobInfo.getType()) {
                case AUDIT:
                    throw new RuntimeException("Audit jobs are not supported yet.");
                case COLLECT:
                    var pRResult = handle_reducer_job(channel, tmpf, programInput, className, jobInfo.getPrerequisiteJobs(), jobId);
                    channel.sendObject(pRResult);
                    SimpleClient.LOGGER.info("Sending result: " + pRResult.toString());
                    break;
                case SLOG:
                    SimpleClient.LOGGER.info("Starting slog job.");
                    var pResult = handle_slog_job(tmpf, programInput, className, jobId);
                    SimpleClient.LOGGER.info("Sending result: " + pResult.toString());
                    channel.sendObject(pResult);
                    break;
            }

            // We're done so let's sign-off.
            channel.sendObject(new SignOff());
        } catch (IOException e) {
            SimpleClient.LOGGER.log(Level.SEVERE, "Failed to send object to server.", e);
        } catch (InvalidAlgorithmParameterException | InvalidKeyException | ClassNotFoundException | IllegalBlockSizeException | BadPaddingException e) {
            SimpleClient.LOGGER.log(Level.SEVERE, "Failed to read object from server.", e);
        }

        try {
            channel.close();
        } catch (IOException e) {
            SimpleClient.LOGGER.log(Level.SEVERE, "Failed to close channel to server", e);
        }
    }

    private PushResult handle_slog_job(File tmpf, Object programInput, String className, long job_id) throws IOException {
        Object result;
        try {
            result = JarLoader.loadJar(tmpf, className).apply(programInput);
        } catch (MalformedURLException | ClassNotFoundException | InstantiationException | IllegalAccessException
                | InvocationTargetException | SecurityException | NoSuchMethodException e) {
            SimpleClient.LOGGER.log(Level.SEVERE, "Failed to run jar file.", e);
            throw new IOException("jar run failed", e);
        }
        return new PushResult(job_id, this.roleKey, result);
    }

    private PushResult handle_reducer_job(SecureChannel channel, File tmpf, Object programInput, String className, Set<Long> prerequisiteJobs, long job_id) {
        var input = programInput != null ? programInput : 0;
        try {
            var reducer = JarLoader.loadJar(tmpf, className);
            for (var fjob : prerequisiteJobs) {
                var inspreq = new InspectResult(fjob, job_id, roleKey, 1); // take only 1 for now.
                channel.sendObject(inspreq);
                var response = channel.readObject();
                if (response instanceof Termination) {
                    throw new RuntimeException("Server terminated session: " + ((Termination) response).getCause());
                }
                var first_result = ((Object[])response)[0]; // just take index 0
                var red_input = new Object[]{input, first_result}; // combine the two inputs.
                input = reducer.apply(red_input);
            }
            return new PushResult(job_id, this.roleKey, input);
        } catch (MalformedURLException | ClassNotFoundException | InstantiationException | IllegalAccessException
                | InvocationTargetException | SecurityException | NoSuchMethodException e) {
            SimpleClient.LOGGER.log(Level.SEVERE, "Failed to run jar file.", e);
            throw new RuntimeException("jar run failed", e);
        } catch (InvalidAlgorithmParameterException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException | IOException e) {
            SimpleClient.LOGGER.log(Level.SEVERE, "Failed to send object to server.", e);
            throw new RuntimeException("operation failed", e);
        }

    }
}
