package com.atlas.backend.commitment;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** Use UTC wall values for DATETIME without depending on JVM/session timezone. */
@Converter
public class UtcInstantConverter implements AttributeConverter<Instant, LocalDateTime> {
    public LocalDateTime convertToDatabaseColumn(Instant value) { return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC); }
    public Instant convertToEntityAttribute(LocalDateTime value) { return value == null ? null : value.toInstant(ZoneOffset.UTC); }
}
