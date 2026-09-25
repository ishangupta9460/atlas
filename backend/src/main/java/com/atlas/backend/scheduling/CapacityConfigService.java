package com.atlas.backend.scheduling;

import com.atlas.backend.event.Event;
import com.atlas.backend.event.EventRepository;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class CapacityConfigService {
    private final SchedulingConfigRepository repository;
    private final EventRepository events;
    private final ObjectMapper mapper = new ObjectMapper();

    public CapacityConfigService(SchedulingConfigRepository repository, EventRepository events) {
        this.repository = repository;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public CapacityCalculator.Policy get(Long owner) {
        return repository.capacity(owner).orElseGet(CapacityCalculator.Policy::defaults);
    }

    @Transactional
    public CapacityCalculator.Policy put(Long owner, CapacityCalculator.Policy policy) {
        if (policy == null) throw new IllegalArgumentException("Provide a capacity policy");
        repository.lockOwner(owner);
        var before = get(owner);
        if (!before.equals(policy)) {
            repository.ensureConfig(owner);
            repository.saveCapacity(owner, policy);
            events.appendAndFlush(Event.forEntity("scheduling_config", owner, "capacity.updated", "user", null,
                    mapper.writeValueAsString(Map.of("before", before, "after", policy))));
        }
        return policy;
    }
}
