package com.wally.customersupport.conversation.application.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class WcsToolRegistryTest {

    @Test
    void exposesStableDescriptorsForRegisteredTools() {
        WcsTool<String, String> tool = tool("support.echo");

        WcsToolRegistry registry = new WcsToolRegistry(List.of(tool));

        assertThat(registry.descriptors())
                .extracting(WcsToolDescriptor::name)
                .containsExactly("support.echo");
        assertThat(registry.find("support.echo", EchoTool.class)).isPresent();
        assertThat(registry.find("support.echo", CatalogSearchTool.class)).isEmpty();
    }

    @Test
    void rejectsDuplicateToolNamesAtConstruction() {
        WcsTool<String, String> first = tool("catalog.search");
        WcsTool<String, String> second = tool("catalog.search");

        assertThatThrownBy(() -> new WcsToolRegistry(List.of(first, second)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate WCS tool");
    }

    @Test
    void rejectsMalformedOrNonObjectSchemas() {
        assertThatThrownBy(() -> new WcsToolDescriptor("test", "test", "v1", "not-json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inputSchemaJson");
        assertThatThrownBy(() -> new WcsToolDescriptor("test", "test", "v1", "[]"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inputSchemaJson");
    }

    private static WcsTool<String, String> tool(String name) {
        return new EchoTool(new WcsToolDescriptor(name, "test", "test-v1", "{}"));
    }

    private static final class EchoTool implements WcsTool<String, String> {

        private final WcsToolDescriptor descriptor;

        private EchoTool(WcsToolDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        @Override
        public WcsToolDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public Class<String> inputType() {
            return String.class;
        }

        @Override
        public String execute(String input) {
            return input;
        }
    }
}
