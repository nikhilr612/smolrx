package smolrx;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

import smolrx.jobs.JobInfo;
import smolrx.jobs.JobType;
import smolrx.msg.BulkInputs;
import smolrx.msg.BulkPush;
import smolrx.msg.InputRequest;
import smolrx.msg.JarRequest;
import smolrx.msg.JobRequest;
import smolrx.msg.Joblisting;
import smolrx.msg.ProtocolConfig;
import smolrx.msg.SignOff;
import smolrx.msg.Termination;

public class ParallelBulkClient implements Runnable {
    private static final int MAX_CONCURRENT_JOBS = Runtime.getRuntime().availableProcessors();
    private static final Logger LOGGER = Logger.getLogger("smolrx-bulk-client");

    private final String hostName;
    private final int serverPort;
    private Long minJobId;
    private Long maxJobId;
    private final int maxJobIds;
    private final int minPriority;
    private final String roleKey;

    public ParallelBulkClient(String hostName, int serverPort, int minPriority, int maxJobIds, String roleKey) {
        this.hostName = hostName;
        this.serverPort = serverPort;
        this.minJobId = null;
        this.maxJobId = null;
        this.minPriority = minPriority;
        this.maxJobIds = maxJobIds;
        this.roleKey = roleKey;
    }

    @Override
    @SuppressWarnings("LoggerStringConcat")
    public void run() {
        minJobId = Long.MAX_VALUE;
        maxJobId = Long.MIN_VALUE;
        JobType jobType = null;
        try (Socket socket = new Socket(hostName, serverPort)) {
            SecureChannel channel = SecureChannel.openServerChannel(socket);
            Object config = channel.readObject();
            if (!(config instanceof ProtocolConfig)) {
                throw new RuntimeException("Invalid protocol config object received.");
            }

            LOGGER.info("Requesting job listing...");
            JobRequest jobRequest = new JobRequest(minPriority, maxJobIds, roleKey);
            channel.sendObject(jobRequest);

            Object jobListingResp = channel.readObject();
            if (jobListingResp instanceof Termination term) {
                throw new RuntimeException("Server terminated session: " + term.getCause());
            }

            Joblisting jobListing = (Joblisting) jobListingResp;
            if (jobListing == null || jobListing.getJobIDs().isEmpty()) {
                channel.sendObject(new SignOff());
                channel.close();
                throw new RuntimeException("Job listing is null or empty.");
            }

            List<Long> jobIdsToProcess = jobListing.getJobIDs();
            for (long jobId : jobIdsToProcess) {
                if (jobId < minJobId) {
                    minJobId = jobId;
                }
                if (jobId > maxJobId) {
                    maxJobId = jobId;
                }
            }

            LOGGER.log(Level.INFO, "Requesting bulk inputs in range: {0} to {1}", new Object[]{minJobId, maxJobId});
            InputRequest inputRequest = new InputRequest(roleKey, minJobId, maxJobId + 1, new ArrayList<>());
            channel.sendObject(inputRequest);
            Object inputResponse = channel.readObject();

            if (inputResponse instanceof Termination term) {
                throw new RuntimeException("Server terminated session: " + term.getCause());
            }

            BulkInputs bulkInputs = (BulkInputs) inputResponse;
            Objects.requireNonNull(bulkInputs, "BulkInputs must not be null");
            LOGGER.log(Level.INFO, "Received {0} bulk inputs.", bulkInputs.getInputs().size());

            // Group job IDs by Program ID
            Map<Long, Map<Long, Object>> programToJobInputs = new HashMap<>();
            Map<Long, JobInfo> jobIdToInfo = new HashMap<>();

            List<Long> jobIds = jobListing.getJobIDs();
            List<JobInfo> jobInfos = jobListing.getJobInfos();
            jobType = jobInfos.get(0).getType(); // Assuming all jobs have the same type
            for (int i = 0; i < jobIds.size(); i++) {
                Long jobId = jobIds.get(i);
                if (jobId < minJobId || jobId > maxJobId) continue;

                JobInfo info = jobInfos.get(i);
                Object input = bulkInputs.getInputs().get(jobId);

                Long programId = info.getProgramId();
                programToJobInputs.computeIfAbsent(programId, k -> new HashMap<>()).put(jobId, input);
                jobIdToInfo.put(jobId, info);
            }

            HashMap<Long, Object> results = new HashMap<>();
            for (Map.Entry<Long, Map<Long, Object>> entry : programToJobInputs.entrySet()) {
                Long programId = entry.getKey();
                Map<Long, Object> jobsForProgram = entry.getValue();
                LOGGER.log(Level.INFO, "Requesting jar for program ID {0}", programId);
                channel.sendObject(new JarRequest(minJobId, roleKey));
                File tmpf = File.createTempFile("smolrx", ".jar");
                tmpf.deleteOnExit();
                FileOutputStream fos = new FileOutputStream(tmpf);
                var programInput = channel.readObject();
                channel.readStream(fos);
                fos.close();
                LOGGER.log(Level.INFO, "Downloaded JAR for program ID {0}", programId);
                if (programInput instanceof Termination progterm) {
                    throw new RuntimeException("Server terminated session: " + progterm.getCause());
                }
                LOGGER.info("Received program input: " + programInput);

                String className = jobsForProgram.keySet().stream()
                        .map(jobIdToInfo::get)
                        .filter(info -> info != null)
                        .map(info -> info.getProperties().getOrDefault("Xclass", "Main"))
                        .findFirst().orElse("Main");
                LOGGER.log(Level.INFO, "Loading jar file: {0} with class: {1}", new Object[]{tmpf.getAbsolutePath(), className});

                // print the entire jar file contents for debugging purposes without adding functions in other classses and doing it here.
                try (JarFile jarFile = new JarFile(tmpf)) {
                    LOGGER.info("Contents of the JAR file:");
                    jarFile.stream().forEach(jarEntry -> {
                        String entryType = jarEntry.isDirectory() ? "Directory" : "File";
                        long entrySize = jarEntry.getSize();
                        LOGGER.fine(String.format("Entry: %s | Type: %s | Size: %d bytes", jarEntry.getName(), entryType, entrySize));
                    });
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Failed to read JAR file contents: " + tmpf.getAbsolutePath(), e);
                }

                // Process jobs only if roleKey is SLOG
                if (jobType == JobType.SLOG) {
                    results = processJobsForProgram(tmpf, className, jobsForProgram);
                }
                BulkPush bulkPush = new BulkPush(results, roleKey);
                channel.sendObject(bulkPush);
                // print results for debugging purposes without adding functions in other classes and doing it here.
                LOGGER.info("BulkPush results: " + results.toString());
                results.clear(); // Clear the results for the next program ID
            }
            channel.sendObject(new SignOff());
            try {
                channel.close();
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to close channel to server", e);
            }

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error during socket connection", e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error during bulk processing", e);
        }
    }

    private HashMap<Long, Object> processJobsForProgram(File tmpf, String className, 
                                     Map<Long, Object> jobsForProgram) {
        
    ExecutorService executor = Executors.newFixedThreadPool(MAX_CONCURRENT_JOBS);
    HashMap<Long, Object> results = new HashMap<>();
    for (Map.Entry<Long, Object> jobEntry : jobsForProgram.entrySet()) {
            long jobId = jobEntry.getKey();
            Object input = jobEntry.getValue();

            executor.submit(() -> {
                try {
                    Object output = handle_slog_job(tmpf, input, className, jobId);
                    synchronized (results) {
                        results.put(jobId, output);
                        LOGGER.log(Level.INFO, "Pushed result for job ID: {0}", jobId);
                    }
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Failed to process job " + jobId, e);
                }
            });
        }
        executor.shutdown();
                while (!executor.isTerminated()) {
                    // Wait for all tasks for the current job group to finish
                }
        executor.shutdown();
        return results;
    }

    private Object handle_slog_job(File tmpf, Object programInput, String className, long job_id) throws IOException {
        Object result;
        try {
            result = JarLoader.loadJar(tmpf, className).apply(programInput);
        } catch (MalformedURLException | ClassNotFoundException | InstantiationException | IllegalAccessException
                | InvocationTargetException | SecurityException | NoSuchMethodException e) {
            LOGGER.log(Level.SEVERE, "Failed to run jar file.", e);
            throw new IOException("jar run failed", e);
        }
        return result;
    }
}