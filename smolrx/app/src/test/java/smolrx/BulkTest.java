package smolrx;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import smolrx.jobs.JobBuilder;
import smolrx.jobs.JobManager;
import smolrx.jobs.JobManagerBuilder;
import smolrx.jobs.JobType;
import smolrx.storage.FileStorage;

public class BulkTest {
    private static JobManager setupJobs() {
        var jmBuilder = new JobManagerBuilder()
            .allowAnySlogger()
            .setBulkPushLimit(100)
            .setBulkReqLimit(100)
            .withKey("private", JobType.COLLECT)
            .addJar(1, "./testjars/bfcarm.jar");

        // Add mappers.
        for (int i = 1; i <= 1000; i++) {
            var job = JobBuilder.newInstance(i, 1, JobType.SLOG)
                .setJobData(i) // input is the number to test
                .setRedundancyCount(1)
                .setProperty("Xclass", "bfcarm.Test")
                .build();
            jmBuilder.addJob(i, job);
        }

        // Add reducers
        for (int i = 0; i < 10; i++) {
            var jb = JobBuilder.newInstance(1000+i+1, 1, JobType.COLLECT);
            for (int j = 1; j <= 100; j++) {
                jb.relax().addPrerequisiteJob(100*i+j);
            }
            var job = jb.setProperty("Xclass", "bfcarm.Count").build();
            jmBuilder.addJob(1000+i+1, job);
        }

        return jmBuilder.build();
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("""
                               Usage: java App <mode> [options]
                               Modes:
                                 server    - Start server node
                                 client    - Start worker client
                                 collector - Start collector client""");
            return;
        }

        switch (args[0].toLowerCase()) {
            case "server" -> startServer();
            case "client" -> startWorkerClient();
            case "collector" -> startCollectorClient();
            case "bulk-client" -> startBulkClient();
            case "bulk-collector" -> startBulkCollector();
            case "test" -> startTest();
            case "reset" -> {
                File dir = new File("./jobs-storage/");
                try {
                    for (File file : dir.listFiles()) file.delete();
                    System.out.println("Storage reset.");
                } catch (NullPointerException e) {
                    System.out.println("There are no files to remove.");
                }
            }
            default -> System.err.println("Invalid mode: " + args[0]);
        }
    }

    private static void startServer() {
        try {
            FileStorage fStorage = FileStorage.create("./jobs-storage/");
            Server server = new Server(6444, 10, setupJobs(), fStorage);
            server.start();
        } catch (IOException e) {
            System.err.println("Failed to start server:");
            Logger
                .getLogger(BulkTest.class.getName())
                .log(Level.SEVERE, "Error starting server", e);
        }
    }

    private static void startBulkCollector() {
        new Thread(new ParallelClient("localhost", 6444, 1000, 10, "private")).start();
        System.out.println("Started bulk collector client with COLLECT role");
    }

    private static void startWorkerClient() {
        new Thread(new SimpleClient("localhost", 6444, 0, "slog-key")).start();
        System.out.println("Started worker client with SLOG role");
    }

    private static void startCollectorClient() {
        new Thread(new SimpleClient("localhost", 6444, 1000, "private")).start();
        System.out.println("Started collector client with COLLECT role");
    }
    private static void startBulkClient(){
        new Thread(new ParallelClient("localhost", 6444, 0, 100, "slog-key")).start();
    }
    private static void startTest() {
        for (int i = 0; i < 10; i++) {
            ParallelClient parallelClient = new ParallelClient("localhost", 6444, 0, 100, "slog-key");
            parallelClient.run();
        }
            new Thread(new ParallelClient("localhost", 6444, 1000, 10, "private")).start();
            System.out.println("Started 10 worker clients with SLOG role and 1 collector clients with COLLECT role");
    }
}