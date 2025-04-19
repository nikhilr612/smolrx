package smolrx;

import java.io.File;
import java.io.IOException;

import smolrx.jobs.JobBuilder;
import smolrx.jobs.JobManager;
import smolrx.jobs.JobManagerBuilder;
import smolrx.jobs.JobType;
import smolrx.storage.FileStorage;

public class BulkTest {
    private static JobManager setupJobs() {
        // TODO: Serialize JobManager
        var jmBuilder = new JobManagerBuilder()
            .allowAnySlogger()
            .setBulkPushLimit(100)
            .setBulkReqLimit(100)
            .withKey("slog-key", JobType.SLOG)
            .withKey("collector-key", JobType.COLLECT)
            .addJar(1, "./testjars/bfcarm.jar");

        // Create 1000 SLOG jobs
        for (int i = 1; i <= 1000; i++) {
            jmBuilder.addJob(i, new JobBuilder(i, 1, JobType.SLOG)
                .setJobData(i)
                .setRedundancyCount(2)
                .setProperty("Xclass", "bfcarm.Test")
                .relax()
                .build());
        }

        // Create 10 COLLECT jobs each depending on 100 SLOG jobs
        for (int i = 0; i < 10; i++) {
            var collectJobId = 1000 + i;
            var builder = new JobBuilder(collectJobId, 1, JobType.COLLECT)
                .setProperty("Xclass", "bfcarm.Count");
            
            for (int j = 1; j <= 100; j++) {
                builder.addPrerequisiteJob(i * 100 + j);
            }
            
            jmBuilder.addJob(collectJobId, builder.build());
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
            case "test" -> startBullshitting();
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

    @SuppressWarnings("CallToPrintStackTrace")
    private static void startServer() {
        try {
            FileStorage fStorage = new FileStorage("./jobs-storage/");
            Server server = new Server(6444, 10, setupJobs(), fStorage);
            server.start();
        } catch (IOException e) {
            System.err.println("Failed to start server:");
            e.printStackTrace();
        }
    }

    private static void startWorkerClient() {
        new Thread(new SimpleClient("localhost", 6444, 0, "slog-key")).start();
        System.out.println("Started worker client with SLOG role");
    }

    private static void startCollectorClient() {
        // new Thread(new CollectorTest("localhost", 6444, 0, "collector-key")).start();
        System.out.println("Started collector client with COLLECT role");
    }
    private static void startBulkClient(){
        new Thread(new ParallelBulkClient("localhost", 6444, 0, 100, "slog-key")).start();
    }
    private static void startBullshitting() {
        for (int i = 0; i < 100; i++) {
            new Thread(new SimpleClient("localhost", 6444, 0, "slog-key")).start();
        }
    // new Thread(new CollectorTest("localhost", 6444, 0, "collector-key")).start();   
        System.out.println("Started 100 worker clients with SLOG role and 1 collector clients with COLLECT role");
    }
}