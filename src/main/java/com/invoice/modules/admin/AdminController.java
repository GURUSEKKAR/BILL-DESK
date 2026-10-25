package com.invoice.modules.admin;

import com.invoice.modules.invoices.job.OverdueJob;
import com.invoice.modules.jobs.entity.Job;
import com.invoice.modules.jobs.entity.JobStatus;
import com.invoice.modules.jobs.repository.JobRepository;
import com.invoice.modules.jobs.service.JobService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Admin / observability endpoints.
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final OverdueJob overdueJob;
    private final JobRepository jobRepository;
    private final JobService jobService;

    /** Manually trigger overdue detection. */
    @PostMapping("/run-overdue")
    public Map<String, Object> runOverdue() {
        int count = overdueJob.runOnce();
        return Map.of("markedOverdue", count);
    }

    /** Job queue stats. */
    @GetMapping("/jobs/stats")
    public Map<String, Long> jobStats() {
        return Map.of(
            "pending", jobRepository.countByStatus(JobStatus.PENDING),
            "sending", jobRepository.countByStatus(JobStatus.SENDING),
            "done",    jobRepository.countByStatus(JobStatus.DONE),
            "failed",  jobRepository.countByStatus(JobStatus.FAILED)
        );
    }

    /** Replay a FAILED job by resetting it to PENDING with a fresh attempt budget. */
    @PostMapping("/jobs/{id}/replay")
    public Map<String, String> replayJob(@PathVariable UUID id) {
        Job job = jobRepository.findById(id)
            .orElseThrow(() -> new com.invoice.common.exception.ApiException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Job not found"));
        if (job.getStatus() != JobStatus.FAILED) {
            throw com.invoice.common.exception.ApiException.conflict("Only FAILED jobs can be replayed");
        }
        job.setStatus(JobStatus.PENDING);
        job.setAttempts(0);
        job.setNextRunAt(java.time.Instant.now());
        jobRepository.save(job);
        return Map.of("status", "replayed", "jobId", id.toString());
    }

    /** List all FAILED jobs for the admin dashboard. */
    @GetMapping("/jobs/failed")
    public java.util.List<Job> failedJobs() {
        return jobRepository.findAll().stream()
            .filter(j -> j.getStatus() == JobStatus.FAILED)
            .sorted(java.util.Comparator.comparing(Job::getUpdatedAt).reversed())
            .toList();
    }
}
