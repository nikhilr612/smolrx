package smolrx;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import smolrx.jobs.JobBuilder;
import smolrx.jobs.JobManager;
import smolrx.jobs.JobManagerBuilder;
import smolrx.jobs.JobType;
import smolrx.storage.FileStorage;

public class App {

    // TODO: Move to test.

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
                jb.addPrerequisiteJob(100*i+j);
            }
            var job = jb.setProperty("Xclass", "bfcarm.Count").relax().build();
            jmBuilder.addJob(1000+i+1, job);
        }

        return jmBuilder.build();
    }

    public static void main(String[] args) {
        System.out.println(args[0]);
        switch (args[0]) {
            case "server" -> {
                try {
                    FileStorage fStorage = FileStorage.create("./testdir/");
                    Server s = new Server(6444, 5, setupJobs(), fStorage);
                    s.start();
                } catch (IOException e) {
                    System.err.println("Failed to start server:");
                    Logger
                        .getLogger(App.class.getName())
                        .log(Level.SEVERE, "Error starting server", e);
                }
            }
            case "client" -> {
                SimpleClient c = new SimpleClient("localhost", 6444, 0, args[1]);
                c.run();
            }
            default -> {
                TimidClient tc = new TimidClient("localhost", 6444, 0, "public");
                tc.run();
            }
        }
    }
}
