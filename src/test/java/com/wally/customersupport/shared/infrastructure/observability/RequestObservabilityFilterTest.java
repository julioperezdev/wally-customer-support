package com.wally.customersupport.shared.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestObservabilityFilterTest {

    private final RequestObservabilityFilter filter = new RequestObservabilityFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void addsRequestIdToResponseAndCleansMdcAfterSuccessfulRequest() throws Exception {
        MockHttpServletRequest request = request("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) -> {
            assertThat(MDC.get(RequestObservabilityFilter.REQUEST_ID_MDC_KEY)).isNotBlank();
            ((HttpServletResponse) servletResponse).setStatus(HttpServletResponse.SC_OK);
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(RequestObservabilityFilter.REQUEST_ID_HEADER))
                .isNotBlank()
                .matches("[0-9a-f-]{36}");
        assertThat(MDC.get(RequestObservabilityFilter.REQUEST_ID_MDC_KEY)).isNull();
    }

    @Test
    void preservesRequestIdWhenAFilterIsNested() throws Exception {
        MDC.put(RequestObservabilityFilter.REQUEST_ID_MDC_KEY, "parent-request");
        MockHttpServletRequest request = request("POST", "/webhook/telegram");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                assertThat(MDC.get(RequestObservabilityFilter.REQUEST_ID_MDC_KEY))
                        .isNotEqualTo("parent-request"));

        assertThat(MDC.get(RequestObservabilityFilter.REQUEST_ID_MDC_KEY)).isEqualTo("parent-request");
    }

    @Test
    void emitsCompletionContextAndRethrowsApplicationFailure() {
        MockHttpServletRequest request = request("POST", "/webhook/telegram");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RuntimeException failure = new IllegalStateException("synthetic failure");

        assertThatThrownBy(() -> filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            throw failure;
        })).isSameAs(failure);

        assertThat(response.getHeader(RequestObservabilityFilter.REQUEST_ID_HEADER)).isNotBlank();
        assertThat(MDC.get(RequestObservabilityFilter.REQUEST_ID_MDC_KEY)).isNull();
    }

    private static MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setAttribute(
                org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
                uri.startsWith("/webhook/") ? "/webhook/{channel}" : uri);
        return request;
    }
}
