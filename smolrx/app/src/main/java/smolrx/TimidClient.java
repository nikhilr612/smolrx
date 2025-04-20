package smolrx;

import java.io.File;
import java.io.FileOutputStream;
import java.net.Socket;
import smolrx.msg.*;

public class TimidClient implements Runnable {

    private final String hostName;
    private final int serverPort;
    private final int minPriority;
    private final String roleKey;

    public TimidClient(String hostName, int serverPort, int minPriority, String roleKey) {
        this.hostName = hostName;
        this.serverPort = serverPort;
        this.minPriority = minPriority;
        this.roleKey = roleKey;
    }

    @Override
    public void run() {
        long startTime = System.currentTimeMillis();
        long endTime = startTime;

        try (Socket socket = new Socket(hostName, serverPort)) {
            SecureChannel channel = SecureChannel.openServerChannel(socket);

            var _config_ignore = channel.readObject(); // read the config object.
            if (!(_config_ignore instanceof ProtocolConfig)) {
                throw new RuntimeException("Invalid protocol config object received.");
            }

            JobRequest jobRequest = new JobRequest(minPriority, 1, roleKey);
            channel.sendObject(jobRequest);

            Object response = channel.readObject();
            if (response instanceof Termination termination) {
                throw new RuntimeException("Server terminated session: " + termination.getCause());
            }

            Joblisting jobListing = (Joblisting) response;
            long jobId = jobListing.getJobIDs().get(0);

            JarRequest jarRequest = new JarRequest(jobId, roleKey);
            channel.sendObject(jarRequest);

            Object programInput = channel.readObject();
            if (programInput instanceof Termination termination) {
                throw new RuntimeException("Server terminated session without sending program input: " + termination.getCause());
            }

            File tempJar = File.createTempFile("smolrx", ".jar");
            tempJar.deleteOnExit();

            try (FileOutputStream fos = new FileOutputStream(tempJar)) {
                channel.readStream(fos);
            }

            endTime = System.currentTimeMillis();

            // Sign off from the session
            channel.sendObject(new SignOff());
            channel.close();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.out.println((endTime - startTime));
        }
    }
}