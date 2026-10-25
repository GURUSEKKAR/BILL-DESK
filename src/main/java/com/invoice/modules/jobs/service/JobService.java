package com.invoice.modules.jobs.service;

import com.invoice.modules.jobs.entity.Job;
import com.invoice.modules.jobs.entity.JobStatus;
import com.invoice.modules.jobs.repository.JobRepository;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Enqueue, claim, complete, and fail background jobs.
 * <p>
 * The queue is the {@code jobs} table. Claiming uses a single atomic UPDATE
 * with {@code FOR UPDATE SKIP LOCKED} so concurrent workers never collide.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final ObjectMapper objectMapper;

    /** Enqueue a new job with a JSON payload. */
    @Transactional
    public Job enqueue(String type, Map<String, Object> payload) {
        Job job = new Job();
        job.setType(type);
        try {
            job.setPayload(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize job payload", e);
        }
        job.setStatus(JobStatus.PENDING);
        return jobRepository.save(job);
    }

    /**
     * Atomically claim the next due job (PENDING → SENDING).
     * Returns empty if no job is available.
     */
    @Transactional
    public Optional<Job> claimNextJob() {
        return jobRepository.claimNextJobId()
            .flatMap(id -> jobRepository.findById(id));
    }

    /** Mark a job as successfully completed. */
    @Transactional
    public void complete(Job job) {
        job.setStatus(JobStatus.DONE);
        jobRepository.save(job);
        log.info("Job {} type={} completed", job.getId(), job.getType());
    }

    /**
     * Handle a job failure. Permanent errors go straight to FAILED.
     * Transient errors schedule a retry with exponential backoff + jitter.
     * If max attempts is exceeded, the job goes to FAILED.
     */
    @Transactional
    public void handleFailure(Job job, Throwable error) {
        boolean permanent = error instanceof PermanentJobException;

        if (permanent || job.getAttempts() >= job.getMaxAttempts()) {
            job.setStatus(JobStatus.FAILED);
            job.setLastError(truncate(error.getMessage(), 2000));
            jobRepository.save(job);
            log.warn("Job {} type={} FAILED: {}",
                job.getId(), job.getType(), error.getMessage());
            return;
        }

        // Exponential backoff: 2^attempts seconds + random jitter (0-1s)
        long baseSeconds = (long) Math.pow(2, job.getAttempts());
        long jitterMillis = ThreadLocalRandom.current().nextLong(0, 1000);
        long delayMillis = baseSeconds * 1000 + jitterMillis;

        job.setNextRunAt(Instant.now().plusMillis(delayMillis));
        job.setLastError(truncate(error.getMessage(), 2000));
        // status stays SENDING → will be reset to PENDING by recovery or re-claim
        job.setStatus(JobStatus.PENDING);
        jobRepository.save(job);
        log.warn("Job {} type={} attempt {}/{} failed, retrying in {}ms: {}",
            job.getId(), job.getType(), job.getAttempts(),
            job.getMaxAttempts(), delayMillis, error.getMessage());
    }

    /** Recover SENDING jobs stuck longer than the timeout (worker crashed). */
    @Transactional
    public int recoverStuckJobs(java.time.Duration timeout) {
        Instant now = Instant.now();
        return jobRepository.recoverStuckJobs(now.minus(timeout), now);
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
