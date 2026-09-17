package com.wally.customersupport.cart.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
    void parsesAddCommandForAnotherItem() {
        CartCommandParser.Command command = CartCommandParser.parse(
                "Sumá también un buzo Spring Boot negro talle XL al carrito");

        assertEquals(CartCommandParser.Action.ADD, command.action());
        assertEquals(1, command.quantity());
        assertEquals(new CatalogQuery("spring boot", null, "xl", "negro", "buzo", null, null), command.query());
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
