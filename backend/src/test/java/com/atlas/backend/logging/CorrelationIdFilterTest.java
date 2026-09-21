package com.atlas.backend.logging;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {
    final CorrelationIdFilter filter = new CorrelationIdFilter();
    @AfterEach void cleanup() { MDC.clear(); }

    @Test void propagatesValidatedIdAndRestoresExistingContext() throws Exception {
        MDC.put("correlationId", "outer"); MDC.put("unrelated", "keep");
        var request = new MockHttpServletRequest("GET", "/api/health");
        request.addHeader("X-Correlation-ID", "client-42");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> assertEquals("client-42", MDC.get("correlationId")));
        assertEquals("client-42", response.getHeader("X-Correlation-ID"));
        assertEquals("outer", MDC.get("correlationId"));
        assertEquals("keep", MDC.get("unrelated"));
    }

    @Test void missingIdsAreGeneratedAndNeverLeakToTheNextRequest() throws Exception {
        var first = new MockHttpServletResponse(); var second = new MockHttpServletResponse();
        for (var response : new MockHttpServletResponse[]{first, second}) {
            filter.doFilter(new MockHttpServletRequest("GET", "/api/health"), response, (req, res) ->
                assertEquals(response.getHeader("X-Correlation-ID"), MDC.get("correlationId")));
            UUID.fromString(response.getHeader("X-Correlation-ID"));
            assertNull(MDC.get("correlationId"));
        }
        assertNotEquals(first.getHeader("X-Correlation-ID"), second.getHeader("X-Correlation-ID"));
    }

    @Test void invalidAndAmbiguousHeadersAreReplaced() throws Exception {
        for (String value : new String[]{"", "a".repeat(65), "injected\r\nline", "space value", "one,two"}) {
            var request = new MockHttpServletRequest("GET", "/"); request.addHeader("X-Correlation-ID", value);
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, (req, res) -> {});
            UUID.fromString(response.getHeader("X-Correlation-ID"));
        }
        var request = new MockHttpServletRequest("GET", "/");
        request.addHeader("X-Correlation-ID", "first"); request.addHeader("X-Correlation-ID", "second");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {});
        UUID.fromString(response.getHeader("X-Correlation-ID"));
    }

    @Test void exceptionPropagatesAndContextIsCleaned() {
        var failure = new ServletException("not logged request content");
        assertSame(failure, assertThrows(ServletException.class, () -> filter.doFilter(
            new MockHttpServletRequest("GET", "/"), new MockHttpServletResponse(),
            (req, res) -> { throw failure; })));
        assertNull(MDC.get("correlationId"));
    }

    @Test void redispatchRetainsRequestIdentity() throws Exception {
        var request = new MockHttpServletRequest("GET", "/");
        var first = new MockHttpServletResponse(); var second = new MockHttpServletResponse();
        filter.doFilter(request, first, (req, res) -> {});
        request.setDispatcherType(jakarta.servlet.DispatcherType.ERROR);
        filter.doFilter(request, second, (req, res) -> {});
        assertEquals(first.getHeader("X-Correlation-ID"), second.getHeader("X-Correlation-ID"));
        assertNull(MDC.get("correlationId"));
    }
}
