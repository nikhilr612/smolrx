package smolrx.msg;

import java.util.ArrayList;
import java.util.Optional;

import smolrx.jobs.JobInfo;
import smolrx.jobs.JobMetadata;

public final class Joblisting extends ServerMessage {

    private static final long serialVersionUID = 5432123456789L;

    ArrayList<Long> jobIDs;
    ArrayList<JobInfo> jobInfos;
    ArrayList<JobMetadata> jobMeta;

    /**
     * Create a new job listing with the specified jobs and their meta data.
     * @param jobIDs
     * @param jobInfos
     * @param meta
     */
    public Joblisting(ArrayList<Long> jobIDs, ArrayList<JobInfo> jobInfos, ArrayList<JobMetadata> meta) {
        this.jobIDs = jobIDs;
        this.jobInfos = jobInfos;
        this.jobMeta = meta;
    }

    /**
     * Create a new job listing with the specified jobs and without their meta data.
     * @param jobIDs
     * @param jobInfos
     */
    public Joblisting(ArrayList<Long> jobIDs, ArrayList<JobInfo> jobInfos) {
        this.jobIDs = jobIDs;
        this.jobInfos = jobInfos;
        this.jobMeta = null;
    }

    public ArrayList<Long> getJobIDs() {
        return jobIDs;
    }

    public ArrayList<JobInfo> getJobInfos() {
        return jobInfos;
    }

    public Optional<ArrayList<JobMetadata>> getJobMeta() {
        return jobMeta == null ? Optional.empty() : Optional.of(jobMeta);
    }

    public boolean hasMeta() {
        return jobMeta != null;
    }
}
