package com.atlas.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HealthControllerTest {

    @Test
    void livenessDoesNotDependOnTheDatabase() {
        DataSource dataSource = mock(DataSource.class);

        var response = new HealthController(dataSource).health();

        assertEquals("ok", response.get("status"));
        verifyNoInteractions(dataSource);
    }

    @Test
    void readinessIsReadyWhenTheDatabaseConnectionIsValid() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(1)).thenReturn(true);

        var response = new HealthController(dataSource).readiness();

        assertEquals(200, response.getStatusCode().value());
        assertEquals("ready", response.getBody().get("status"));
    }

    @Test
    void readinessIsUnavailableWithoutLeakingDatabaseFailureDetails() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new IllegalStateException("jdbc:secret://database"));

        var response = new HealthController(dataSource).readiness();

        assertEquals(503, response.getStatusCode().value());
        assertEquals("unavailable", response.getBody().get("status"));
        assertFalse(response.getBody().toString().contains("secret"));
    }

    @Test
    void readinessRejectsNoSensitiveStateWhenConnectionIsInvalid() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(1)).thenReturn(false);

        var response = new HealthController(dataSource).readiness();

        assertEquals(503, response.getStatusCode().value());
        assertEquals("unavailable", response.getBody().get("status"));
    }
}
