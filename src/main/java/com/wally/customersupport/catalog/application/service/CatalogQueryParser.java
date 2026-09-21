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

    static final Pattern SKU = Pattern.compile("\\b[a-z]{2}(?:-[a-z0-9]+){2,}\\b");
    static final Pattern SIZE = Pattern.compile("\\b(?:talle|talla|tamano|size)?\\s*(xxl|xl|xs|l|m|s)\\b");
    static final Pattern COLOR = Pattern.compile(
            "\\b(negro|negra|negros|negras|blanco|blanca|blancos|blancas|gris|grises|azul|azules|"
                    + "rojo|roja|rojos|rojas|verde|verdes)\\b");
    private static final Pattern PRODUCT_TYPE = Pattern.compile("\\b(remera|remeras|buzo|buzos|campera|camperas)\\b");
    private static final Pattern MAX_PRICE = Pattern.compile(
            "\\b(?:menos\\s+de|menor\\s+(?:que|a)?|mas\\s+barat(?:o|a|os|as)\\s+(?:que|a)|por\\s+debajo\\s+de|hasta|como\\s+maximo(?:\\s+de)?|maximo(?:\\s+de)?|tope(?:\\s+de)?)"
                    + "\\s*\\$?\\s*([0-9][0-9\\s.,]*)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MIN_PRICE = Pattern.compile(
            "\\b(?:mas\\s+de|mayor\\s+(?:que|a)?|por\\s+encima\\s+de|desde|a\\s+partir\\s+de|como\\s+minimo(?:\\s+de)?|minimo(?:\\s+de)?)"
                    + "\\s*\\$?\\s*([0-9][0-9\\s.,]*)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BETWEEN_PRICE = Pattern.compile(
            "\\bentre\\s*\\$?\\s*([0-9][0-9\\s.,]*)\\s+(?:y|a)\\s*\\$?\\s*([0-9][0-9\\s.,]*)",
            Pattern.CASE_INSENSITIVE);
    static final Pattern REFINEMENT_MARKER = Pattern.compile(
            "\\b(que|sea|tambien|también|ahora|solo|sólo|pero|mejor|tipo)\\b");
    private static final Pattern CONTINUATION_MARKER = Pattern.compile(
            "\\b(opcion|opciones|alternativa|alternativas|mostrame|muestrame|mostrar|"
                    + "anterior|anteriores|antes|esto|este|estos|ese|eso|esa|esas|mismo|misma|"
                    + "ultimo|ultima|arriba|abajo|asi|algo asi|"
                    + "algo como|lo de antes|mas barato|mas barata|mas baratos|mas baratas|"
                    + "menor precio|economico|economica)\\b");
    private static final Pattern CHEAPER_CONTINUATION_MARKER = Pattern.compile(
            "\\b(mas barato|mas barata|mas baratos|mas baratas|menor precio|"
                    + "economico|economica|mas conveniente)\\b");
    private static final Pattern WARMTH_MARKER = Pattern.compile(
            "\\b(frio|abrigo|abrigado|abrigada|invierno|para el frio|para abrigo|"
                    + "para abrigarse|para el invierno)\\b");
    private static final Pattern SHIPPING_MARKER = Pattern.compile(
            "\\b(envio|envios|entrega|despacho)\\b");
    private static final Pattern PURCHASE_MARKER = Pattern.compile(
            "\\b(comprar|comprarla|comprarlo|comprame|compro|adquirir|llevarme|pagar|pagarla|"
                    + "pasame\\s+(?:el\\s+)?(?:link|enlace)|"
                    + "generame\\s+(?:el\\s+)?(?:link|enlace)|"
                    + "(?:link|enlace)\\s+de\\s+pago|"
                    + "me\\s+(?:(?:la|lo)\\s+)?llevo)\\b");
    private static final Pattern NEGATIVE_PURCHASE_MARKER = Pattern.compile(
            "\\bno\\s+(?:quiero|necesito|voy\\s+a)\\s+(?:comprar|comprarla|comprarlo|pagar|llevar|llevarme)\\b|"
                    + "\\btodavia\\s+no\\s+(?:quiero\\s+)?(?:comprar|pagar|llevar)\\b|"
                    + "\\bno\\s+(?:la|lo)\\s+(?:compro|llevo)\\b|"
                    + "\\bno\\s+(?:comprar|comprarla|comprarlo|pagar|pagarla|llevar|llevarme|adquirir)\\b");
    static final Pattern PURCHASE_QUANTITY_AFTER_VERB = Pattern.compile(
            "\\b(?:quiero|necesito|comprar|llevar|llevarme)\\s+([1-9][0-9]?)\\b");
    static final Pattern PURCHASE_QUANTITY_WITH_UNIT = Pattern.compile(
            "\\b([1-9][0-9]?)\\s*(?:unidades?|u)\\b");
    static final Pattern PURCHASE_QUANTITY_X = Pattern.compile(
            "\\bx\\s*([1-9][0-9]?)\\b");
    private static final Pattern UNSUPPORTED_CATALOG_CATEGORY = Pattern.compile(
            "\\b(gorra|gorras|zapatilla|zapatillas|zapato|zapatos|pantalon|pantalones|"
                    + "camisa|camisas|short|shorts|accesorio|accesorios|bufanda|bufandas|"
                    + "media|medias)\\b");
    static final Pattern CATALOG_MARKER = Pattern.compile(
            "\\b(remera|remeras|buzo|buzos|campera|camperas|producto|productos|catalogo|stock|disponible|"
                    + "disponibilidad|talle|talla|tamano|size|sku|precio|precios|cuesta|cueste|color|barato|barata|"
                    + "caro|cara|menos|mas|hasta|debajo|encima|entre|frio|abrigo|invierno|"
                    + "tienen|tienes|tenes|hay|ofrece|ofrecen|dispone|disponen|"
                    + "gorra|gorras|zapatilla|zapatillas|zapato|zapatos|pantalon|pantalones|"
                    + "camisa|camisas|short|shorts|accesorio|accesorios|bufanda|bufandas|"
                    + "media|medias)\\b");
    private static final Pattern GENERAL_CATALOG_REQUEST = Pattern.compile(
            "\\b(?:que|cuales?)\\s+(?:productos?|opciones?)\\s+(?:tienen|hay|ofrecen|venden|vendes|tenes|tienes)\\b|"
                    + "\\b(?:que|cuales?)\\s+(?:venden|vendes|ofrecen|tenes|tienes)\\b|"
                    + "\\b(?:tenes|tienes|venden|vendes|ofrecen|ofrece|hay)\\s+(?:algo\\s+de\\s+)?ropa\\b|"
                    + "\\b(?:que|cuales?)\\s+ropa\\s+(?:tienen|hay|ofrecen|venden|vendes|tenes|tienes)\\b|"
                    + "\\b(?:mostrame|muestrame|mostrar)\\s+(?:todo|el\\s+catalogo|los\\s+productos)\\b");
    private static final Pattern AVAILABILITY_FOLLOW_UP = Pattern.compile(
            "\\b(disponible|disponibilidad|hay stock|tiene stock)\\b");
    private static final Pattern PRICE_FOLLOW_UP = Pattern.compile(
            "\\b(cuanto|cuesta|precio|sale|valor)\\b");
    private static final Pattern SIZE_FOLLOW_UP = Pattern.compile(
            "\\b(que talle|cual talle|que talla|cual talla|que tamano|cual tamano|que size)\\b");
    private static final Pattern COLOR_FOLLOW_UP = Pattern.compile(
            "\\b(que color|cual color|en que color)\\b");
    private static final Pattern STOP_WORDS = Pattern.compile(
            "\\b(tienen|tienes|tenemos|tenes|hay|ofrece|ofrecen|dispone|disponen|venden|vende|quiero|busco|necesito|soy|tengo|estoy|ropa|una|un|el|la|los|las|del|de|en|con|"
                    + "vendes|q|onda|"
                    + "por|para|favor|me|podes|pueden|puedo|cuanto|cuál|cual|es|esta|tiene|stock|disponible|"
                    + "disponibilidad|precio|precios|color|talle|talla|tamano|size|sku|productos?|catalogo|"
                    + "este|estos|esto|algo|"
            + "alguna|alguno|que|qué|sea|estilo|mi|ahora|solo|sólo|tambien|también|pero|mejor|tipo|"
                    + "frio|abrigo|abrigado|abrigada|invierno|lindo|linda|bonito|bonita|"
                    + "cuesta|cueste|menos|mas|barato|barata|caro|cara|hasta|debajo|encima|entre|"
                    + "opcion|opciones|alternativa|alternativas|mostrame|muestrame|mostrar|anterior|"
                    + "anteriores|antes|eso|esa|esas|asi|algo|lo|y|como|hace|hacen|se|envio|envios|entrega|"
                    + "despacho|pesos?|ars|comprar|comprarla|comprarlo|comprame|compro|adquirir|llevarme|"
                    + "llevar|llevarme|llevo|pasame|generame|link|enlace|pago|pagar|pagarla|compra|unidades?|u|"
                    + "suma|sumame|agrega|agregame|agregar|anade|anademe|anadir|al|carrito|"
                    + "dos|tres|cuatro|cinco|ese|esos|misma|mismo)\\b");

    private static final CatalogConversationQueryResolver CONVERSATION_RESOLVER =
            new CatalogConversationQueryResolver();

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
        if (productType == null && WARMTH_MARKER.matcher(normalized).find()) {
            productType = "abrigo";
        }
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
        return CONVERSATION_RESOLVER.parseConversation(recentMessages, latestMessage);
    }

    public static boolean isContextualContinuation(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        return CONTINUATION_MARKER.matcher(normalize(message)).find();
    }

    /**
     * Detects a relative price refinement such as "algo como lo de antes pero
     * más barato". The actual cheapest variant is always selected from the
     * catalog facts, never inferred by the language model.
     */
    public static boolean isCheaperContinuation(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        return CHEAPER_CONTINUATION_MARKER.matcher(normalize(message)).find();
    }

    /**
     * Detects only a relative cheaper request. A numeric constraint such as
     * "más barato que 20.000" must remain a normal max-price filter and must
     * not be reduced to the single cheapest result.
     */
    public static boolean isRelativeCheaperContinuation(String message) {
        if (!isCheaperContinuation(message)) {
            return false;
        }
        return parse(message)
                .map(query -> query.minPrice() == null && query.maxPrice() == null)
                .orElse(true);
    }

    /**
     * Uses a stable internal category for natural-language requests about
     * winter or warm clothing. The catalog service expands it to the actual
     * supported product types (buzo and campera).
     */
    public static boolean isWarmthSelection(CatalogQuery query) {
        return query != null && "abrigo".equalsIgnoreCase(query.productType());
    }

    public static boolean isGeneralCatalogRequest(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        return GENERAL_CATALOG_REQUEST.matcher(normalize(message)).find();
    }

    /**
     * Returns true for a turn that only adds filters to the active selection,
     * such as "quiero la talla M" or "que sea negro". These turns must be
     * resolved against conversation state before accepting a model-provided
     * catalog query.
     */
    public static boolean isFilterOnlyRefinement(String message) {
        return parse(message)
                .filter(query -> !query.isEmpty() && !query.hasPrimarySelector())
                .isPresent();
    }

    public static boolean isShippingQuestion(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        return SHIPPING_MARKER.matcher(normalize(message)).find();
    }

    /**
     * Detects an explicit customer request to start checkout. Informational
     * interest such as "me gusta" is intentionally not enough to create an
     * order or payment link.
     */
    public static boolean isPurchaseRequest(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = normalize(message);
        return !isPurchaseDeferral(message)
                && PURCHASE_MARKER.matcher(normalized).find();
    }

    /** Returns true when the customer explicitly postpones the purchase. */
    public static boolean isPurchaseDeferral(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        return NEGATIVE_PURCHASE_MARKER.matcher(normalize(message)).find();
    }

    /**
     * Reconstructs the most recent bounded catalog selection for an explicit
     * purchase request. The current turn can refine the previous selection,
     * but a purchase request without one unambiguous selection remains empty.
     */
    public static Optional<CatalogQuery> parsePurchaseConversation(
            List<String> recentMessages,
            String latestMessage) {
        return CONVERSATION_RESOLVER.parsePurchaseConversation(recentMessages, latestMessage);
    }

    /** Returns the requested quantity, defaulting to one for checkout. */
    public static int purchaseQuantity(String message) {
        return CONVERSATION_RESOLVER.purchaseQuantity(message);
    }

    public static boolean isUnsupportedCatalogCategory(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        return UNSUPPORTED_CATALOG_CATEGORY.matcher(normalize(message)).find();
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

    static Optional<CatalogQuery> parseRefinement(String message) {
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

    static PriceRange extractPriceRange(String message) {
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
            case "negra", "negros", "negras" -> "negro";
            case "blanca", "blancos", "blancas" -> "blanco";
            case "grises" -> "gris";
            case "azules" -> "azul";
            case "roja", "rojos", "rojas" -> "rojo";
            case "verdes" -> "verde";
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

    static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                // Small, explicit store-vocabulary corrections keep common
                // chat typos bounded; this is not fuzzy matching and never
                // invents a product name.
                .replaceAll("\\bremra\\b", "remera")
                .replaceAll("\\bremras\\b", "remeras")
                .replaceAll("\\bbuso\\b", "buzo")
                .replaceAll("\\bbusos\\b", "buzos")
                .replaceAll("\\btaya\\b", "talla")
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

    record PriceRange(BigDecimal minPrice, BigDecimal maxPrice) {

        boolean isEmpty() {
            return minPrice == null && maxPrice == null;
        }
    }
}
