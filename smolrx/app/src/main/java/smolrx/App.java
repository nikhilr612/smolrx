package smolrx;

import java.io.IOException;

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
            var job = new JobBuilder(i, 1, JobType.SLOG)
                .setJobData(i) // input is the number to test
                .setRedundancyCount(1)
                .setProperty("Xclass", "bfcarm.Test")
                .build();
            jmBuilder.addJob(i, job);
        }

        // Add reducers
        for (int i = 0; i < 10; i++) {
            var jb = new JobBuilder(1000+i, 1, JobType.COLLECT);
            for (int j = 1; j <= 100; j++) {
                jb.addPrerequisiteJob(100*i+j);
            }
            var job = jb.setProperty("Xclass", "bfcarm.Count").build();
            jmBuilder.addJob(1000+i, job);
        }

        return jmBuilder.build();
    }

    public static void main(String[] args) {
        System.out.println(args[0]);
        if (args[0].equals("server")) {
            try {
                FileStorage fStorage = new FileStorage("./testdir/");
                Server s = new Server(6444, 5, setupJobs(), fStorage);
                s.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (args[0].equals("client")) {
            SimpleClient c = new SimpleClient("localhost", 6444, 0, "public");
            c.run();
        } else {
            TimidClient tc = new TimidClient("localhost", 6444, 0, "public");
            tc.run();
        }
    }
}
