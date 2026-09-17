package com.wally.customersupport.catalog.application.service;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void removesNeutralAvailabilityPhrasesFromProductName() {
        CatalogQuery query = CatalogQueryParser.parse("¿Tienes buzo Spring Boot?").orElseThrow();

        assertEquals("spring boot", query.name());
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
    void preservesActiveProductForAvailabilityFollowUp() {
        CatalogQuery query = CatalogQueryParser.parseConversation(
                List.of("Busco una remera negra talle M"),
                "¿Está disponible?").orElseThrow();

        assertEquals("remera", query.productType());
        assertEquals("negro", query.color());
        assertEquals("m", query.size());
    }

    @Test
    void understandsTallaAsASizeRefinement() {
        CatalogQuery query = CatalogQueryParser.parseConversation(
                List.of("Busco una remera negra"),
                "Quiero la talla M").orElseThrow();

        assertEquals("remera", query.productType());
        assertEquals("negro", query.color());
        assertEquals("m", query.size());
    }

    @Test
    void preservesActiveSelectionForThisProductContinuation() {
        CatalogQuery query = CatalogQueryParser.parseConversation(
                List.of("Busco una remera negra talle M"),
                "Este producto").orElseThrow();

        assertEquals("remera", query.productType());
        assertEquals("negro", query.color());
        assertEquals("m", query.size());
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
    void recognizesPurchaseDeferralWithoutTreatingItAsCheckout() {
        assertTrue(CatalogQueryParser.isPurchaseDeferral("No quiero comprar todavía"));
        assertFalse(CatalogQueryParser.isPurchaseRequest("No quiero comprar todavía"));
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

    @Test
    void preservesActiveProductTypeForOptionsContinuation() {
        CatalogQuery query = CatalogQueryParser.parseConversation(
                List.of("Mejor un buzo"),
                "¿Qué opciones tienen?").orElseThrow();

        assertEquals("buzo", query.productType());
    }

    @Test
    void treatsUnsupportedStoreCategoryAsCatalogDataWithoutInventingAvailability() {
        CatalogQuery query = CatalogQueryParser.parse("¿Venden gorras?").orElseThrow();

        assertEquals("gorras", query.name());
        assertTrue(CatalogQueryParser.isUnsupportedCatalogCategory("¿Venden gorras?"));
    }

    @Test
    void recognizesShippingAndRemovesShippingWordsFromCatalogName() {
        CatalogQuery query = CatalogQueryParser.parse(
                "¿Cuánto cuesta el buzo y cómo se hace el envío?").orElseThrow();

        assertEquals("buzo", query.productType());
        assertNull(query.name());
        assertTrue(CatalogQueryParser.isShippingQuestion("¿Cuánto cuesta y cómo se hace el envío?"));
    }

    @Test
    void reconstructsTheLastUniqueSelectionForAnExplicitPurchaseRequest() {
        CatalogQuery query = CatalogQueryParser.parsePurchaseConversation(
                List.of("Busco una remera negra talle M"), "Quiero comprarla").orElseThrow();

        assertEquals("remera", query.productType());
        assertEquals("negro", query.color());
        assertEquals("m", query.size());
    }

    @Test
    void reconstructsRefinedSelectionForAnExplicitPurchaseRequest() {
        CatalogQuery query = CatalogQueryParser.parsePurchaseConversation(
                List.of("Este producto", "Quiero la talla M", "Busco una remera negra"),
                "Quiero comprar ahora").orElseThrow();

        assertEquals("remera", query.productType());
        assertEquals("negro", query.color());
        assertEquals("m", query.size());
    }

    @Test
    void recognizesExplicitPurchaseAndQuantityWithoutTreatingInterestAsCheckout() {
        assertTrue(CatalogQueryParser.isPurchaseRequest("Pasame el link de pago para esa remera"));
        assertTrue(CatalogQueryParser.isPurchaseRequest("Me la llevo"));
        assertTrue(CatalogQueryParser.isPurchaseRequest("Me llevo la remera negra"));
        assertEquals(2, CatalogQueryParser.purchaseQuantity("Quiero comprar 2 remeras negras"));
        assertEquals(1, CatalogQueryParser.purchaseQuantity("Me interesa esa remera"));
        assertFalse(CatalogQueryParser.isPurchaseRequest("Quiero esa remera"));
        assertFalse(CatalogQueryParser.isPurchaseRequest("No quiero comprarla todavía"));
        assertFalse(CatalogQueryParser.isPurchaseRequest("No quiero pagar todavía"));
        assertFalse(CatalogQueryParser.isPurchaseRequest("No comprar todavía"));
    }
}
