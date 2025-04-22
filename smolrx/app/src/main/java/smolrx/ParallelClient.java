package smolrx;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

import smolrx.jobs.JobInfo;
import smolrx.jobs.JobType;
import smolrx.msg.BulkInputs;
import smolrx.msg.BulkPush;
import smolrx.msg.InputRequest;
import smolrx.msg.InspectResult;
import smolrx.msg.JarRequest;
import smolrx.msg.JobRequest;
import smolrx.msg.Joblisting;
import smolrx.msg.ProtocolConfig;
import smolrx.msg.PushResult;
import smolrx.msg.SignOff;
import smolrx.msg.Termination;

public class ParallelClient implements Runnable {
    private static final int MAX_CONCURRENT_JOBS = Runtime.getRuntime().availableProcessors();
    private static final Logger LOGGER = Logger.getLogger("smolrx-parallel-client");

    private final String hostName;
    private final int serverPort;
    private final int maxJobIds;
    private Long minJobId;
    private Long maxJobId;
    private final int minPriority;
    private final String roleKey;

    public ParallelClient(String hostName, int serverPort, int minPriority, int maxJobIds, String roleKey) {
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
        CompletionService<Object> completionService = new ExecutorCompletionService<>(executor);

        try (Socket socket = new Socket(hostName, serverPort)) {
            SecureChannel channel = SecureChannel.openServerChannel(socket);
            initializeConnection(channel);
            
            Joblisting jobListing = requestJobListing(channel);
            JobType jobType = determineJobType(jobListing);
            
            if (null == jobType) {
                throw new RuntimeException("Unsupported job type: " + jobType);
            } else switch (jobType) {
                case SLOG -> processSlogJobs(channel, completionService, jobListing);
                case COLLECT -> processCollectorJobs(channel, completionService, jobListing);
                default -> throw new RuntimeException("Unsupported job type: " + jobType);
            }
            
            signOff(channel);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error during socket connection", e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error during processing", e);
        } finally {
            executor.shutdown();
        }
    }

    private void initializeConnection(SecureChannel channel) throws IOException, ClassNotFoundException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        Object config = channel.readObject();
        if (!(config instanceof ProtocolConfig)) {
            throw new RuntimeException("Invalid protocol config object received.");
        }
    }

    private Joblisting requestJobListing(SecureChannel channel) throws IOException, ClassNotFoundException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        LOGGER.info("Requesting job listing...");
        channel.sendObject(new JobRequest(minPriority, maxJobIds, roleKey));
        
        Object response = channel.readObject();
        if (response instanceof Termination term) {
            throw new RuntimeException("Server terminated session: " + term.getCause());
        }
        return (Joblisting) response;
    }

    private JobType determineJobType(Joblisting jobListing) {
        if (jobListing == null || jobListing.getJobInfos().isEmpty()) {
            throw new RuntimeException("Job listing is null or empty");
        }
        return jobListing.getJobInfos().get(0).getType();
    }

    private void processSlogJobs(SecureChannel channel, CompletionService<Object> completionService, 
                               Joblisting jobListing) throws Exception {
        determineJobIdRange(jobListing);
        BulkInputs bulkInputs = requestBulkInputs(channel);
        Map<Long, Map<Long, Object>> programToJobs = groupJobsByProgram(jobListing, bulkInputs);
        Map<Long, JobInfo> jobInfoMap = createJobInfoMap(jobListing);
        
        for (Map.Entry<Long, Map<Long, Object>> entry : programToJobs.entrySet()) {
            Long programId = entry.getKey();
            Map<Long, Object> jobsForProgram = entry.getValue();
            
            File jarFile = downloadJarFile(channel, programId);
            String className = determineClassName(jobsForProgram, jobInfoMap);
            logJarContents(jarFile);
            
            HashMap<Long, Object> results = processSlogJobs(completionService, jarFile, className, jobsForProgram);
            sendResults(channel, results);
            jarFile.delete(); // Clean up temp file
        }
    }
    private void processCollectorJobs(SecureChannel channel, CompletionService<Object> completionService,
                                    Joblisting jobListing) throws Exception {
        determineJobIdRange(jobListing);
        Map<Long, JobInfo> jobInfoMap = createJobInfoMap(jobListing);
        Map<Long, File> programJarMap = downloadProgramJars(channel, jobListing);
        
        HashMap<Long, Object> results = processCollectorJobs(completionService, channel, jobInfoMap, programJarMap);
        LOGGER.log(Level.INFO, "Collected results: {0}", results);
        sendResults(channel, results);
        
        // Clean up temp files
        programJarMap.values().forEach(File::delete);
    }

    // ===== SLOG-specific methods =====
    private void determineJobIdRange(Joblisting jobListing) {
        for (long jobId : jobListing.getJobIDs()) {
            if (jobId < minJobId) minJobId = jobId;
            if (jobId > maxJobId) maxJobId = jobId;
        }
    }

    private BulkInputs requestBulkInputs(SecureChannel channel) throws IOException, ClassNotFoundException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        LOGGER.log(Level.INFO, "Requesting bulk inputs in range: {0} to {1}", new Object[]{minJobId, maxJobId});
        channel.sendObject(new InputRequest(roleKey, minJobId, maxJobId + 1, new ArrayList<>()));
        
        Object response = channel.readObject();
        if (response instanceof Termination term) {
            throw new RuntimeException("Server terminated session: " + term.getCause());
        }
        
        BulkInputs bulkInputs = (BulkInputs) response;
        Objects.requireNonNull(bulkInputs, "BulkInputs must not be null");
        LOGGER.log(Level.INFO, "Received {0} bulk inputs.", bulkInputs.getInputs().size());
        return bulkInputs;
    }

    private Map<Long, Map<Long, Object>> groupJobsByProgram(Joblisting jobListing, BulkInputs bulkInputs) {
        Map<Long, Map<Long, Object>> programToJobs = new HashMap<>();
        List<Long> jobIds = jobListing.getJobIDs();
        List<JobInfo> jobInfos = jobListing.getJobInfos();
        
        for (int i = 0; i < jobIds.size(); i++) {
            Long jobId = jobIds.get(i);
            if (jobId < minJobId || jobId > maxJobId) continue;
            
            JobInfo info = jobInfos.get(i);
            Object input = bulkInputs.getInputs().get(jobId);
            programToJobs.computeIfAbsent(info.getProgramId(), k -> new HashMap<>()).put(jobId, input);
        }
        return programToJobs;
    }

    private HashMap<Long, Object> processSlogJobs(CompletionService<Object> completionService, 
                                                File jarFile, String className, 
                                                Map<Long, Object> jobs) {
        HashMap<Long, Object> results = new HashMap<>();
        int submittedTasks = 0;
        
        // Submit all tasks
        for (Map.Entry<Long, Object> entry : jobs.entrySet()) {
            final long jobId = entry.getKey();
            final Object input = entry.getValue();
            
            completionService.submit(() -> {
                try {
                    return new Object[]{jobId, JarLoader.loadJar(jarFile, className).apply(input)};
                } catch (ClassNotFoundException | IllegalAccessException | InstantiationException | NoSuchMethodException | SecurityException | InvocationTargetException | MalformedURLException e) {
                    LOGGER.log(Level.SEVERE, "Failed to process SLOG job " + jobId, e);
                    return new Object[]{jobId, null};
                }
            });
            submittedTasks++;
        }
        
        // Collect results as they complete
        for (int i = 0; i < submittedTasks; i++) {
            try {
                Future<Object> future = completionService.take();
                Object[] result = (Object[]) future.get();
                if (result[1] != null) {
                    results.put((Long)result[0], result[1]);
                    LOGGER.log(Level.INFO, "Processed SLOG job ID: {0}", result[0]);
                }
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.log(Level.SEVERE, "Error waiting for job completion", e);
            }
        }
        
        return results;
    }

    // ===== COLLECTOR-specific methods =====
    private Map<Long, File> downloadProgramJars(SecureChannel channel, Joblisting jobListing) 
            throws IOException, ClassNotFoundException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        Map<Long, File> programJarMap = new HashMap<>();
        for (JobInfo jobInfo : jobListing.getJobInfos()) {
            Long programId = jobInfo.getProgramId();
            if (!programJarMap.containsKey(programId)) {
                programJarMap.put(programId, downloadJarFile(channel, programId));
            }
        }
        return programJarMap;
    }

    private HashMap<Long, Object> processCollectorJobs(CompletionService<Object> completionService,
                                                     SecureChannel channel, Map<Long, JobInfo> jobInfoMap,
                                                     Map<Long, File> programJarMap) {
        HashMap<Long, Object> results = new HashMap<>();
        int submittedTasks = 0;
        
        // Submit all collector jobs
        for (Map.Entry<Long, JobInfo> entry : jobInfoMap.entrySet()) {
            final long jobId = entry.getKey();
            final JobInfo jobInfo = entry.getValue();
            final File jarFile = programJarMap.get(jobInfo.getProgramId());
            final String className = jobInfo.getProperties().getOrDefault("Xclass", "Main");
            
            completionService.submit(() -> {
                try {
                    Object result = handleCollectorJob(channel, jarFile, className, jobInfo, jobId);
                    return new Object[]{jobId, result};
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Failed to process collector job " + jobId, e);
                    return new Object[]{jobId, null};
                }
            });
            submittedTasks++;
        }
        
        // Collect results as they complete
        for (int i = 0; i < submittedTasks; i++) {
            try {
                Future<Object> future = completionService.take();
                Object[] result = (Object[]) future.get();
                if (result[1] != null) {
                    results.put((Long)result[0], result[1]);
                    LOGGER.log(Level.INFO, "Processed COLLECTOR job ID: {0}", result[0]);
                }
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.log(Level.SEVERE, "Error waiting for job completion", e);
            }
        }
        
        return results;
    }

    private PushResult handleCollectorJob(SecureChannel channel, File jarFile, String className,
                                        JobInfo jobInfo, long jobId) throws Exception {
        Object input = 0;
        try {
            var reducer = JarLoader.loadJar(jarFile, className);
            for (Long dep : jobInfo.getPrerequisiteJobs()) {
                input = processDependency(channel, reducer, input, dep, jobId);
            }
            return new PushResult(jobId, roleKey, input);
        } catch (IOException | ClassNotFoundException | IllegalAccessException | 
                InstantiationException | NoSuchMethodException | SecurityException | 
                InvocationTargetException | 
                BadPaddingException | IllegalBlockSizeException e) {
            LOGGER.log(Level.SEVERE, "Failed to process collector job " + jobId, e);
            throw new RuntimeException(e);
        }
    }

    private Object processDependency(SecureChannel channel, Function<Object, Object> reducer, Object input,
        Long dep, long jobId) throws Exception {
        synchronized (channel) {
        channel.sendObject(new InspectResult(dep, jobId, roleKey, maxJobIds));
        Object response = channel.readObject();
        if (response instanceof Termination term) {
        throw new RuntimeException("Server terminated: " + term.getCause());
        }

        Object[] resultsArr = (Object[]) response;
        LOGGER.log(Level.FINE, "Input: {0}, Results: {1}", new Object[]{dep, resultsArr[0]});
        Object redInput = new Object[]{input, resultsArr[0]};
        return reducer.apply(redInput);
        }   
    }

    // ===== Common utility methods =====
    private Map<Long, JobInfo> createJobInfoMap(Joblisting jobListing) {
        Map<Long, JobInfo> jobInfoMap = new HashMap<>();
        for (int i = 0; i < jobListing.getJobIDs().size(); i++) {
            long jobId = jobListing.getJobIDs().get(i);
            jobInfoMap.put(jobId, jobListing.getJobInfos().get(i));
        }
        return jobInfoMap;
    }

    private File downloadJarFile(SecureChannel channel, Long programId) throws IOException, ClassNotFoundException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        LOGGER.log(Level.INFO, "Downloading JAR for program ID {0}", programId);
        channel.sendObject(new JarRequest(minJobId, roleKey));
        
        File jarFile = File.createTempFile("smolrx", ".jar");
        jarFile.deleteOnExit();
        
        try (FileOutputStream fos = new FileOutputStream(jarFile)) {
            Object programInput = channel.readObject();
            if (programInput instanceof Termination term) {
                throw new RuntimeException("Server terminated: " + term.getCause());
            }
            channel.readStream(fos);
        }
        return jarFile;
    }

    private String determineClassName(Map<Long, Object> jobs, Map<Long, JobInfo> jobInfoMap) {
        return jobs.keySet().stream()
                .map(jobInfoMap::get)
                .filter(Objects::nonNull)
                .map(info -> info.getProperties().getOrDefault("Xclass", "Main"))
                .findFirst().orElse("Main");
    }

    private void logJarContents(File jarFile) {
        try (JarFile jar = new JarFile(jarFile)) {
            LOGGER.info("JAR contents:");
            jar.stream().forEach(entry -> 
                LOGGER.log(Level.FINE, "{0} ({1} bytes)", new Object[]{entry.getName(), entry.getSize()})
            );
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to read JAR contents", e);
        }
    }

    private void sendResults(SecureChannel channel, HashMap<Long, Object> results) throws IOException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        channel.sendObject(new BulkPush(results, roleKey));
        LOGGER.log(Level.INFO, "Sent results for {0} jobs", results.size());
    }

    private void signOff(SecureChannel channel) throws IOException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        channel.sendObject(new SignOff());
        LOGGER.info("Session completed successfully");
    }
}