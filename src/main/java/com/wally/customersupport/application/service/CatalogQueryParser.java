package com.wally.customersupport.application.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.wally.customersupport.domain.model.CatalogQuery;

final class CatalogQueryParser {

    private static final Pattern SKU = Pattern.compile("\\b[a-z]{2}(?:-[a-z0-9]+){2,}\\b");
    private static final Pattern SIZE = Pattern.compile("\\b(?:talle|tamano|size)?\\s*(xxl|xl|xs|l|m|s)\\b");
    private static final Pattern COLOR = Pattern.compile("\\b(negro|negra|blanco|blanca|gris|azul|rojo|roja|verde)\\b");
    private static final Pattern CATALOG_MARKER = Pattern.compile(
            "\\b(remera|remeras|buzo|buzos|campera|camperas|producto|productos|catalogo|stock|disponible|"
                    + "disponibilidad|talle|tamano|size|sku|precio|precios|color)\\b");
    private static final Pattern STOP_WORDS = Pattern.compile(
            "\\b(tienen|tenes|hay|venden|vende|quiero|busco|necesito|una|un|el|la|los|las|del|de|en|con|"
                    + "por|para|favor|me|podes|pueden|puedo|cuanto|cuál|cual|es|esta|tiene|stock|disponible|"
                    + "disponibilidad|precio|precios|color|talle|tamano|size|sku|productos?|catalogo|algo|"
                    + "alguna|alguno|que|qué)\\b");

    private CatalogQueryParser() {
    }

    static Optional<CatalogQuery> parse(String message) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }

        String normalized = normalize(message);
        Matcher skuMatcher = SKU.matcher(normalized);
        String sku = skuMatcher.find() ? skuMatcher.group() : null;
        String size = extract(SIZE, normalized);
        String color = normalizeColor(extract(COLOR, normalized));

        if (!CATALOG_MARKER.matcher(normalized).find() && sku == null && size == null && color == null) {
            return Optional.empty();
        }

        String name = normalized;
        if (sku != null) {
            name = name.replace(sku, " ");
        }
        name = removeMatches(name, SIZE);
        name = removeMatches(name, COLOR);
        name = STOP_WORDS.matcher(name).replaceAll(" ");
        name = name.replaceAll("[^a-z0-9]+", " ").replaceAll("\\s+", " ").trim();

        return Optional.of(new CatalogQuery(name.isBlank() ? null : name, sku, size, color));
    }

    private static String extract(Pattern pattern, String input) {
        Matcher matcher = pattern.matcher(input);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String removeMatches(String input, Pattern pattern) {
        return pattern.matcher(input).replaceAll(" ");
    }

    private static String normalizeColor(String color) {
        if (color == null) {
            return null;
        }
        return switch (color) {
            case "negra" -> "negro";
            case "blanca" -> "blanco";
            case "roja" -> "rojo";
            default -> color;
        };
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[¿?¡!.,;:()\\[\\]{}]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
