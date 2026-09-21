package com.wally.customersupport.catalog.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.stream.Stream;

import com.wally.customersupport.catalog.domain.model.CatalogQuery;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Language matrix for the deterministic catalog boundary. The examples are
 * intentionally incomplete, colloquial and typo-tolerant, while assertions
 * remain structural and independent of product data.
 */
class CatalogNaturalLanguageMatrixTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("messages")
    void extractsTheSameStructuredFiltersFromHumanLanguage(
            String message,
            String productType,
            String name,
            String size,
            String color) {
        CatalogQuery query = CatalogQueryParser.parse(message).orElseThrow();

        assertEquals(productType, query.productType(), message);
        assertEquals(name, query.name(), message);
        assertEquals(size, query.size(), message);
        assertEquals(color, query.color(), message);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refinements")
    void keepsIncompleteRefinementsStructuredWithoutRequiringAProductName(
            String message,
            String size,
            String color) {
        CatalogQuery query = CatalogQueryParser.parse(message).orElseThrow();

        assertNull(query.name(), message);
        assertEquals(size, query.size(), message);
        assertEquals(color, query.color(), message);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("typos")
    void acceptsOnlyExplicitBoundedVocabularyCorrections(
            String message,
            String expectedType,
            String expectedSize) {
        CatalogQuery query = CatalogQueryParser.parse(message).orElseThrow();

        assertEquals(expectedType, query.productType(), message);
        assertEquals(expectedSize, query.size(), message);
        assertNotNull(query, message);
    }

    static Stream<Arguments> messages() {
        return Stream.of(
                Arguments.of("Quiero un buzo", "buzo", null, null, null),
                Arguments.of("que onda, tienen buzos?", "buzo", null, null, null),
                Arguments.of("q tenes de remera?", "remera", null, null, null),
                Arguments.of("Busco una remera negra", "remera", null, null, "negro"),
                Arguments.of("algo negro para el frio", "abrigo", null, null, "negro"),
                Arguments.of("Quiero una camiseta mediana", "remera", null, "m", null),
                Arguments.of("Busco una sudadera grande", "buzo", null, "l", null),
                Arguments.of("Quiero algo para el frío, preferentemente talle L", "abrigo", null, "l", null),
                Arguments.of("Remera NullPointer negra talle M", "remera", "nullpointer", "m", "negro"));
    }

    static Stream<Arguments> refinements() {
        return Stream.of(
                Arguments.of("la M", "m", null),
                Arguments.of("soy talle m", "m", null),
                Arguments.of("que sea negra", null, "negro"),
                Arguments.of("ese en negro", null, "negro"),
                Arguments.of("hasta 20.000", null, null));
    }

    static Stream<Arguments> typos() {
        return Stream.of(
                Arguments.of("Quiero un buso", "buzo", null),
                Arguments.of("Busco una remra negra talle M", "remera", "m"),
                Arguments.of("Quiero una remera, taya M", "remera", "m"));
    }
}
