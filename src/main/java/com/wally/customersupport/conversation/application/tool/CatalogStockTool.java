package com.wally.customersupport.conversation.application.tool;

import java.util.Objects;

import com.wally.customersupport.cart.application.port.out.CartCatalogReader;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Reads stock for one validated SKU without exposing persistence details. */
@Component
@Slf4j
public final class CatalogStockTool implements WcsTool<CatalogStockTool.Input, CatalogStockTool.Result> {

    public static final String NAME = WcsToolContractCatalog.CATALOG_STOCK;
    public static final WcsToolDescriptor DESCRIPTOR = WcsToolContractCatalog.find(NAME).orElseThrow();

    private final CartCatalogReader catalogReader;

    public CatalogStockTool(CartCatalogReader catalogReader) {
        this.catalogReader = Objects.requireNonNull(catalogReader, "catalogReader");
    }

    @Override
    public WcsToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public Class<Input> inputType() {
        return Input.class;
    }

    @Override
    public Result execute(Input input) {
        Objects.requireNonNull(input, "input");
        Result result = catalogReader.findBySku(input.sku())
                .filter(CartCatalogReader.CatalogItem::active)
                .map(item -> item.stock() > 0
                        ? new Result(Status.AVAILABLE, item.sku(), item.stock())
                        : new Result(Status.OUT_OF_STOCK, item.sku(), 0))
                .orElseGet(() -> new Result(Status.NOT_FOUND, input.sku(), 0));
        StructuredEventLog.info(log, "WCS_TOOL_EXECUTED", java.util.Map.of(
                "tool", NAME,
                "status", result.status().name(),
                "stock", result.stock()));
        return result;
    }

    public record Input(String sku) {

        public Input {
            sku = Objects.requireNonNull(sku, "sku").trim();
            if (sku.isBlank() || sku.length() > 64) {
                throw new IllegalArgumentException("sku must be non-blank and at most 64 characters");
            }
        }
    }

    public enum Status {
        AVAILABLE,
        OUT_OF_STOCK,
        NOT_FOUND
    }

    public record Result(Status status, String sku, int stock) {

        public Result {
            status = Objects.requireNonNull(status, "status");
            sku = Objects.requireNonNull(sku, "sku");
            if (stock < 0) {
                throw new IllegalArgumentException("stock must not be negative");
            }
        }
    }
}
