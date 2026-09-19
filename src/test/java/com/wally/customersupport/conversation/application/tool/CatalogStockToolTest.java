package com.wally.customersupport.conversation.application.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import com.wally.customersupport.cart.application.port.out.CartCatalogReader;
import org.junit.jupiter.api.Test;

class CatalogStockToolTest {

    @Test
    void returnsAvailableStockFromTheCatalogPort() {
        CartCatalogReader reader = mock(CartCatalogReader.class);
        when(reader.findBySku("RP-REM-NP-NEG-M")).thenReturn(Optional.of(item("RP-REM-NP-NEG-M", 12, true)));

        CatalogStockTool.Result result = new CatalogStockTool(reader)
                .execute(new CatalogStockTool.Input(" RP-REM-NP-NEG-M "));

        assertThat(result).isEqualTo(new CatalogStockTool.Result(
                CatalogStockTool.Status.AVAILABLE, "RP-REM-NP-NEG-M", 12));
    }

    @Test
    void distinguishesOutOfStockAndInactiveOrMissingVariants() {
        CartCatalogReader reader = mock(CartCatalogReader.class);
        when(reader.findBySku("OUT")).thenReturn(Optional.of(item("OUT", 0, true)));
        when(reader.findBySku("INACTIVE")).thenReturn(Optional.of(item("INACTIVE", 4, false)));

        CatalogStockTool tool = new CatalogStockTool(reader);

        assertThat(tool.execute(new CatalogStockTool.Input("OUT")).status())
                .isEqualTo(CatalogStockTool.Status.OUT_OF_STOCK);
        assertThat(tool.execute(new CatalogStockTool.Input("INACTIVE")).status())
                .isEqualTo(CatalogStockTool.Status.NOT_FOUND);
        assertThat(tool.execute(new CatalogStockTool.Input("MISSING")).status())
                .isEqualTo(CatalogStockTool.Status.NOT_FOUND);
    }

    @Test
    void validatesSkuAtTheToolBoundary() {
        CartCatalogReader reader = mock(CartCatalogReader.class);

        assertThatThrownBy(() -> new CatalogStockTool.Input(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CatalogStockTool.Input("x".repeat(65)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static CartCatalogReader.CatalogItem item(String sku, int stock, boolean active) {
        return new CartCatalogReader.CatalogItem(
                sku, "Producto", "M", "Negro", BigDecimal.valueOf(100), "ARS", stock, active);
    }
}
