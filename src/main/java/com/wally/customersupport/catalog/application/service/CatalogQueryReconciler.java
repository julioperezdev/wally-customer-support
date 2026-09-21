package com.wally.customersupport.catalog.application.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

import com.wally.customersupport.catalog.domain.model.CatalogQuery;

/**
 * Applies deterministic precedence to a model-proposed catalog query.
 *
 * <p>Explicit lexical facts and active conversation state win over model
 * guesses. This class is intentionally independent from catalog persistence
 * and from the LLM adapter.</p>
 */
public final class CatalogQueryReconciler {

    private static final Pattern NON_PRODUCT_DESCRIPTOR = Pattern.compile(
            "\\b(frio|abrigo|abrigado|abrigada|invierno|lindo|linda|bonito|bonita|"
                    + "algo|lo|antes|anterior|anteriormente|soy|tengo|estoy|ropa)\\b");

    private CatalogQueryReconciler() {
    }

    public static CatalogQuery reconcile(CatalogQuery deterministic, CatalogQuery proposed) {
        if (deterministic == null) {
            return proposed;
        }
        if (proposed == null) {
            return deterministic;
        }
        String productType = firstNonBlank(deterministic.productType(), proposed.productType());
        String proposedName = isNonProductDescriptor(proposed.name()) ? null : proposed.name();
        String name = firstNonBlank(deterministic.name(), proposedName);
        if (name != null && productType != null && name.equalsIgnoreCase(productType)) {
            name = null;
        }
        return new CatalogQuery(
                name,
                firstNonBlank(deterministic.sku(), proposed.sku()),
                firstNonBlank(deterministic.size(), proposed.size()),
                firstNonBlank(deterministic.color(), proposed.color()),
                productType,
                deterministic.minPrice() == null ? proposed.minPrice() : deterministic.minPrice(),
                deterministic.maxPrice() == null ? proposed.maxPrice() : deterministic.maxPrice());
    }

    public static CatalogQuery normalizeModelQuery(CatalogQuery proposed) {
        if (proposed == null) {
            return null;
        }
        String normalizedName = proposed.name() == null
                ? null
                : proposed.name().strip().toLowerCase(Locale.ROOT);
        if (proposed.productType() == null
                && normalizedName != null
                && java.util.Set.of("remera", "buzo", "campera", "abrigo").contains(normalizedName)) {
            return new CatalogQuery(
                    null,
                    proposed.sku(),
                    proposed.size(),
                    proposed.color(),
                    normalizedName,
                    proposed.minPrice(),
                    proposed.maxPrice());
        }
        return proposed;
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    private static boolean isNonProductDescriptor(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return NON_PRODUCT_DESCRIPTOR.matcher(normalize(value)).replaceAll(" ")
                .replaceAll("\\s+", " ")
                .trim()
                .isBlank();
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
    }
}
