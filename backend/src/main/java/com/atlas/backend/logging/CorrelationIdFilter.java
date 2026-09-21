package com.atlas.backend.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/** Boundary context for the existing synchronous servlet/service/persistence path. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";
    private static final String ATTRIBUTE = CorrelationIdFilter.class.getName() + ".id";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "TRACE");
    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

    @Override protected boolean shouldNotFilterErrorDispatch() { return false; }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String id = (String) request.getAttribute(ATTRIBUTE);
        if (id == null) {
            var headers = Collections.list(request.getHeaders(HEADER));
            id = headers.size() == 1 && SAFE_ID.matcher(headers.get(0)).matches()
                ? headers.get(0) : UUID.randomUUID().toString();
            request.setAttribute(ATTRIBUTE, id);
        }
        String previous = MDC.get(MDC_KEY);
        MDC.put(MDC_KEY, id);
        response.setHeader(HEADER, id);
        long started = System.nanoTime();
        boolean failed = false;
        String method = METHODS.contains(request.getMethod()) ? request.getMethod() : "OTHER";
        try {
            log.atInfo().addKeyValue("operation", "request.started").addKeyValue("method", method)
                .log("Request started");
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException | Error ex) {
            failed = true;
            throw ex;
        } finally {
            try {
                Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                log.atInfo().addKeyValue("operation", "request.completed").addKeyValue("method", method)
                    .addKeyValue("route", route == null ? "unmapped" : route.toString())
                    .addKeyValue("status", failed ? 500 : response.getStatus())
                    .addKeyValue("durationMs", (System.nanoTime() - started) / 1_000_000)
                    .log("Request completed");
            } finally {
                if (previous == null) MDC.remove(MDC_KEY); else MDC.put(MDC_KEY, previous);
            }
        }
    }
}
