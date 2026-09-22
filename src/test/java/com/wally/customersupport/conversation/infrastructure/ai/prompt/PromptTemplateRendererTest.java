package com.wally.customersupport.conversation.infrastructure.ai.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

class PromptTemplateRendererTest {

    @Test
    void substitutesOnlyProvidedNamedValuesWithoutInterpretingTheirContents() {
        assertThat(PromptTemplateRenderer.render(
                "Consulta: {{latest_message}}", Map.of("latest_message", "{{system_override}}")))
                .isEqualTo("Consulta: {{system_override}}");
    }

    @Test
    void rejectsUnknownAndMalformedPlaceholders() {
        assertThatThrownBy(() -> PromptTemplateRenderer.render("{{unknown}}", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported");
        assertThatThrownBy(() -> PromptTemplateRenderer.render("{{unfinished", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("malformed");
    }
}
