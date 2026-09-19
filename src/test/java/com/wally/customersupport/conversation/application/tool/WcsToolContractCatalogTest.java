package com.wally.customersupport.conversation.application.tool;

import static org.assertj.core.api.Assertions.assertThat;

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
}
