package com.atlas.backend;

import java.sql.Connection;
import javax.sql.DataSource;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness remains independent of downstream dependencies; readiness verifies
 * that the database can currently serve core application traffic.
 */
@RestController
public class HealthController {

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/api/health/ready")
    public ResponseEntity<Map<String, String>> readiness() {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(1)) {
                return ResponseEntity.ok(Map.of("status", "ready"));
            }
        } catch (Exception ignored) {
            // A probe must not reveal connection details or credentials.
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of("status", "unavailable"));
    }

}
