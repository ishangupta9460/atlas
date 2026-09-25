package com.atlas.backend.scheduling;

import com.atlas.backend.event.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class WorkingHoursService {
    private final SchedulingConfigRepository repository;
    private final EventRepository events;
    private final ObjectMapper mapper = new ObjectMapper();
    public WorkingHoursService(SchedulingConfigRepository repository, EventRepository events) {
        this.repository = repository; this.events = events;
    }
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public WorkingHours get(Long owner) { return repository.workingHours(owner).orElse(null); }

    @Transactional
    public WorkingHours put(Long owner, WorkingHours hours) {
        if (hours == null) throw new IllegalArgumentException("Provide working hours");
        repository.lockOwner(owner);
        var before = repository.workingHours(owner);
        if (before.isEmpty() || !before.get().equals(hours)) {
            repository.ensureConfig(owner);
            repository.saveWorkingHours(owner, hours);
            var payload = new java.util.LinkedHashMap<String, Object>();
            payload.put("before", before.orElse(null)); payload.put("after", hours);
            events.appendAndFlush(Event.forEntity("scheduling_config", owner, "working_hours.updated", "user", null,
                    mapper.writeValueAsString(payload)));
        }
        return hours;
    }
}
