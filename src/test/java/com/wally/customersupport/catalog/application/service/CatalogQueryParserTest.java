package com.wally.customersupport.catalog.application.service;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
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

    @Test
    void recognizesCatalogFollowUpQuestions() {
        assertEquals(CatalogQueryParser.FollowUpKind.AVAILABILITY,
                CatalogQueryParser.followUpKind("¿Está disponible?"));
        assertEquals(CatalogQueryParser.FollowUpKind.PRICE,
                CatalogQueryParser.followUpKind("¿Cuánto cuesta?"));
    }

    @Test
    void extractsMaximumPriceWithoutPollutingTheProductName() {
        CatalogQuery query = CatalogQueryParser.parse(
                "Busco una remera negra talle M que cueste menos de 20.000 pesos").orElseThrow();

        assertNull(query.name());
        assertEquals("remera", query.productType());
        assertEquals("m", query.size());
        assertEquals("negro", query.color());
        assertEquals(new BigDecimal("20000"), query.maxPrice());
        assertNull(query.minPrice());
    }

    @Test
    void extractsPriceRangeAndMergesItWithConversationFilters() {
        CatalogQuery query = CatalogQueryParser.parseConversation(
                List.of("busco ropa negra", "que cueste menos de 50000"),
                "quiero un buzo entre 30000 y 45000").orElseThrow();

        assertEquals("buzo", query.productType());
        assertEquals("negro", query.color());
        assertEquals(new BigDecimal("30000"), query.minPrice());
        assertEquals(new BigDecimal("45000"), query.maxPrice());
    }
}
