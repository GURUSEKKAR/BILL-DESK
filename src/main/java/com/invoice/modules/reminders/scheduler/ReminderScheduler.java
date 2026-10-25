package com.invoice.modules.reminders.scheduler;

import com.invoice.modules.jobs.service.JobService;
import com.invoice.modules.reminders.entity.Reminder;
import com.invoice.modules.reminders.entity.ReminderStatus;
import com.invoice.modules.reminders.repository.ReminderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Runs every minute, finds due PENDING reminders, and enqueues a
 * {@code send_reminder} job for each one.
 * <p>
 * The job (not the scheduler) does the actual SENDING claim and send,
 * so the scheduler never blocks on network I/O.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderScheduler {

    private final ReminderRepository reminderRepository;
    private final JobService jobService;

    @Scheduled(fixedRateString = "${app.reminder.poll-interval-seconds:60}000")
    @Transactional
    public void poll() {
        List<Reminder> due = reminderRepository
            .findByStatusAndScheduledAtBefore(ReminderStatus.PENDING, Instant.now());

        if (due.isEmpty()) return;

        log.info("Enqueuing {} due reminders", due.size());
        for (Reminder reminder : due) {
            jobService.enqueue("send_reminder",
                Map.of("reminderId", reminder.getId().toString()));
        }
    }
}
