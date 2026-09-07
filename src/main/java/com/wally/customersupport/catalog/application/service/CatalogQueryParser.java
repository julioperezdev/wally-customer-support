package com.wally.customersupport.catalog.application.service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.wally.customersupport.catalog.domain.model.CatalogQuery;

public final class CatalogQueryParser {

    public enum FollowUpKind {
        NONE,
        AVAILABILITY,
        PRICE,
        SIZE,
        COLOR
    }

    private static final Pattern SKU = Pattern.compile("\\b[a-z]{2}(?:-[a-z0-9]+){2,}\\b");
    private static final Pattern SIZE = Pattern.compile("\\b(?:talle|tamano|size)?\\s*(xxl|xl|xs|l|m|s)\\b");
    private static final Pattern COLOR = Pattern.compile("\\b(negro|negra|blanco|blanca|gris|azul|rojo|roja|verde)\\b");
    private static final Pattern PRODUCT_TYPE = Pattern.compile("\\b(remera|remeras|buzo|buzos|campera|camperas)\\b");
    private static final Pattern MAX_PRICE = Pattern.compile(
            "\\b(?:menos\\s+de|menor\\s+(?:que|a)?|por\\s+debajo\\s+de|hasta|como\\s+maximo(?:\\s+de)?|maximo(?:\\s+de)?|tope(?:\\s+de)?)"
                    + "\\s*\\$?\\s*([0-9][0-9\\s.,]*)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MIN_PRICE = Pattern.compile(
            "\\b(?:mas\\s+de|mayor\\s+(?:que|a)?|por\\s+encima\\s+de|desde|a\\s+partir\\s+de|como\\s+minimo(?:\\s+de)?|minimo(?:\\s+de)?)"
                    + "\\s*\\$?\\s*([0-9][0-9\\s.,]*)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BETWEEN_PRICE = Pattern.compile(
            "\\bentre\\s*\\$?\\s*([0-9][0-9\\s.,]*)\\s+(?:y|a)\\s*\\$?\\s*([0-9][0-9\\s.,]*)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern REFINEMENT_MARKER = Pattern.compile(
            "\\b(que|sea|tambien|también|ahora|solo|sólo|pero|mejor|tipo)\\b");
    private static final Pattern CATALOG_MARKER = Pattern.compile(
            "\\b(remera|remeras|buzo|buzos|campera|camperas|producto|productos|catalogo|stock|disponible|"
                    + "disponibilidad|talle|tamano|size|sku|precio|precios|cuesta|cueste|color|barato|barata|"
                    + "caro|cara|menos|mas|hasta|debajo|encima|entre)\\b");
    private static final Pattern AVAILABILITY_FOLLOW_UP = Pattern.compile(
            "\\b(disponible|disponibilidad|hay stock|tiene stock)\\b");
    private static final Pattern PRICE_FOLLOW_UP = Pattern.compile(
            "\\b(cuanto|cuesta|precio|sale|valor)\\b");
    private static final Pattern SIZE_FOLLOW_UP = Pattern.compile(
            "\\b(que talle|cual talle|que tamano|cual tamano|que size)\\b");
    private static final Pattern COLOR_FOLLOW_UP = Pattern.compile(
            "\\b(que color|cual color|en que color)\\b");
    private static final Pattern STOP_WORDS = Pattern.compile(
            "\\b(tienen|tenes|hay|venden|vende|quiero|busco|necesito|una|un|el|la|los|las|del|de|en|con|"
                    + "vendes|"
                    + "por|para|favor|me|podes|pueden|puedo|cuanto|cuál|cual|es|esta|tiene|stock|disponible|"
                    + "disponibilidad|precio|precios|color|talle|tamano|size|sku|productos?|catalogo|algo|"
                    + "alguna|alguno|que|qué|sea|estilo|mi|ahora|solo|sólo|tambien|también|mejor|tipo|"
                    + "cuesta|cueste|menos|mas|barato|barata|caro|cara|hasta|debajo|encima|entre|"
                    + "pesos?|ars)\\b");

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
        PriceRange priceRange = extractPriceRange(message);

        if (!CATALOG_MARKER.matcher(normalized).find()
                && sku == null && size == null && color == null && priceRange.isEmpty()) {
            return Optional.empty();
        }

        String name = normalized;
        if (sku != null) {
            name = name.replace(sku, " ");
        }
        name = removeMatches(name, SIZE);
        name = removeMatches(name, COLOR);
        name = removeMatches(name, PRODUCT_TYPE);
        name = removePriceClauses(name);
        name = STOP_WORDS.matcher(name).replaceAll(" ");
        name = name.replaceAll("[^a-z0-9]+", " ").replaceAll("\\s+", " ").trim();

        return Optional.of(new CatalogQuery(
                name.isBlank() ? null : name,
                sku,
                size,
                color,
                productType,
                priceRange.minPrice(),
                priceRange.maxPrice()));
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

    public static FollowUpKind followUpKind(String message) {
        if (message == null || message.isBlank()) {
            return FollowUpKind.NONE;
        }
        String normalized = normalize(message);
        if (AVAILABILITY_FOLLOW_UP.matcher(normalized).find()) {
            return FollowUpKind.AVAILABILITY;
        }
        if (PRICE_FOLLOW_UP.matcher(normalized).find()) {
            return FollowUpKind.PRICE;
        }
        if (SIZE_FOLLOW_UP.matcher(normalized).find()) {
            return FollowUpKind.SIZE;
        }
        if (COLOR_FOLLOW_UP.matcher(normalized).find()) {
            return FollowUpKind.COLOR;
        }
        return FollowUpKind.NONE;
    }

    private static Optional<CatalogQuery> parseRefinement(String message) {
        String normalized = normalize(message);
        String size = extract(SIZE, normalized);
        String color = normalizeColor(extract(COLOR, normalized));
        String productType = normalizeProductType(extract(PRODUCT_TYPE, normalized));
        PriceRange priceRange = extractPriceRange(message);
        String name = removeMatches(normalized, SIZE);
        name = removeMatches(name, COLOR);
        name = removeMatches(name, PRODUCT_TYPE);
        name = removePriceClauses(name);
        name = STOP_WORDS.matcher(name).replaceAll(" ");
        name = name.replaceAll("[^a-z0-9]+", " ").replaceAll("\\s+", " ").trim();
        if (name.isBlank() && size == null && color == null && productType == null && priceRange.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new CatalogQuery(
                name.isBlank() ? null : name,
                null,
                size,
                color,
                productType,
                priceRange.minPrice(),
                priceRange.maxPrice()));
    }

    private static boolean looksLikeCatalogTurn(String message) {
        String normalized = normalize(message);
        return CATALOG_MARKER.matcher(normalized).find()
                || SKU.matcher(normalized).find()
                || SIZE.matcher(normalized).find()
                || COLOR.matcher(normalized).find()
                || REFINEMENT_MARKER.matcher(normalized).find()
                || !extractPriceRange(message).isEmpty()
                || followUpKind(normalized) != FollowUpKind.NONE;
    }

    private static PriceRange extractPriceRange(String message) {
        String normalized = normalizeKeepingPriceSeparators(message);
        Matcher betweenMatcher = BETWEEN_PRICE.matcher(normalized);
        if (betweenMatcher.find()) {
            return new PriceRange(parsePrice(betweenMatcher.group(1)), parsePrice(betweenMatcher.group(2)));
        }

        Matcher maxMatcher = MAX_PRICE.matcher(normalized);
        Matcher minMatcher = MIN_PRICE.matcher(normalized);
        BigDecimal maxPrice = maxMatcher.find() ? parsePrice(maxMatcher.group(1)) : null;
        BigDecimal minPrice = minMatcher.find() ? parsePrice(minMatcher.group(1)) : null;
        return new PriceRange(minPrice, maxPrice);
    }

    private static String removePriceClauses(String input) {
        String withoutBetween = BETWEEN_PRICE.matcher(input).replaceAll(" ");
        return MAX_PRICE.matcher(MIN_PRICE.matcher(withoutBetween).replaceAll(" ")).replaceAll(" ");
    }

    private static BigDecimal parsePrice(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String compact = value.trim().replaceAll("\\s+", "");
        if (compact.isBlank()) {
            return null;
        }
        int comma = compact.lastIndexOf(',');
        int dot = compact.lastIndexOf('.');
        if (comma >= 0 && dot >= 0) {
            compact = comma > dot
                    ? compact.replace(".", "").replace(',', '.')
                    : compact.replace(",", "");
        } else if (comma >= 0) {
            compact = compact.substring(comma + 1).length() <= 2
                    ? compact.replace(',', '.')
                    : compact.replace(",", "");
        } else if (dot >= 0 && compact.substring(dot + 1).length() == 3) {
            compact = compact.replace(".", "");
        }
        try {
            return new BigDecimal(compact);
        } catch (NumberFormatException exception) {
            return null;
        }
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

    private static String normalizeKeepingPriceSeparators(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private record PriceRange(BigDecimal minPrice, BigDecimal maxPrice) {

        private boolean isEmpty() {
            return minPrice == null && maxPrice == null;
        }
    }
}
