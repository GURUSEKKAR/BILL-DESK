package com.invoice.modules.jobs.repository;

import com.invoice.modules.jobs.entity.Job;
import com.invoice.modules.jobs.entity.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {

    /**
     * Atomically claims the next PENDING job (SELECT ... FOR UPDATE SKIP LOCKED)
     * and sets it to SENDING. Returns the claimed job id, or empty if none available.
     */
    @Query(value = """
        UPDATE jobs SET status = 'SENDING', updated_at = now(), attempts = attempts + 1
        WHERE id = (
            SELECT id FROM jobs
            WHERE status = 'PENDING' AND next_run_at <= now()
            ORDER BY next_run_at
            FOR UPDATE SKIP LOCKED
            LIMIT 1
        )
        RETURNING id
        """, nativeQuery = true)
    Optional<UUID> claimNextJobId();

    /** Recovers SENDING jobs stuck longer than the timeout (worker crashed). */
    @Modifying
    @Query("UPDATE Job j SET j.status = com.invoice.modules.jobs.entity.JobStatus.PENDING, " +
           "j.updatedAt = :now " +
           "WHERE j.status = com.invoice.modules.jobs.entity.JobStatus.SENDING " +
           "AND j.updatedAt < :timeout")
    int recoverStuckJobs(@Param("timeout") Instant timeout,
                         @Param("now") Instant now);

    long countByStatus(JobStatus status);
}
