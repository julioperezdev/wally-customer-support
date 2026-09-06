package com.wally.customersupport.shared.infrastructure.observability;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/** Adds request correlation and emits one sanitized completion event per HTTP request. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestObservabilityFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_MDC_KEY = "requestId";

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestObservabilityFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        String previousRequestId = MDC.get(REQUEST_ID_MDC_KEY);
        long startedAt = System.nanoTime();
        Throwable failure = null;

        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException exception) {
            failure = exception;
            throw exception;
        } finally {
            int httpStatus = effectiveStatus(response.getStatus(), failure);
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("requestId", requestId);
            fields.put("httpMethod", request.getMethod());
            fields.put("route", route(request));
            fields.put("httpStatus", httpStatus);
            fields.put("outcome", outcome(httpStatus, failure));
            fields.put("durationMs", elapsedMillis(startedAt));
            if (failure != null) {
                fields.put("errorType", failure.getClass().getSimpleName());
            }

            if (failure != null || httpStatus >= 400) {
                StructuredEventLog.warn(LOGGER, "HTTP_REQUEST_COMPLETED", fields);
            } else {
                StructuredEventLog.info(LOGGER, "HTTP_REQUEST_COMPLETED", fields);
            }

            if (previousRequestId == null) {
                MDC.remove(REQUEST_ID_MDC_KEY);
            } else {
                MDC.put(REQUEST_ID_MDC_KEY, previousRequestId);
            }
        }
    }

    private static String route(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pattern instanceof String value && !value.isBlank() ? value : "unmatched";
    }

    private static String outcome(int status, Throwable failure) {
        if (failure != null) {
            return "ERROR";
        }
        if (status >= 500) {
            return "SERVER_ERROR";
        }
        if (status >= 400) {
            return "CLIENT_ERROR";
        }
        return "SUCCESS";
    }

    private static int effectiveStatus(int responseStatus, Throwable failure) {
        return failure != null && responseStatus < 400 ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR : responseStatus;
    }

    private static long elapsedMillis(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }
}
