package com.wally.customersupport.cart.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wally.customersupport.catalog.domain.model.CatalogQuery;
import org.junit.jupiter.api.Test;

class CartCommandParserTest {

    @Test
    void parsesAddCommandWithQuantityAndCatalogFilters() {
        CartCommandParser.Command command = CartCommandParser.parse(
                "Agregá 2 remeras NullPointer negras talle M al carrito");

        assertEquals(CartCommandParser.Action.ADD, command.action());
        assertEquals(2, command.quantity());
        assertEquals(new CatalogQuery("nullpointer", null, "m", "negro", "remera", null, null), command.query());
    }

    @Test
    void parsesSpanishQuantityWordsWithoutPollutingTheCatalogQuery() {
        CartCommandParser.Command add = CartCommandParser.parse(
                "Agregá dos remeras NullPointer negras talle M al carrito");
        CartCommandParser.Command remove = CartCommandParser.parse(
                "Sacá dos remeras NullPointer negras talle M");

        CatalogQuery expectedQuery = new CatalogQuery("nullpointer", null, "m", "negro", "remera", null, null);
        assertEquals(CartCommandParser.Action.ADD, add.action());
        assertEquals(2, add.quantity());
        assertEquals(expectedQuery, add.query());
        assertEquals(CartCommandParser.Action.REMOVE, remove.action());
        assertEquals(2, remove.quantity());
        assertEquals(expectedQuery, remove.query());
    }

    @Test
    void parsesQuantityAfterCartPhraseWithoutTreatingItAsOne() {
        CartCommandParser.Command numeric = CartCommandParser.parse("Agrega al carrito 2");
        CartCommandParser.Command words = CartCommandParser.parse("Agregá al carrito dos");

        assertEquals(CartCommandParser.Action.ADD, numeric.action());
        assertEquals(2, numeric.quantity());
        assertTrue(numeric.query().isEmpty());
        assertEquals(CartCommandParser.Action.ADD, words.action());
        assertEquals(2, words.quantity());
        assertTrue(words.query().isEmpty());
    }

    @Test
    void parsesQuantityAfterCartPhraseBeforeTheItem() {
        CartCommandParser.Command command = CartCommandParser.parse(
                "Agrega al carrito 2 remeras NullPointer negras talle M");

        assertEquals(CartCommandParser.Action.ADD, command.action());
        assertEquals(2, command.quantity());
        assertEquals(new CatalogQuery("nullpointer", null, "m", "negro", "remera", null, null), command.query());
    }

    @Test
    void parsesSingularQuantityWordsButKeepsPlainCatalogRequestsOutsideTheCart() {
        CartCommandParser.Command add = CartCommandParser.parse(
                "Sumá una remera NullPointer negra talle M al carrito");

        assertEquals(CartCommandParser.Action.ADD, add.action());
        assertEquals(1, add.quantity());
        assertEquals(new CatalogQuery("nullpointer", null, "m", "negro", "remera", null, null), add.query());
        assertEquals(CartCommandParser.Action.NONE, CartCommandParser.parse("Quiero una remera").action());
    }

    @Test
    void parsesAddCommandForAnotherItem() {
        CartCommandParser.Command command = CartCommandParser.parse(
                "Sumá también un buzo Spring Boot negro talle XL al carrito");

        assertEquals(CartCommandParser.Action.ADD, command.action());
        assertEquals(1, command.quantity());
        assertEquals(new CatalogQuery("spring boot", null, "xl", "negro", "buzo", null, null), command.query());
    }

    @Test
    void recognizesInflectedAddPronounWithoutInventingTheVariant() {
        CartCommandParser.Command command = CartCommandParser.parse("Agregala al carrito");

        assertEquals(CartCommandParser.Action.ADD, command.action());
        assertEquals(1, command.quantity());
        assertEquals(true, command.query().isEmpty());
    }

    @Test
    void parsesImplicitAdditionOnlyWhenTheCartBoundaryAllowsIt() {
        CartCommandParser.Command command = CartCommandParser.parse(
                "También quiero 1 buzo Spring Boot negro talle XL", true);

        assertEquals(CartCommandParser.Action.ADD, command.action());
        assertEquals(1, command.quantity());
        assertEquals(new CatalogQuery("spring boot", null, "xl", "negro", "buzo", null, null), command.query());
        assertEquals(CartCommandParser.Action.NONE, CartCommandParser.parse("Quiero un buzo").action());
    }

    @Test
    void recognizesCartLifecycleCommandsWithoutCatalogQuery() {
        assertEquals(CartCommandParser.Action.VIEW, CartCommandParser.parse("¿Qué hay en mi carrito?").action());
        assertEquals(CartCommandParser.Action.REMOVE, CartCommandParser.parse("Sacá 1 del carrito").action());
        assertEquals(CartCommandParser.Action.CLEAR, CartCommandParser.parse("Vaciar carrito").action());
        assertEquals(CartCommandParser.Action.REVIEW_CHECKOUT, CartCommandParser.parse("Quiero pagar").action());
        assertEquals(CartCommandParser.Action.CONFIRM, CartCommandParser.parse("Confirmar compra").action());
        assertEquals(CartCommandParser.Action.CANCEL_CHECKOUT,
                CartCommandParser.parse("Cancelar el link de pago").action());
        assertEquals(CartCommandParser.Action.DEFER,
                CartCommandParser.parse("No quiero comprar todavía").action());
    }

    @Test
    void doesNotTreatUnrelatedQuestionsAsCartCommands() {
        CartCommandParser.Command command = CartCommandParser.parse("¿Dónde están ubicados?");

        assertEquals(CartCommandParser.Action.NONE, command.action());
        assertNull(command.query());
    }
}
