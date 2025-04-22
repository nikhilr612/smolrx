package smolrx.msg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import smolrx.jobs.JobInfo;
import smolrx.jobs.JobMetadata;

public final class Joblisting extends ServerMessage {

    private static final long serialVersionUID = 5432123456789L;

    List<Long> jobIDs;
    List<JobInfo> jobInfos;
    List<JobMetadata> jobMeta;

    /**
     * Create a new job listing with the specified jobs and their meta data.
     * @param jobIDs
     * @param jobInfos
     * @param meta
     */
    public Joblisting(ArrayList<Long> jobIDs, ArrayList<JobInfo> jobInfos, ArrayList<JobMetadata> meta) {
        this.jobIDs = Collections.unmodifiableList(jobIDs);
        this.jobInfos = Collections.unmodifiableList(jobInfos);
        this.jobMeta = Collections.unmodifiableList(meta);
    }

    /**
     * Create a new job listing with the specified jobs and without their meta data.
     * @param jobIDs
     * @param jobInfos
     */
    public Joblisting(ArrayList<Long> jobIDs, ArrayList<JobInfo> jobInfos) {
        this.jobIDs = Collections.unmodifiableList(jobIDs);
        this.jobInfos = Collections.unmodifiableList(jobInfos);
        this.jobMeta = null;
    }

    public List<Long> getJobIDs() {
        return jobIDs;
    }

    public List<JobInfo> getJobInfos() {
        return jobInfos;
    }

    public Optional<List<JobMetadata>> getJobMeta() {
        return jobMeta == null ? Optional.empty() : Optional.of(jobMeta);
    }

    public boolean hasMeta() {
        return jobMeta != null;
    }

    public void printTable() {
        System.out.println("Job ID\tJob Type\tLink");
        for (int i = 0; i < jobIDs.size(); i++) {
            System.out.println(jobIDs.get(i) + "\t" + jobInfos.get(i).getType() + "\t" + jobInfos.get(i).getLink());
        }
    }
}
