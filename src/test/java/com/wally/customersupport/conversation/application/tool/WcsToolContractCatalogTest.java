package com.wally.customersupport.conversation.application.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class WcsToolContractCatalogTest {

    @Test
    void exposesVersionedContractsForCoreBusinessCapabilities() {
        assertThat(WcsToolContractCatalog.all())
                .extracting(WcsToolDescriptor::name)
                .containsExactlyInAnyOrder(
                        "catalog.search",
                        "catalog.stock",
                        "knowledge.retrieve",
                        "conversation.state",
                        "cart.manage",
                        "checkout.create",
                        "human-handoff",
                        "safe-fallback");

        assertThat(WcsToolContractCatalog.find(WcsToolContractCatalog.CATALOG_SEARCH))
                .get()
                .satisfies(contract -> {
                    assertThat(contract.inputSchemaVersion()).isEqualTo("catalog-input-v1");
                    assertThat(contract.outputSchemaVersion()).isEqualTo("catalog-output-v1");
                    assertThat(contract.requiredCapability()).isEqualTo("catalog.read");
                });
    }

    @Test
    void rejectsNoInvalidContractMetadata() {
        assertThat(WcsToolContractCatalog.all())
                .allSatisfy(contract -> {
                    assertThat(contract.inputSchemaJson()).startsWith("{");
                    assertThat(contract.outputSchemaJson()).startsWith("{");
                    assertThat(contract.inputSchemaVersion()).isNotBlank();
                    assertThat(contract.outputSchemaVersion()).isNotBlank();
                });
    }

    @Test
    void exposesBoundedSchemasForEveryNonCatalogCapability() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        assertRequiredFields(mapper, WcsToolContractCatalog.CATALOG_STOCK, "sku");
        assertRequiredFields(mapper, WcsToolContractCatalog.KNOWLEDGE_RETRIEVE, "query", "topic");
        assertRequiredFields(mapper, WcsToolContractCatalog.CONVERSATION_STATE,
                "operation", "selectionField", "selectionValue");
        assertRequiredFields(mapper, WcsToolContractCatalog.CART_MANAGE, "operation", "sku", "quantity");
        assertRequiredFields(mapper, WcsToolContractCatalog.CHECKOUT_CREATE, "confirmed", "cartVersion");
        assertRequiredFields(mapper, WcsToolContractCatalog.HUMAN_HANDOFF,
                "reason", "priority", "contextAvailable");
        assertRequiredFields(mapper, WcsToolContractCatalog.SAFE_FALLBACK, "reason");

        JsonNode checkout = mapper.readTree(
                WcsToolContractCatalog.find(WcsToolContractCatalog.CHECKOUT_CREATE)
                        .orElseThrow()
                        .inputSchemaJson());
        assertThat(checkout.path("properties").path("confirmed").path("const").booleanValue()).isTrue();
    }

    private static void assertRequiredFields(
            ObjectMapper mapper,
            String toolName,
            String... expectedFields) throws Exception {
        JsonNode schema = mapper.readTree(WcsToolContractCatalog.find(toolName).orElseThrow().inputSchemaJson());
        assertThat(schema.path("type").asText()).isEqualTo("object");
        assertThat(schema.path("additionalProperties").booleanValue()).isFalse();

        List<String> actualFields = new ArrayList<>();
        schema.path("required").forEach(node -> actualFields.add(node.asText()));
        assertThat(actualFields).containsExactlyInAnyOrder(expectedFields);
    }
}
