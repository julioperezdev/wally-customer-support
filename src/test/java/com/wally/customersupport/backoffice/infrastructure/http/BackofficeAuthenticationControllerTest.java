package com.wally.customersupport.backoffice.infrastructure.http;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.wally.customersupport.backoffice.application.port.out.BackofficeIdentityProvider;
import com.wally.customersupport.backoffice.application.service.BackofficeAuthenticationService;
import com.wally.customersupport.backoffice.application.service.BackofficeAuthenticationException;
import com.wally.customersupport.backoffice.infrastructure.config.BackofficeAuthenticationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BackofficeAuthenticationControllerTest {

    private BackofficeAuthenticationService authenticationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authenticationService = mock(BackofficeAuthenticationService.class);
        BackofficeAuthenticationProperties properties = new BackofficeAuthenticationProperties(
                true, true, "None", "wcs_access", "wcs_refresh", 3600,
                "http://localhost:5173",
                new BackofficeAuthenticationProperties.Cognito("us-east-1", "client-id"));
        mockMvc = MockMvcBuilders.standaloneSetup(
                new BackofficeAuthenticationController(authenticationService, properties)).build();
    }

    @Test
    void setsHttpOnlySessionCookiesAfterLogin() throws Exception {
        when(authenticationService.login("operator@example.com", "secret"))
                .thenReturn(new BackofficeIdentityProvider.AuthenticationResult(
                        "access-token", "refresh-token", 3600, null));

        MvcResult result = mockMvc.perform(post("/internal/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"operator@example.com\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHENTICATED"))
                .andReturn();

        List<String> cookies = result.getResponse().getHeaders("Set-Cookie");
        org.assertj.core.api.Assertions.assertThat(cookies)
                .anyMatch(cookie -> cookie.contains("wcs_access=access-token") && cookie.contains("HttpOnly"))
                .anyMatch(cookie -> cookie.contains("wcs_refresh=refresh-token") && cookie.contains("HttpOnly"));

        verify(authenticationService).login("operator@example.com", "secret");
    }

    @Test
    void refreshUsesTheHttpOnlyRefreshCookie() throws Exception {
        when(authenticationService.refresh("refresh-token"))
                .thenReturn(new BackofficeIdentityProvider.AuthenticationResult(
                        "new-access-token", null, 900, null));

        mockMvc.perform(post("/internal/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("wcs_refresh", "refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFRESHED"))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString(
                        "wcs_access=new-access-token")));

        verify(authenticationService).refresh("refresh-token");
    }

    @Test
    void doesNotExposeTokensInTheResponseBody() throws Exception {
        when(authenticationService.login(anyString(), anyString()))
                .thenReturn(new BackofficeIdentityProvider.AuthenticationResult(
                        "access-token", "refresh-token", 3600, null));

        mockMvc.perform(post("/internal/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"operator\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    @Test
    void clearsBothSessionCookiesOnLogout() throws Exception {
        mockMvc.perform(post("/internal/auth/logout")
                        .cookie(new jakarta.servlet.http.Cookie("wcs_access", "access-token")))
                .andExpect(status().isNoContent())
                .andExpect(header().stringValues("Set-Cookie",
                        org.hamcrest.Matchers.hasItems(
                                org.hamcrest.Matchers.containsString("wcs_access=;"),
                                org.hamcrest.Matchers.containsString("wcs_refresh=;"))));

        verify(authenticationService).logout("access-token");
    }

    @Test
    void mapsInvalidCredentialsToUnauthorizedWithoutLeakingProviderDetails() throws Exception {
        when(authenticationService.login("operator", "bad-password"))
                .thenThrow(BackofficeAuthenticationException.invalidCredentials());

        mockMvc.perform(post("/internal/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"operator\",\"password\":\"bad-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").doesNotExist());
    }
}
