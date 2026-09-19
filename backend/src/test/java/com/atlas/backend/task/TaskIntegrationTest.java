package com.atlas.backend.task;

import com.atlas.backend.event.EventRepository;
import com.atlas.backend.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class TaskIntegrationTest {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private EventRepository eventRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    void createTaskReturnsCreatedReadyTaskAndRecordsEvent() throws Exception {
        String token = registerAndLogin("creator@example.com");

        MvcResult result = mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Write review\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title", is("Write review")))
                .andExpect(jsonPath("$.status", is("ready")))
                .andReturn();

        long taskId = json(result).get("id").asLong();
        assertEquals(1, eventRepository.findAllByTaskId(taskId).size());
        assertEquals("task.created", eventRepository.findAllByTaskId(taskId).get(0).getType());
    }

    @Test
    void createRejectsBlankTitleAndAllTaskRoutesRequireAuthentication() throws Exception {
        String token = registerAndLogin("validation@example.com");

        mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code", is("VALIDATION_ERROR")));

        mockMvc.perform(get("/api/tasks/today"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void todayReturnsOnlyCallersNonCompletedTasks() throws Exception {
        String alice = registerAndLogin("alice@example.com");
        String bob = registerAndLogin("bob@example.com");
        long readyTask = createTask(alice, "Ready task");
        long completedTask = createTask(alice, "Completed task");
        createTask(bob, "Bob task");
        start(alice, completedTask);
        finish(alice, completedTask);

        mockMvc.perform(get("/api/tasks/today").header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].id", is((int) readyTask)))
                .andExpect(jsonPath("$[0].status", is("ready")));
    }

    @Test
    void startTransitionsReadyTaskAndRecordsEvent() throws Exception {
        String token = registerAndLogin("starter@example.com");
        long taskId = createTask(token, "Start task");

        start(token, taskId)
                .andExpect(jsonPath("$.status", is("in_progress")))
                .andExpect(jsonPath("$.startedAt").isNotEmpty());

        Task task = taskRepository.findById(taskId).orElseThrow();
        assertEquals(Task.IN_PROGRESS, task.getStatus());
        assertNotNull(task.getStartedAt());
        assertEquals("task.started", eventRepository.findAllByTaskId(taskId).get(1).getType());
    }

    @Test
    void finishTransitionsInProgressTaskAndRecordsEvent() throws Exception {
        String token = registerAndLogin("finisher@example.com");
        long taskId = createTask(token, "Finish task");
        start(token, taskId);

        finish(token, taskId)
                .andExpect(jsonPath("$.status", is("completed")))
                .andExpect(jsonPath("$.finishedAt").isNotEmpty());

        Task task = taskRepository.findById(taskId).orElseThrow();
        assertEquals(Task.COMPLETED, task.getStatus());
        assertNotNull(task.getFinishedAt());
        assertEquals("task.finished", eventRepository.findAllByTaskId(taskId).get(2).getType());
    }

    @Test
    void startRejectsInProgressAndCompletedTasks() throws Exception {
        String token = registerAndLogin("invalid-start@example.com");
        long taskId = createTask(token, "Task");
        start(token, taskId);

        mockMvc.perform(post("/api/tasks/{id}/start", taskId).header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code", is("INVALID_TASK_STATE")));

        finish(token, taskId);
        mockMvc.perform(post("/api/tasks/{id}/start", taskId).header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code", is("INVALID_TASK_STATE")));
    }

    @Test
    void finishRejectsReadyTask() throws Exception {
        String token = registerAndLogin("invalid-finish@example.com");
        long taskId = createTask(token, "Task");

        mockMvc.perform(post("/api/tasks/{id}/finish", taskId).header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code", is("INVALID_TASK_STATE")));
    }

    @Test
    void userCannotViewStartOrFinishAnotherUsersTask() throws Exception {
        String alice = registerAndLogin("owner@example.com");
        String bob = registerAndLogin("other@example.com");
        long aliceTask = createTask(alice, "Private task");

        mockMvc.perform(get("/api/tasks/today").header("Authorization", "Bearer " + bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));
        mockMvc.perform(post("/api/tasks/{id}/start", aliceTask).header("Authorization", "Bearer " + bob))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code", is("TASK_NOT_FOUND")));
        mockMvc.perform(post("/api/tasks/{id}/finish", aliceTask).header("Authorization", "Bearer " + bob))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code", is("TASK_NOT_FOUND")));
    }

    private String registerAndLogin(String email) throws Exception {
        String request = "{\"email\":\"" + email + "\",\"password\":\"securePassword123\"}";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated());
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andReturn();
        return json(login).get("token").asText();
    }

    private long createTask(String token, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTaskRequest(title))))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result).get("id").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions start(String token, long taskId) throws Exception {
        return mockMvc.perform(post("/api/tasks/{id}/start", taskId)
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions finish(String token, long taskId) throws Exception {
        return mockMvc.perform(post("/api/tasks/{id}/finish", taskId)
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void cleanDatabase() {
        eventRepository.deleteAll();
        taskRepository.deleteAll();
        userRepository.deleteAll();
    }
}
