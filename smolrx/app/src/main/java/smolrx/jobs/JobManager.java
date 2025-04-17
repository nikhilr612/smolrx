package smolrx.jobs;

import java.io.Serializable;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeMap;

import smolrx.RXException;
import smolrx.msg.BulkInputs;
import smolrx.msg.BulkPush;
import smolrx.msg.InputRequest;
import smolrx.msg.InspectResult;
import smolrx.msg.JarRequest;
import smolrx.msg.JobRequest;
import smolrx.msg.Joblisting;
import smolrx.msg.PushResult;

/**
 * Manage scheduled jobs on the server.
 */
public class JobManager {
    /**
     * Map program IDs to their jar files.
     */
    HashMap<Long, String> jarMap;
    
    /**
     * Map JobIDs to job information.
     */
    TreeMap<Long, JobInfo> jobInfo;

    /**
     * Map JobIDs to Book-keeping information.
     */
    HashMap<Long, JobMetadata> jobMetas;

    /**
     * Map role keys to job types that clients with the key can take.
     */
    HashMap<String, JobType> keyMap;

    /**
     * Admit results for any slogger.
     */
    boolean admitAnySlogger;

    /**
     * Do not allow aggregation / collect jobs to complete before all pre-requisites have completed upto set redundance.
     */
    boolean forceRedundance;

    /**
     * Disallow bulk input requests for more jobs than this limit.
     */
    int bulkLimit;

    /**
     * Maximum number of results that can be pushed at once.
     */
    int bulkPushLimit;

    public boolean admitsAnySlogger() {
        return admitAnySlogger;
    }

    private void verifyPrerequisites(HashSet<Long> prerequisite_jobs) throws RXException {
        for (long job_id : prerequisite_jobs) {
            if (this.jobInfo.containsKey(job_id) && (forceRedundance || (this.jobMetas.get(job_id).completion_count == 0))) {
                throw new RXException("pre-requisite job with id: " + job_id + " is pending.");
            }
        }
    }

    /**
     * For a given key, return the JobType of jobs clients holding that key can take.
     * @param key The key.
     * @throws RXException when `key` has no associated JobType.
     * @return The type of jobs the client with `key` can take.
     */
    public JobType suitableJobType(String key) throws RXException {
        var result = this.keyMap.get(key);
        if (result != null) return result;
        if (this.admitAnySlogger) return JobType.SLOG;
        else throw new RXException("Unknown key", new IllegalArgumentException(key));
    }

    /**
     * List all jobs as per the request.
     * @param request The request for the jobs, specifying type.
     * @return The list of jobs.
     * @throws RXException If the request used an invalid role key.
     */
    public Joblisting listJobs(JobRequest request) throws RXException {
        var sortedMap = this.jobInfo.tailMap(request.getMinPriority());
        var it = sortedMap.entrySet().iterator();
        var jobIds = new ArrayList<Long>();
        var jobInfos = new ArrayList<JobInfo>();
        ArrayList<JobMetadata> jobMetas = null;

        var suitableType = this.suitableJobType(request.getRoleKey());

        if ((request.getRoleKey() != null) && (suitableType == JobType.AUDIT)) {
            jobMetas = new ArrayList<>();
            while (it.hasNext() && jobIds.size() < request.getLimit()) {
                var t = it.next();
                jobIds.add(t.getKey());
                jobInfos.add(t.getValue().maskedClone());
                jobMetas.add(this.jobMetas.get(t.getKey()));
            }

            return new Joblisting(jobIds, jobInfos, jobMetas);
        }
        
        while (it.hasNext() && jobIds.size() < request.getLimit()) {
            var t = it.next();
            if (suitableType != t.getValue().type) continue;
            jobIds.add(t.getKey());
            jobInfos.add(t.getValue().maskedClone());
        }

        return new Joblisting(jobIds, jobInfos);
    }

    /**
     * Fetch the pair of Jar path, and Job input data for the given jar request.
     * @param jarRequest The Jar Request
     * @return The Entry with Jar path as key, and Serializable input as value.
     * @throws RXException if request Job ID is invalid, role is incompatible, or pre-requisite jobs have not finished.
     */
    public AbstractMap.SimpleEntry<String,Serializable> fetchJobInfoPair(JarRequest jarRequest) throws RXException {
        var suitable = this.suitableJobType(jarRequest.getRoleKey());
        return _fetchJobInfoPairInner(jarRequest.getJobId(), suitable);
    }

    private AbstractMap.SimpleEntry<String,Serializable> _fetchJobInfoPairInner(long job_id, JobType suitable) throws RXException {
        var jobInfo = this.jobInfo.get(job_id);
        if (jobInfo == null) throw new RXException("No pending job with id: " + job_id);
        if (suitable != jobInfo.type) {
            throw new RXException("Client ill-suited to the job.");
        }
        verifyPrerequisites(jobInfo.prerequisite_jobs);
        var entry = new AbstractMap.SimpleEntry<>(this.jarMap.get(jobInfo.programId), jobInfo.jobData);
        return entry;
    }

    /**
     * Register the completion of this job. This DOES NOT save the result.
     * @param pushResult The result information.
     * @throws RXException If the role key is invalid, the job was already completed with required redundancy, or client is ill-suited to the job.
     */
    public void registerJobResult(PushResult pushResult) throws RXException {
        var jobtype = this.suitableJobType(pushResult.getRoleKey());
        synchronized(jobMetas) {
            _registerJobResultInner(pushResult.getJobId(), jobtype);
        }
    }

    /**
     * Register the completion of jobs in the bulk result. This DOES NOT save the result.
     * @param pushResult The Bulk result information.
     * @throws RXException If the role key is invalid, or a job was already completed with required redundancy, or client is ill-suited to a job.
     */
    public void registerJobResults(BulkPush pushResult) throws RXException {
        var jobtype = this.suitableJobType(pushResult.getRoleKey());
        if (pushResult.getJobs().size() > this.bulkPushLimit) {
            throw new RXException("Bulk push exceeds limit of " + this.bulkPushLimit);
        }
        synchronized(jobMetas) {
            for (var job_id : pushResult.getJobs()) {
                _registerJobResultInner(job_id, jobtype);
            }
        }
    }

    private void _registerJobResultInner(long job_id, JobType suitable) throws RXException {
        var jobMeta = this.jobMetas.get(job_id);
        if (jobMeta == null) throw new RXException("No scheduled job with id: " + job_id);
        var jobInfo = this.jobInfo.get(job_id);
        if (jobInfo == null) throw new RXException("Redundant result.");
        if (suitable != jobInfo.type) throw new RXException("Client ill-suited to the job.");
        jobMeta.completion_count += 1;
        if (jobInfo.redundancy_count == jobMeta.completion_count) {
            this.jobInfo.remove(job_id);
        }
    }

    public void validateInspection(InspectResult inspectResult) throws RXException {
        if (this.suitableJobType(inspectResult.getRoleKey()) != JobType.COLLECT) 
            throw new RXException("Client ill-suited to the job.");
        if (!this.jobInfo.containsKey(inspectResult.getParentJobId()))
            throw new RXException("No pending collect job with id: " + inspectResult.getParentJobId());
        var pJobInfo = this.jobInfo.get(inspectResult.getParentJobId());
        if (!pJobInfo.prerequisite_jobs.contains(inspectResult.getJobId()))
            throw new RXException("Cannot inspect results of job with id: " + inspectResult.getJobId());
    }

    public BulkInputs getJobInputs(InputRequest inputRequest) throws RXException {
        if (inputRequest.getSize() > this.bulkLimit) {
            throw new RXException("Bulk input request exceeds limit of " + this.bulkLimit);
        }
        var inputmap = new HashMap<Long, Object>();
        var fetchFails = 0;
        var suitable = this.suitableJobType(inputRequest.getRoleKey());

        // Let's get the range first.
        for (long i = inputRequest.getJobRangeStart(); i < inputRequest.getJobRangeEnd(); i++) {
            try {
                var pair = _fetchJobInfoPairInner(i, suitable);
                inputmap.put(i, pair.getValue());
            } catch (RXException e) {
                fetchFails += 1;
            }
        }

        // Now for the additional jobs.
        for (long job_id : inputRequest.getAdditionalJobs()) {
            try {
                var pair = _fetchJobInfoPairInner(job_id, suitable);
                inputmap.put(job_id, pair.getValue());
            } catch (RXException e) {
                fetchFails += 1;
            }
        }

        return new BulkInputs(inputmap, fetchFails);
    }

    public int getBulkRequestLimit() {
        return bulkLimit;
    }

    public int getBulkPushLimit() {
        return bulkPushLimit;
    }
}