package com.atlas.backend.fixedcommitment;

import com.atlas.backend.event.Event;
import com.atlas.backend.event.EventRepository;
import com.atlas.backend.user.User;
import com.atlas.backend.user.UserRepository;
import com.atlas.backend.security.JwtService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=${dom006.api.url:jdbc:h2:mem:dom006_api;MODE=MySQL;DB_CLOSE_DELAY=-1}",
        "spring.datasource.username=${dom006.mysql.user:sa}",
        "spring.datasource.password=${dom006.mysql.password:}",
        "spring.datasource.driver-class-name=${dom006.mysql.driver:org.h2.Driver}"})
@ActiveProfiles("test")
class FixedCommitmentIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired FixedCommitmentRepository repository;
    @Autowired EventRepository events;
    @Autowired JdbcTemplate jdbc;
    final ObjectMapper mapper = new ObjectMapper();
    MockMvc mvc;
    String token;
    Long ownerId;
    static final String BODY = "{\"title\":\"Meeting\",\"startTime\":\"2026-09-14T15:30:00.123456789+05:30\",\"endTime\":\"2026-09-14T11:00:00Z\",\"recurrenceRule\":null}";

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        User user = users.save(User.of("fixed-owner@example.com", "not-used"));
        ownerId = user.getId();
        token = jwt.generateToken(user);
    }
    @AfterEach void cleanup() { events.deleteAll(); repository.deleteAll(); users.deleteAll(); }

    @Test void crudPersistsUtcSnapshotsAndDeletionHistory() throws Exception {
        long id = create(BODY);
        var originalEvent = events.findAllByEntityTypeAndEntityIdOrderByTimestamp("fixed_commitment", id).get(0);
        String originalPayload = originalEvent.getPayload();
        mvc.perform(auth(get("/fixed-commitments/{id}", id))).andExpect(status().isOk())
                .andExpect(jsonPath("$.source", is("manual"))).andExpect(jsonPath("$.flexibilityTier", is("fixed")))
                .andExpect(jsonPath("$.startTime", is("2026-09-14T10:00:00.123456Z")))
                .andExpect(jsonPath("$.recurrenceRule").value(org.hamcrest.Matchers.nullValue()));
        assertEquals(ownerId, repository.findById(id).orElseThrow().getUserId());
        mvc.perform(auth(patch("/fixed-commitments/{id}", id)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Changed \\\"quoted\\\" title\",\"endTime\":\"2026-09-14T12:00:00Z\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.endTime", is("2026-09-14T12:00:00Z")));
        mvc.perform(auth(patch("/fixed-commitments/{id}", id)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        assertEquals(2, events.count());
        mvc.perform(auth(delete("/fixed-commitments/{id}", id))).andExpect(status().isNoContent()).andExpect(content().string(""));
        assertFalse(repository.existsById(id));
        var history = events.findAllByEntityTypeAndEntityIdOrderByTimestamp("fixed_commitment", id);
        assertEquals(3, history.size());
        assertEquals(originalPayload, events.findById(originalEvent.getId()).orElseThrow().getPayload());
        Event deleted = history.stream().filter(e -> e.getType().equals("fixed_commitment.deleted")).findFirst().orElseThrow();
        assertEquals("Changed \"quoted\" title", mapper.readTree(deleted.getPayload()).path("before").path("title").asText());
        for (Event event : history) { assertEquals("user", event.getActor()); assertNull(event.getTaskId()); }
        mvc.perform(auth(get("/fixed-commitments/{id}", id))).andExpect(status().isNotFound());
        mvc.perform(auth(delete("/fixed-commitments/{id}", id))).andExpect(status().isNotFound());
        assertEquals(3, events.count());
    }

    @Test void overlapsAndRepeatedPostRemainDistinctEvenWithSameHeader() throws Exception {
        long first = create(BODY);
        long second = create(BODY);
        assertNotEquals(first, second);
        mvc.perform(auth(patch("/fixed-commitments/{id}", second)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"startTime\":\"2026-09-14T10:30:00Z\"}")).andExpect(status().isOk());
        assertEquals(2, repository.count());
        assertEquals(3, events.count());
    }

    @Test void rejectsInvalidAndForgedInputWithoutWrites() throws Exception {
        for (String body : new String[]{"{}", "null", "[]", "{", BODY.replace("Meeting", " "),
                BODY.replace("Meeting", "x".repeat(256)), BODY.replace("\"Meeting\"", "123"),
                BODY.replace("2026-09-14T11:00:00Z", "2026-09-14T09:00:00Z"),
                BODY.replace("2026-09-14T11:00:00Z", "2026-09-14T11:00:00"),
                BODY.replace("\"recurrenceRule\":null", "\"recurrenceRule\":\"FREQ=DAILY\"")}) {
            mvc.perform(auth(post("/fixed-commitments")).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error_code", is("VALIDATION_ERROR")));
        }
        for (String field : new String[]{"userId", "user_id", "source", "flexibilityTier", "unexpected"}) {
            mvc.perform(auth(post("/fixed-commitments")).contentType(MediaType.APPLICATION_JSON)
                            .content(BODY.substring(0, BODY.length() - 1) + ",\"" + field + "\":\"screenshot_import\"}"))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(0, repository.count()); assertEquals(0, events.count());
    }

    @Test void patchDistinguishesOmissionNullAndInvalidFinalInterval() throws Exception {
        long id = create(BODY.replace(",\"recurrenceRule\":null", ""));
        for (String body : new String[]{"{\"title\":null}", "{\"startTime\":null}", "{\"endTime\":null}",
                "{\"recurrenceRule\":\"\"}", "{\"recurrenceRule\":{}}", "{\"source\":\"manual\"}",
                "{\"flexibilityTier\":\"fixed\"}", "{\"userId\":1}",
                "{\"startTime\":\"2026-09-14T12:00:00Z\"}"}) {
            mvc.perform(auth(patch("/fixed-commitments/{id}", id)).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(auth(patch("/fixed-commitments/{id}", id)).contentType(MediaType.APPLICATION_JSON).content("{\"recurrenceRule\":null}"))
                .andExpect(status().isOk());
        assertEquals(1, events.count());
        assertEquals("Meeting", repository.findById(id).orElseThrow().getTitle());
    }

    @Test void everyRouteRequiresJwtAndForeignIdsMatchMissingIds() throws Exception {
        long id = create(BODY);
        for (String bearer : new String[]{"", "Bearer invalid"}) {
            for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[]{post("/fixed-commitments").content(BODY),
                    get("/fixed-commitments/{id}", id), patch("/fixed-commitments/{id}", id).content("{}"), delete("/fixed-commitments/{id}", id)}) {
                mvc.perform(request.header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isUnauthorized());
            }
        }
        token = jwt.generateToken(users.save(User.of("fixed-other@example.com", "not-used")));
        for (String method : new String[]{"GET", "PATCH", "DELETE"}) {
            String foreign = mvc.perform(auth(request(org.springframework.http.HttpMethod.valueOf(method), "/fixed-commitments/" + id))
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
            String missing = mvc.perform(auth(request(org.springframework.http.HttpMethod.valueOf(method), "/fixed-commitments/999999"))
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
            assertEquals(mapper.readTree(missing), mapper.readTree(foreign));
        }
        assertEquals(1, repository.count()); assertEquals(1, events.count());
    }

    @Test void storedImportProvenanceSurvivesManualUpdate() throws Exception {
        long id = create(BODY);
        jdbc.update("UPDATE fixed_commitments SET source = 'screenshot_import', recurrence_rule = 'opaque-future-value' WHERE id = ?", id);
        mvc.perform(auth(patch("/fixed-commitments/{id}", id)).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Reviewed\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.source", is("screenshot_import")))
                .andExpect(jsonPath("$.recurrenceRule", is("opaque-future-value")));
        mvc.perform(auth(patch("/fixed-commitments/{id}", id)).contentType(MediaType.APPLICATION_JSON).content("{\"recurrenceRule\":null}"))
                .andExpect(status().isOk());
        assertNull(repository.findById(id).orElseThrow().getRecurrenceRule());
    }

    @Test void malformedIdsAndSubMicrosecondIntervalsReturnSanitizedValidationErrors() throws Exception {
        mvc.perform(auth(get("/fixed-commitments/not-a-number")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error_code", is("VALIDATION_ERROR")));
        mvc.perform(auth(post("/fixed-commitments")).contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("2026-09-14T11:00:00Z", "2026-09-14T10:00:00.123456999Z")))
                .andExpect(status().isBadRequest());
        mvc.perform(auth(post("/fixed-commitments")).contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("2026-09-14T11:00:00Z", "+10000-01-01T00:00:00Z")))
                .andExpect(status().isBadRequest());
        assertEquals(0, repository.count()); assertEquals(0, events.count());
    }

    long create(String body) throws Exception {
        return json(mvc.perform(auth(post("/fixed-commitments")).header("Idempotency-Key", "same-key")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn()).path("id").asLong();
    }
    MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request) { return request.header("Authorization", "Bearer " + token); }
    JsonNode json(MvcResult result) throws Exception { return mapper.readTree(result.getResponse().getContentAsString()); }
}
