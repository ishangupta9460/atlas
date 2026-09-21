package com.atlas.backend.logging;

import static org.junit.jupiter.api.Assertions.*;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.read.ListAppender;
import com.atlas.backend.security.JwtService;
import com.atlas.backend.user.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.datasource.url=jdbc:h2:mem:ops001;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class RequestLoggingIntegrationTest {
    @Value("${local.server.port}") int port;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;
    final HttpClient client = HttpClient.newHttpClient();
    final ObjectMapper mapper = new ObjectMapper();
    final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    final ListAppender<ILoggingEvent> captured = new ListAppender<>() {
        @Override protected void append(ILoggingEvent event) {
            event.prepareForDeferredProcessing(); super.append(event);
        }
    };
    String token;

    @BeforeEach void setup() {
        token = jwt.generateToken(users.save(User.of("ops001@example.com", "private-password")));
        captured.start(); root.addAppender(captured);
    }
    @AfterEach void cleanup() {
        root.detachAppender(captured); captured.stop();
        jdbc.execute("ALTER TABLE events DROP CONSTRAINT IF EXISTS chk_ops001_reject");
        jdbc.update("DELETE FROM events"); jdbc.update("DELETE FROM goals"); jdbc.update("DELETE FROM users");
    }
    HttpResponse<String> send(String method, String path, String id, String body, boolean authenticated) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json");
        if (id != null) builder.header("X-Correlation-ID", id);
        if (authenticated) builder.header("Authorization", "Bearer " + token);
        return client.send(builder.method(method, HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    List<JsonNode> logs(String id) {
        // Use the actual application console encoder on emitted events, including their MDC snapshot.
        @SuppressWarnings("unchecked")
        var console = (ConsoleAppender<ILoggingEvent>) root.getAppender("CONSOLE");
        assertNotNull(console);
        return captured.list.stream().filter(e -> id.equals(e.getMDCPropertyMap().get("correlationId")))
            .map(e -> mapper.readTree(new String(console.getEncoder().encode(e), StandardCharsets.UTF_8))).toList();
    }
    JsonNode operation(List<JsonNode> rows, String operation) {
        return rows.stream().filter(row -> operation.equals(row.path("operation").asText())).findFirst().orElseThrow();
    }

    @Test void realRequestTracesEventTransactionAndDoesNotLogPrivateContent() throws Exception {
        var response = send("POST", "/goals?private=secret-query", "ops-success", "{\"title\":\"private-title\"}", true);
        assertEquals(201, response.statusCode());
        assertEquals("ops-success", response.headers().firstValue("X-Correlation-ID").orElseThrow());
        var rows = logs("ops-success");
        assertNotNull(operation(rows, "request.started"));
        assertEquals("goal.created", operation(rows, "event.append.requested").path("eventType").asText());
        assertEquals("committed", operation(rows, "event.transaction.completed").path("outcome").asText());
        var completed = operation(rows, "request.completed");
        assertEquals(201, completed.path("status").asInt());
        assertEquals("/goals", completed.path("route").asText());
        assertTrue(completed.path("durationMs").asLong() >= 0);
        for (var row : rows) {
            assertTrue(row.has("@timestamp")); assertTrue(row.has("level")); assertTrue(row.has("logger_name"));
            assertTrue(row.has("thread_name")); assertTrue(row.has("message"));
            assertFalse(row.toString().contains(token)); assertFalse(row.toString().contains("private-title"));
            assertFalse(row.toString().contains("secret-query")); assertFalse(row.toString().contains("private-password"));
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM events", Integer.class));
    }

    @Test void authenticationFailureAndHealthBothReceiveIndependentCorrelationIds() throws Exception {
        var denied = send("GET", "/goals/1", "ops-denied", "", false);
        assertEquals(401, denied.statusCode());
        assertEquals(401, operation(logs("ops-denied"), "request.completed").path("status").asInt());
        var health = send("GET", "/api/health", null, "", false);
        assertEquals(200, health.statusCode());
        String id = health.headers().firstValue("X-Correlation-ID").orElseThrow(); UUID.fromString(id);
        assertEquals(200, operation(logs(id), "request.completed").path("status").asInt());
        assertNotEquals("ops-denied", id);
    }

    @Test void rolledBackEventWriteIsLoggedAsRollbackNotCommit() throws Exception {
        jdbc.execute("ALTER TABLE events ADD CONSTRAINT chk_ops001_reject CHECK(type <> 'goal.created')");
        var response = send("POST", "/goals", "ops-rollback", "{\"title\":\"Rollback\"}", true);
        assertEquals(500, response.statusCode());
        assertEquals("rolled_back", operation(logs("ops-rollback"), "event.transaction.completed").path("outcome").asText());
        assertEquals(500, operation(logs("ops-rollback"), "request.completed").path("status").asInt());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM goals", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM events", Integer.class));
    }
}
