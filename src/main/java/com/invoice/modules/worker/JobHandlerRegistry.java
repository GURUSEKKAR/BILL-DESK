package com.invoice.modules.worker;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects all {@link JobHandler} beans and maps them by type string.
 * The worker looks up the handler by job.getType() at runtime.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobHandlerRegistry {

    private final List<JobHandler> handlers;
    private final Map<String, JobHandler> registry = new HashMap<>();

    @PostConstruct
    void init() {
        for (JobHandler h : handlers) {
            registry.put(h.type(), h);
            log.info("Registered job handler: {}", h.type());
        }
    }

    public JobHandler get(String type) {
        return registry.get(type);
    }
}
