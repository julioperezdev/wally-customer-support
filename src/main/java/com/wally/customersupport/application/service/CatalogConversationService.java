package com.wally.customersupport.application.service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.wally.customersupport.domain.model.CatalogProduct;
import com.wally.customersupport.domain.model.CatalogQuery;
import com.wally.customersupport.domain.model.CatalogVariant;
import org.springframework.stereotype.Service;

/**
 * Interprets the small, deterministic catalog vocabulary used by the MVP.
 *
 * <p>This boundary deliberately does not ask an LLM to generate SQL or
 * catalog facts. A future LLM may replace the parser while keeping the same
 * {@link CatalogQuery} contract.</p>
 */
@Service
public class CatalogConversationService {

    private static final String CATALOG_CLARIFICATION =
            "Para buscar en el catálogo, indicame el nombre del producto, SKU, talle o color.";
    private static final String NO_MATCH =
            "No encontré coincidencias en el catálogo demo para esa consulta. "
                    + "No puedo confirmar disponibilidad fuera de los datos registrados.";

    private final CatalogQueryService catalogQueryService;

    public CatalogConversationService(CatalogQueryService catalogQueryService) {
        this.catalogQueryService = catalogQueryService;
    }

    public Optional<String> replyFor(String message) {
        Optional<CatalogQuery> parsedQuery = CatalogQueryParser.parse(message);
        if (parsedQuery.isEmpty()) {
            return Optional.empty();
        }

        CatalogQuery query = parsedQuery.get();
        if (query.isEmpty()) {
            return Optional.of(CATALOG_CLARIFICATION);
        }

        List<CatalogProduct> products = catalogQueryService.search(query);
        return Optional.of(format(query, products));
    }

    private static String format(CatalogQuery query, List<CatalogProduct> products) {
        if (products == null || products.isEmpty()) {
            return NO_MATCH;
        }

        StringBuilder response = new StringBuilder("Encontré estos productos:\n");
        products.stream()
                .flatMap(product -> product.variants().stream()
                        .map(variant -> new ProductVariant(product, variant)))
                .forEach(productVariant -> appendVariant(response, productVariant));
        return response.toString().trim();
    }

    private static void appendVariant(StringBuilder response, ProductVariant productVariant) {
        CatalogProduct product = productVariant.product();
        CatalogVariant variant = productVariant.variant();
        response.append("- ")
                .append(product.name())
                .append(" — ")
                .append(variant.color())
                .append(", talle ")
                .append(variant.size())
                .append(" — ")
                .append(formatPrice(variant.price()))
                .append(' ')
                .append(variant.currency())
                .append(" — ")
                .append(variant.stock() > 0 ? "stock disponible: " + variant.stock() : "sin stock")
                .append(" (SKU: ")
                .append(variant.sku())
                .append(")\n");
    }

    private static String formatPrice(BigDecimal price) {
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-AR"));
        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);
        return formatter.format(price);
    }

    private record ProductVariant(CatalogProduct product, CatalogVariant variant) {
    }
}
