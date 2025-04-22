package smolrx;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

import smolrx.jobs.JobInfo;
import smolrx.jobs.JobType;
import smolrx.msg.BulkPush;
import smolrx.msg.InspectResult;
import smolrx.msg.JarRequest;
import smolrx.msg.JobRequest;
import smolrx.msg.Joblisting;
import smolrx.msg.ProtocolConfig;
import smolrx.msg.PushResult;
import smolrx.msg.SignOff;
import smolrx.msg.Termination;

public class ParallelCollectorClient implements Runnable {
    private static final int MAX_CONCURRENT_JOBS = Runtime.getRuntime().availableProcessors();
    private static final Logger LOGGER = Logger.getLogger("smolrx-collector-client");

    private final String hostName;
    private final int serverPort;
    private final int maxJobIds;
    private Long minJobId;
    private Long maxJobId;
    private final int minPriority;
    private final String roleKey;

    public ParallelCollectorClient(String hostName, int serverPort, int minPriority, int maxJobIds, String roleKey) {
        this.hostName = hostName;
        this.serverPort = serverPort;
        this.minPriority = minPriority;
        this.minJobId = null;
        this.maxJobId = null;
        this.maxJobIds = maxJobIds;
        this.roleKey = roleKey;
    }

    @Override
    public void run() {
        minJobId = Long.MAX_VALUE;
        maxJobId = Long.MIN_VALUE;
        ExecutorService executor = Executors.newFixedThreadPool(MAX_CONCURRENT_JOBS);
        Map<Long, Future<PushResult>> results = new HashMap<>();

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
            java.util.Objects.requireNonNull(jobListing, "Job listing cannot be null or empty.");
            if (jobListing.getJobIDs().isEmpty()) {
                throw new RuntimeException("Job listing is null or empty.");
            }

            // Get job type from first job (assuming all jobs are same type)
            JobType jobType = jobListing.getJobInfos().get(0).getType();
            
            Map<Long, File> programJarMap = new HashMap<>();
            Map<Long, JobInfo> jobInfoMap = new HashMap<>();
            for (int i = 0; i < jobListing.getJobIDs().size(); i++) {
                long jobId = jobListing.getJobIDs().get(i);
                JobInfo jobInfo = jobListing.getJobInfos().get(i);
                if (jobId < minJobId) {
                    minJobId = jobId;
                }
                if (jobId > maxJobId) {
                    maxJobId = jobId;
                }
                jobInfoMap.put(jobId, jobInfo);
            }

            // Download all required JAR files
            for (JobInfo jobInfo : jobListing.getJobInfos()) {
                Long programId = jobInfo.getProgramId();
                if (programJarMap.containsKey(programId)) continue;
                File jarFile = downloadJarFile(channel, programId);
                programJarMap.put(programId, jarFile);
            }

            // Process jobs only if they are REDUCER type
            if (jobType == JobType.COLLECT) {
                results = processJobs(executor, channel, jobInfoMap, programJarMap);
            }

            // Collect and send results
            HashMap<Long, Object> bulkResultMap = new HashMap<>();
            for (Map.Entry<Long, Future<PushResult>> entry : results.entrySet()) {
                try {
                    PushResult pr = entry.getValue().get();
                    bulkResultMap.put(pr.getJobId(), pr.getResultObject());
                    LOGGER.log(Level.INFO, "Collected result for job ID: {0}", pr.getJobId());
                } catch (InterruptedException | ExecutionException e) {
                    LOGGER.log(Level.SEVERE, "Failed to retrieve result for job ID: " + entry.getKey(), e);
                }
            }
            
            channel.sendObject(new BulkPush(bulkResultMap, roleKey));
            LOGGER.log(Level.INFO, "Results: {0}", bulkResultMap.toString());
            channel.sendObject(new SignOff());

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error during socket connection", e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error during collector processing", e);
        } finally {
            executor.shutdown();
        }
    }

    private File downloadJarFile(SecureChannel channel, Long programId) throws IOException, ClassNotFoundException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        LOGGER.log(Level.INFO, "Requesting jar for program ID {0}", programId);
        channel.sendObject(new JarRequest(minJobId, roleKey));
        
        File tmpf = File.createTempFile("smolrx", ".jar");
        tmpf.deleteOnExit();
        
        try (FileOutputStream fos = new FileOutputStream(tmpf)) {
            Object programInput = channel.readObject();
            if (programInput instanceof Termination termination) {
                throw new RuntimeException("Server terminated session: " + termination.getCause());
            }
            LOGGER.log(Level.INFO, "Received program input: {0}", programInput);
            channel.readStream(fos);
        }
        
        LOGGER.log(Level.INFO, "Downloaded JAR for program ID {0}", programId);
        return tmpf;
    }

    private Map<Long, Future<PushResult>> processJobs(ExecutorService executor, SecureChannel channel,
                                                    Map<Long, JobInfo> jobInfoMap, Map<Long, File> programJarMap) {
        Map<Long, Future<PushResult>> results = new HashMap<>();
        
        for (Map.Entry<Long, JobInfo> entry : jobInfoMap.entrySet()) {
            long jobId = entry.getKey();
            JobInfo jobInfo = entry.getValue();
            Long programId = jobInfo.getProgramId();
            String className = jobInfo.getProperties().getOrDefault("Xclass", "Main");
            File jarFile = programJarMap.get(programId);

            results.put(jobId, executor.submit(() -> 
                handleReducerJob(channel, jarFile, className, jobInfo, jobId)
            ));
        }
        
        executor.shutdown();
        while (!executor.isTerminated()) {}
        
        return results;
    }

    private PushResult handleReducerJob(SecureChannel channel, File tmpf, String className, 
                                      JobInfo jobInfo, long jobId) {
        Object input = 0;
        try {
            var reducer = JarLoader.loadJar(tmpf, className);
            for (Long dep : jobInfo.getPrerequisiteJobs()) {
                InspectResult request = new InspectResult(dep, jobId, roleKey, maxJobIds);
                synchronized (channel) {
                    channel.sendObject(request);
                    Object response = channel.readObject();
                    if (response instanceof Termination term) {
                        LOGGER.log(Level.SEVERE, "Server terminated session: {0}", term.getCause());
                    }
                    Object[] resultsArr = (Object[]) response;
                    LOGGER.log(Level.FINE, "Input: {0}, Results: {1}", new Object[]{dep, resultsArr[0]});
                    Object redInput = new Object[]{input, resultsArr[0]};
                    input = reducer.apply(redInput);
                }
            }
            return new PushResult(jobId, roleKey, input);
        } catch (IOException | ClassNotFoundException | IllegalAccessException | InstantiationException | NoSuchMethodException | SecurityException | InvocationTargetException | InvalidKeyException | BadPaddingException | IllegalBlockSizeException e) {
            LOGGER.log(Level.SEVERE, "Failed to process reducer job " + jobId, e);
            throw new RuntimeException(e);
        }
    }
}