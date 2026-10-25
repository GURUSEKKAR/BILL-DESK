package com.invoice.modules.worker;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.invoice.modules.jobs.entity.Job;
import com.invoice.modules.jobs.entity.JobStatus;
import com.invoice.modules.jobs.service.JobService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-process job worker.
 * <p>
 * Polls the {@code jobs} table every {@code app.worker.poll-interval-ms}
 * milliseconds and dispatches each job to its registered {@link JobHandler}.
 * <p>
 * In a production deployment this process would run as a separate JVM
 * (e.g. {@code java -jar app.jar --spring.main.web-application-type=none}).
 * For this build both the API and the worker run in the same JVM.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkerEngine {

    private final JobService jobService;
    private final JobHandlerRegistry registry;
    private final ObjectMapper objectMapper;

    @Value("${app.worker.poll-interval-ms:2000}")
    private long pollIntervalMs;

    @Value("${app.worker.stuck-job-timeout-minutes:5}")
    private long stuckTimeoutMinutes;

    @Value("${app.worker.enabled:true}")
    private boolean enabled;

    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "job-worker");
            t.setDaemon(true);
            return t;
        });

    private final AtomicBoolean running = new AtomicBoolean(false);

    @PostConstruct
    void start() {
        if (!enabled) {
            log.info("Worker disabled by configuration");
            return;
        }
        log.info("Starting worker (poll interval {}ms)", pollIntervalMs);
        scheduler.scheduleWithFixedDelay(
            this::poll,
            1000,           // initial delay
            pollIntervalMs, // poll interval
            TimeUnit.MILLISECONDS
        );
    }

    @PreDestroy
    void stop() {
        log.info("Stopping worker");
        scheduler.shutdown();
        try {
            scheduler.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void poll() {
        if (!running.compareAndSet(false, true)) return; // already processing
        try {
            // Recover any SENDING rows stuck too long (worker crash recovery)
            int recovered = jobService.recoverStuckJobs(Duration.ofMinutes(stuckTimeoutMinutes));
            if (recovered > 0) {
                log.warn("Recovered {} stuck jobs", recovered);
            }

            // Process jobs until the queue is empty
            Optional<Job> claimed;
            while ((claimed = jobService.claimNextJob()).isPresent()) {
                process(claimed.get());
            }
        } finally {
            running.set(false);
        }
    }

    private void process(Job job) {
        log.debug("Processing job {} type={} attempt={}", job.getId(), job.getType(), job.getAttempts());
        JobHandler handler = registry.get(job.getType());
        if (handler == null) {
            log.error("No handler for job type '{}' — marking failed", job.getType());
            jobService.handleFailure(job,
                new RuntimeException("Unknown job type: " + job.getType()));
            return;
        }

        try {
            Map<String, Object> payload = objectMapper.readValue(
                job.getPayload(), new TypeReference<>() {});
            handler.handle(payload);
            jobService.complete(job);
        } catch (Exception e) {
            log.warn("Job {} failed: {}", job.getId(), e.getMessage());
            jobService.handleFailure(job, e);
        }
    }

    /** Enqueue count of currently PENDING jobs (for admin/observability). */
    public long pendingCount() {
        return jobService.claimNextJob().map(j -> {
            // put it back
            j.setStatus(JobStatus.PENDING);
            return 1L;
        }).orElse(0L);
    }
}
