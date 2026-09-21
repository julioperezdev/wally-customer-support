package com.wally.customersupport.catalog.application.service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Renders only validated catalog facts; it cannot add catalog data. */
public final class CatalogResponseFormatter {

    private static final String CATALOG_CLARIFICATION =
            "Para buscar en el catálogo, indicame el nombre, tipo de producto, SKU, talle o color.";
    private static final String FOLLOW_UP_CLARIFICATION =
            "¿De qué producto o SKU querés conocer ese dato?";
    private static final String MULTIPLE_FOLLOW_UP_RESULTS =
            "Encontré varias opciones. Indicame el SKU o el producto exacto que querés consultar.";
    private static final String NO_MATCH =
            "No encontré coincidencias en el catálogo demo para esa consulta. "
                    + "No puedo confirmar disponibilidad fuera de los datos registrados.";
    private static final String UNSUPPORTED_CATEGORY =
            "Por ahora no ofrecemos %s. Nuestro catálogo actual incluye remeras, buzos y camperas. "
                    + "Si querés, puedo mostrarte esas opciones.";

    private CatalogResponseFormatter() {
    }

    public static String render(CatalogSearchResult result) {
        Objects.requireNonNull(result, "result");
        return switch (result.status()) {
            case MATCHED -> result.followUpKind() == CatalogSearchResult.FollowUpKind.NONE
                    ? format(result.facts())
                    : formatFollowUp(result.facts().getFirst(), result.followUpKind());
            case ALTERNATIVES -> "No encontré " + requestedDescription(result)
                    + " para esa consulta. Como alternativa, encontré:\n"
                    + format(result.facts());
            case CLARIFICATION -> "FOLLOW_UP".equals(result.reason())
                    ? FOLLOW_UP_CLARIFICATION
                    : "UNSUPPORTED_ATTRIBUTE".equals(result.reason())
                            ? "Todavía no puedo filtrar por ese atributo. Puedo buscar por producto, talle, color, precio y stock."
                    : CATALOG_CLARIFICATION;
            case AMBIGUOUS -> MULTIPLE_FOLLOW_UP_RESULTS;
            case NO_MATCH -> NO_MATCH;
            case UNSUPPORTED_CATEGORY -> String.format(
                    Locale.forLanguageTag("es-AR"),
                    UNSUPPORTED_CATEGORY,
                    result.requestedProductType() == null ? "esa categoría" : result.requestedProductType());
        };
    }

    private static String format(List<CatalogFact> facts) {
        StringBuilder response = new StringBuilder("Encontré estos productos:\n");
        facts.forEach(fact -> appendFact(response, fact));
        return response.toString().trim();
    }

    private static String requestedDescription(CatalogSearchResult result) {
        return result.requestedProductType() == null || result.requestedProductType().isBlank()
                ? "esa combinación"
                : result.requestedProductType();
    }

    private static String formatFollowUp(
            CatalogFact fact,
            CatalogSearchResult.FollowUpKind followUpKind) {
        String identity = fact.productName() + " (SKU: " + fact.sku() + ")";
        return switch (followUpKind) {
            case AVAILABILITY -> fact.available()
                    ? "Sí, " + identity + " está disponible. Stock actual: " + fact.stock() + " unidades."
                    : "No, " + identity + " está sin stock.";
            case PRICE -> "El precio actual de " + identity + " es "
                    + formatPrice(fact.price()) + " " + fact.currency() + ".";
            case SIZE -> "La variante " + identity + " es talle " + fact.size() + ".";
            case COLOR -> "La variante " + identity + " es de color " + fact.color() + ".";
            case NONE -> format(List.of(fact));
        };
    }

    private static void appendFact(StringBuilder response, CatalogFact fact) {
        response.append("- ")
                .append(fact.productName())
                .append(" — ")
                .append(fact.color())
                .append(", talle ")
                .append(fact.size())
                .append(" — ")
                .append(formatPrice(fact.price()))
                .append(' ')
                .append(fact.currency())
                .append(" — ")
                .append(fact.available() ? "stock disponible: " + fact.stock() : "sin stock")
                .append(" (SKU: ")
                .append(fact.sku())
                .append(")\n");
    }

    private static String formatPrice(BigDecimal price) {
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-AR"));
        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);
        return formatter.format(price);
    }
}
