package com.wally.customersupport.catalog.application.service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.wally.customersupport.catalog.domain.model.CatalogProduct;
import com.wally.customersupport.catalog.domain.model.CatalogQuery;
import com.wally.customersupport.catalog.domain.model.CatalogVariant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Executes the deterministic catalog use case from structured filters.
 *
 * <p>This boundary deliberately does not ask an LLM to generate SQL or
 * catalog facts. The orchestrator may obtain the {@link CatalogQuery} from a
 * classifier, while PostgreSQL remains the source of truth.</p>
 */
@Service
@RequiredArgsConstructor
public class CatalogConversationService {

    private static final int MAX_GENERAL_RESULTS = 5;
    private static final String CATALOG_CLARIFICATION =
            "Para buscar en el catálogo, indicame el nombre, tipo de producto, SKU, talle o color.";
    private static final String FOLLOW_UP_CLARIFICATION =
            "¿De qué producto o SKU querés conocer ese dato?";
    private static final String MULTIPLE_FOLLOW_UP_RESULTS =
            "Encontré varias opciones. Indicame el SKU o el producto exacto que querés consultar.";
    private static final String NO_MATCH =
            "No encontré coincidencias en el catálogo demo para esa consulta. "
                    + "No puedo confirmar disponibilidad fuera de los datos registrados.";

    private final CatalogQueryService catalogQueryService;

    public Optional<String> replyFor(String message) {
        return CatalogQueryParser.parse(message).map(this::replyForQuery);
    }

    public Optional<String> replyFor(CatalogQuery query) {
        return replyFor(query, List.of(), null);
    }

    public Optional<String> replyFor(CatalogQuery query, List<String> recentMessages, String latestMessage) {
        if (query == null) {
            return Optional.of(CATALOG_CLARIFICATION);
        }
        CatalogQuery activeQuery = resolveActiveQuery(query, recentMessages, latestMessage);
        CatalogQueryParser.FollowUpKind followUpKind = CatalogQueryParser.followUpKind(latestMessage);
        if (followUpKind != CatalogQueryParser.FollowUpKind.NONE) {
            return Optional.of(replyForFollowUp(activeQuery, followUpKind));
        }
        return Optional.of(replyForQuery(activeQuery));
    }

    private String replyForQuery(CatalogQuery query) {
        if (query.isEmpty()) {
            List<CatalogProduct> products = catalogQueryService.search(query);
            return products == null || products.isEmpty() ? NO_MATCH : format(products, MAX_GENERAL_RESULTS);
        }

        List<CatalogProduct> products = catalogQueryService.search(query);
        if (products != null && !products.isEmpty()) {
            return format(products, Integer.MAX_VALUE);
        }
        if (query.productType() != null) {
            List<CatalogProduct> alternatives = catalogQueryService.search(query.withoutProductType());
            if (!alternatives.isEmpty()) {
                return formatNoMatchWithAlternatives(query, alternatives);
            }
        }
        return NO_MATCH;
    }

    private String replyForFollowUp(CatalogQuery activeQuery, CatalogQueryParser.FollowUpKind followUpKind) {
        if (activeQuery == null || activeQuery.isEmpty()) {
            return FOLLOW_UP_CLARIFICATION;
        }

        List<ProductVariant> matches = flatten(catalogQueryService.search(activeQuery));
        if (matches.isEmpty()) {
            return NO_MATCH;
        }
        if (matches.size() > 1) {
            return MULTIPLE_FOLLOW_UP_RESULTS;
        }
        return formatFollowUp(matches.getFirst(), followUpKind);
    }

    private static CatalogQuery resolveActiveQuery(
            CatalogQuery query,
            List<String> recentMessages,
            String latestMessage) {
        if (query != null && !query.isEmpty()) {
            return query;
        }
        if (CatalogQueryParser.followUpKind(latestMessage) == CatalogQueryParser.FollowUpKind.NONE) {
            return query;
        }
        return CatalogQueryParser.parseConversation(recentMessages, latestMessage)
                .filter(parsed -> !parsed.isEmpty())
                .orElse(query);
    }

    private static String format(List<CatalogProduct> products, int maxResults) {
        StringBuilder response = new StringBuilder("Encontré estos productos:\n");
        flatten(products).stream()
                .limit(maxResults)
                .forEach(productVariant -> appendVariant(response, productVariant));
        return response.toString().trim();
    }

    private static String formatNoMatchWithAlternatives(CatalogQuery query, List<CatalogProduct> alternatives) {
        return "No encontré " + query.productType() + " para esa consulta. Como alternativa, encontré:\n"
                + format(alternatives, Integer.MAX_VALUE);
    }

    private static String formatFollowUp(
            ProductVariant productVariant,
            CatalogQueryParser.FollowUpKind followUpKind) {
        CatalogProduct product = productVariant.product();
        CatalogVariant variant = productVariant.variant();
        String identity = product.name() + " (SKU: " + variant.sku() + ")";
        return switch (followUpKind) {
            case AVAILABILITY -> variant.stock() > 0
                    ? "Sí, " + identity + " está disponible. Stock actual: " + variant.stock() + " unidades."
                    : "No, " + identity + " está sin stock.";
            case PRICE -> "El precio actual de " + identity + " es "
                    + formatPrice(variant.price()) + " " + variant.currency() + ".";
            case SIZE -> "La variante " + identity + " es talle " + variant.size() + ".";
            case COLOR -> "La variante " + identity + " es de color " + variant.color() + ".";
            case NONE -> "";
        };
    }

    private static List<ProductVariant> flatten(List<CatalogProduct> products) {
        if (products == null) {
            return List.of();
        }
        return products.stream()
                .flatMap(product -> product.variants().stream()
                        .map(variant -> new ProductVariant(product, variant)))
                .toList();
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
