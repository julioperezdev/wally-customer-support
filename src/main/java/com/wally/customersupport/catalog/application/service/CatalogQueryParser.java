package com.wally.customersupport.catalog.application.service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.wally.customersupport.catalog.domain.model.CatalogQuery;

public final class CatalogQueryParser {

    private static final Pattern SKU = Pattern.compile("\\b[a-z]{2}(?:-[a-z0-9]+){2,}\\b");
    private static final Pattern SIZE = Pattern.compile("\\b(?:talle|tamano|size)?\\s*(xxl|xl|xs|l|m|s)\\b");
    private static final Pattern COLOR = Pattern.compile("\\b(negro|negra|blanco|blanca|gris|azul|rojo|roja|verde)\\b");
    private static final Pattern PRODUCT_TYPE = Pattern.compile("\\b(remera|remeras|buzo|buzos|campera|camperas)\\b");
    private static final Pattern REFINEMENT_MARKER = Pattern.compile(
            "\\b(que|sea|tambien|también|ahora|solo|sólo|pero|mejor|tipo)\\b");
    private static final Pattern CATALOG_MARKER = Pattern.compile(
            "\\b(remera|remeras|buzo|buzos|campera|camperas|producto|productos|catalogo|stock|disponible|"
                    + "disponibilidad|talle|tamano|size|sku|precio|precios|color)\\b");
    private static final Pattern STOP_WORDS = Pattern.compile(
            "\\b(tienen|tenes|hay|venden|vende|quiero|busco|necesito|una|un|el|la|los|las|del|de|en|con|"
                    + "vendes|"
                    + "por|para|favor|me|podes|pueden|puedo|cuanto|cuál|cual|es|esta|tiene|stock|disponible|"
                    + "disponibilidad|precio|precios|color|talle|tamano|size|sku|productos?|catalogo|algo|"
                    + "alguna|alguno|que|qué|sea|estilo|mi|ahora|solo|sólo|tambien|también|mejor|tipo)\\b");

    private CatalogQueryParser() {
    }

    public static Optional<CatalogQuery> parse(String message) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }

        String normalized = normalize(message);
        Matcher skuMatcher = SKU.matcher(normalized);
        String sku = skuMatcher.find() ? skuMatcher.group() : null;
        String size = extract(SIZE, normalized);
        String color = normalizeColor(extract(COLOR, normalized));
        String productType = normalizeProductType(extract(PRODUCT_TYPE, normalized));

        if (!CATALOG_MARKER.matcher(normalized).find() && sku == null && size == null && color == null) {
            return Optional.empty();
        }

        String name = normalized;
        if (sku != null) {
            name = name.replace(sku, " ");
        }
        name = removeMatches(name, SIZE);
        name = removeMatches(name, COLOR);
        name = removeMatches(name, PRODUCT_TYPE);
        name = STOP_WORDS.matcher(name).replaceAll(" ");
        name = name.replaceAll("[^a-z0-9]+", " ").replaceAll("\\s+", " ").trim();

        return Optional.of(new CatalogQuery(name.isBlank() ? null : name, sku, size, color, productType));
    }

    /**
     * Reconstructs active catalog filters from bounded, newest-first history.
     * The latest turn must be a catalog turn or an explicit refinement.
     */
    public static Optional<CatalogQuery> parseConversation(List<String> recentMessages, String latestMessage) {
        if (latestMessage == null || latestMessage.isBlank()) {
            return Optional.empty();
        }
        List<String> boundedHistory = recentMessages == null ? List.of() : recentMessages;
        if (!looksLikeCatalogTurn(latestMessage)) {
            return Optional.empty();
        }
        Optional<CatalogQuery> latestQuery = parse(latestMessage).or(() -> parseRefinement(latestMessage));
        if (latestQuery.isEmpty()) {
            return Optional.empty();
        }

        CatalogQuery activeQuery = null;
        List<String> history = boundedHistory.reversed();
        for (String message : history) {
            Optional<CatalogQuery> parsed = latestMessage.equals(message) ? latestQuery : parse(message);
            if (parsed.isPresent()) {
                activeQuery = activeQuery == null ? parsed.get() : activeQuery.merge(parsed.get());
            }
        }

        if (history.isEmpty() || !latestMessage.equals(history.getLast())) {
            activeQuery = activeQuery == null ? latestQuery.get() : activeQuery.merge(latestQuery.get());
        }
        return Optional.ofNullable(activeQuery);
    }

    private static Optional<CatalogQuery> parseRefinement(String message) {
        String normalized = normalize(message);
        String size = extract(SIZE, normalized);
        String color = normalizeColor(extract(COLOR, normalized));
        String productType = normalizeProductType(extract(PRODUCT_TYPE, normalized));
        String name = removeMatches(normalized, SIZE);
        name = removeMatches(name, COLOR);
        name = removeMatches(name, PRODUCT_TYPE);
        name = STOP_WORDS.matcher(name).replaceAll(" ");
        name = name.replaceAll("[^a-z0-9]+", " ").replaceAll("\\s+", " ").trim();
        if (name.isBlank() && size == null && color == null && productType == null) {
            return Optional.empty();
        }
        return Optional.of(new CatalogQuery(name.isBlank() ? null : name, null, size, color, productType));
    }

    private static boolean looksLikeCatalogTurn(String message) {
        String normalized = normalize(message);
        return CATALOG_MARKER.matcher(normalized).find()
                || SKU.matcher(normalized).find()
                || SIZE.matcher(normalized).find()
                || COLOR.matcher(normalized).find()
                || REFINEMENT_MARKER.matcher(normalized).find();
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

    private static String normalizeProductType(String productType) {
        if (productType == null) {
            return null;
        }
        return switch (productType) {
            case "remeras" -> "remera";
            case "buzos" -> "buzo";
            case "camperas" -> "campera";
            default -> productType;
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
