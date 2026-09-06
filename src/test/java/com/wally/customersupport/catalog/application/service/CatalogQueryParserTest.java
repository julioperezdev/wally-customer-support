package com.wally.customersupport.catalog.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.wally.customersupport.catalog.domain.model.CatalogQuery;
import org.junit.jupiter.api.Test;

class CatalogQueryParserTest {

    @Test
    void extractsProductTypeFromCatalogMessage() {
        CatalogQuery query = CatalogQueryParser.parse("¿Tenés un buzo NullPointer?").orElseThrow();

        assertEquals("nullpointer", query.name());
        assertEquals("buzo", query.productType());
    }

    @Test
    void combinesFiltersAcrossMultiTurnCatalogConversation() {
        CatalogQuery query = CatalogQueryParser.parseConversation(
                List.of("que sea nullpointer", "pero quiero buzo", "tenes algo negro"),
                "que sea nullpointer")
                .orElseThrow();

        assertEquals("nullpointer", query.name());
        assertEquals("buzo", query.productType());
        assertEquals("negro", query.color());
    }

    @Test
    void ignoresAnUnrelatedLatestMessage() {
        assertTrue(CatalogQueryParser.parseConversation(
                List.of("busco una remera negra"), "¿Dónde están ubicados?").isEmpty());
    }

    @Test
    void doesNotTreatGeneralStoreQuestionAsCatalogRefinement() {
        assertTrue(CatalogQueryParser.parseConversation(List.of(), "¿Qué vendés?").isEmpty());
    }
}
