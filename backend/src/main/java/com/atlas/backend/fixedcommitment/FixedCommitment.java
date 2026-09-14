package com.atlas.backend.fixedcommitment;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/** A reservation, not a movable task. UTC local values map exactly to MySQL DATETIME(6). */
@Entity
@Table(name = "fixed_commitments")
public class FixedCommitment {
    public static final String FIXED = "fixed";
    public static final String MANUAL = "manual";
    public static final String SCREENSHOT_IMPORT = "screenshot_import";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;
    @Column(nullable = false, length = 255)
    private String title;
    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;
    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;
    @Column(nullable = false, length = 32, updatable = false)
    private String source;
    @Column(name = "recurrence_rule", columnDefinition = "TEXT")
    private String recurrenceRule;

    protected FixedCommitment() { }

    static FixedCommitment create(Long userId, String title, Instant start, Instant end, String source) {
        if (userId == null || !(MANUAL.equals(source) || SCREENSHOT_IMPORT.equals(source))) {
            throw new InvalidFixedCommitmentException("Owner and valid source are required");
        }
        FixedCommitment value = new FixedCommitment();
        value.userId = userId;
        value.source = source;
        value.update(title, start, end, false);
        return value;
    }

    void update(String title, Instant start, Instant end, boolean clearRecurrence) {
        if (title == null || title.isBlank() || title.length() > 255) {
            throw new InvalidFixedCommitmentException("title must contain text and be at most 255 characters");
        }
        LocalDateTime normalizedStart = normalize(start);
        LocalDateTime normalizedEnd = normalize(end);
        if (!normalizedEnd.isAfter(normalizedStart)) {
            throw new InvalidFixedCommitmentException("endTime must be after startTime at microsecond precision");
        }
        this.title = title;
        this.startTime = normalizedStart;
        this.endTime = normalizedEnd;
        if (clearRecurrence) recurrenceRule = null;
    }

    private static LocalDateTime normalize(Instant value) {
        if (value == null) throw new InvalidFixedCommitmentException("startTime and endTime are required");
        // Limit input before conversion to avoid overflowing LocalDateTime for internal callers.
        if (value.isBefore(Instant.parse("1000-01-01T00:00:00Z"))
                || !value.isBefore(Instant.parse("+10000-01-01T00:00:00Z"))) {
            throw new InvalidFixedCommitmentException("Times must be within UTC years 1000 through 9999");
        }
        return LocalDateTime.ofInstant(value.truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC);
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getTitle() { return title; }
    public Instant getStartTime() { return startTime.toInstant(ZoneOffset.UTC); }
    public Instant getEndTime() { return endTime.toInstant(ZoneOffset.UTC); }
    public String getSource() { return source; }
    public String getRecurrenceRule() { return recurrenceRule; }
    public String getFlexibilityTier() { return FIXED; }
}
