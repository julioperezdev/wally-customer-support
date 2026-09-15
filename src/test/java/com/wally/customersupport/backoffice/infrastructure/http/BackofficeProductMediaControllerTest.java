package com.wally.customersupport.backoffice.infrastructure.http;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import com.wally.customersupport.backoffice.application.model.BackofficeImageView;
import com.wally.customersupport.backoffice.application.service.BackofficeAccessService;
import com.wally.customersupport.backoffice.application.service.BackofficeProductMediaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class BackofficeProductMediaControllerTest {

    @Mock
    private BackofficeAccessService accessService;

    @Mock
    private BackofficeProductMediaService mediaService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new BackofficeProductMediaController(accessService, mediaService)).build();
    }

    @Test
    void returnsShortLivedPrivateViewUrlAfterCatalogAuthorization() throws Exception {
        UUID productId = UUID.randomUUID();
        when(accessService.authorize("backoffice.catalog.read", "operator"))
                .thenReturn(new BackofficeAccessService.Decision(true, 200, "authorized"));
        when(mediaService.requestView(productId)).thenReturn(new BackofficeImageView(
                productId,
                "wcs/catalog/" + productId + "/image.png",
                "https://s3.example/presigned-view",
                Instant.parse("2026-09-14T15:00:00Z")));

        mockMvc.perform(get("/internal/backoffice/catalog/products/{id}/image/view-url", productId)
                        .principal(() -> "operator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(productId.toString()))
                .andExpect(jsonPath("$.viewUrl").value("https://s3.example/presigned-view"));
    }

    @Test
    void doesNotExposeMediaMetadataWithoutCatalogReadCapability() throws Exception {
        UUID productId = UUID.randomUUID();
        when(accessService.authorize("backoffice.catalog.read", "operator"))
                .thenReturn(new BackofficeAccessService.Decision(false, 403, "BACKOFFICE_AUTHORIZATION_REQUIRED"));

        mockMvc.perform(get("/internal/backoffice/catalog/products/{id}/image/view-url", productId)
                        .principal(() -> "operator"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BACKOFFICE_AUTHORIZATION_REQUIRED"));
    }
}
