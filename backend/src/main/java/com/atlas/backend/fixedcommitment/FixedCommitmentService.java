package com.atlas.backend.fixedcommitment;

import com.atlas.backend.event.Event;
import com.atlas.backend.event.EventRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FixedCommitmentService {
    private final FixedCommitmentRepository repository;
    private final EventRepository events;
    private final ObjectMapper payloadMapper = new ObjectMapper();

    public FixedCommitmentService(FixedCommitmentRepository repository, EventRepository events) {
        this.repository = repository;
        this.events = events;
    }

    @Transactional
    public FixedCommitmentResponse create(Long userId, CreateFixedCommitmentRequest request) {
        FixedCommitmentInput.recurrence(request.recurrenceRule());
        FixedCommitment value = repository.save(FixedCommitment.create(userId, FixedCommitmentInput.title(request.title()),
                FixedCommitmentInput.time(request.startTime(), "startTime"), FixedCommitmentInput.time(request.endTime(), "endTime"),
                FixedCommitment.MANUAL));
        writeEvent(value, "created", Map.of("after", snapshot(value)));
        return FixedCommitmentResponse.from(value);
    }

    @Transactional(readOnly = true)
    public FixedCommitmentResponse get(Long userId, Long id) {
        return FixedCommitmentResponse.from(repository.findByIdAndUserId(id, userId).orElseThrow(FixedCommitmentNotFoundException::new));
    }

    @Transactional
    public FixedCommitmentResponse update(Long userId, Long id, UpdateFixedCommitmentRequest request) {
        FixedCommitment value = findLocked(userId, id);
        FixedCommitmentInput.recurrence(request.recurrenceRule());
        Map<String, Object> before = snapshot(value);
        value.update(request.title() == null ? value.getTitle() : FixedCommitmentInput.title(request.title()),
                request.startTime() == null ? value.getStartTime() : FixedCommitmentInput.time(request.startTime(), "startTime"),
                request.endTime() == null ? value.getEndTime() : FixedCommitmentInput.time(request.endTime(), "endTime"),
                request.recurrenceRule() != null);
        Map<String, Object> after = snapshot(value);
        if (!before.equals(after)) writeEvent(value, "updated", Map.of("before", before, "after", after));
        return FixedCommitmentResponse.from(value);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        FixedCommitment value = findLocked(userId, id);
        Map<String, Object> before = snapshot(value);
        repository.delete(value);
        writeEvent(value, "deleted", Map.of("before", before));
    }

    private FixedCommitment findLocked(Long userId, Long id) {
        return repository.lockOwned(id, userId).orElseThrow(FixedCommitmentNotFoundException::new);
    }

    private Map<String, Object> snapshot(FixedCommitment value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", value.getId());
        result.put("userId", value.getUserId());
        result.put("title", value.getTitle());
        result.put("startTime", value.getStartTime().toString());
        result.put("endTime", value.getEndTime().toString());
        result.put("source", value.getSource());
        result.put("recurrenceRule", value.getRecurrenceRule());
        result.put("flexibilityTier", value.getFlexibilityTier());
        return result;
    }

    private void writeEvent(FixedCommitment value, String action, Map<String, Object> payload) {
        repository.flush();
        try {
            events.appendAndFlush(Event.forEntity("fixed_commitment", value.getId(), "fixed_commitment." + action,
                    "user", null, payloadMapper.writeValueAsString(payload)));
        } catch (JacksonException ex) {
            throw new IllegalStateException("Could not serialize Fixed Commitment event", ex);
        }
    }
}
