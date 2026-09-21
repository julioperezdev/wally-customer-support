package com.wally.customersupport.cart.application.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.wally.customersupport.catalog.application.service.CatalogQueryParser;
import com.wally.customersupport.catalog.domain.model.CatalogQuery;

public final class CartCommandParser {

    public enum Action {
        NONE,
        ADD,
        VIEW,
        REMOVE,
        CLEAR,
        REVIEW_CHECKOUT,
        CONFIRM,
        CANCEL_CHECKOUT,
        DEFER
    }

    public record Command(Action action, CatalogQuery query, int quantity) {
    }

    private static final Pattern VIEW = Pattern.compile("\\b(carrito|cesta)\\b");
    private static final Pattern CLEAR = Pattern.compile("\\b(vaciar|limpiar|borrar)\\b.*\\b(carrito|cesta)\\b");
    private static final Pattern CANCEL_CHECKOUT = Pattern.compile(
            "\\b(cancelar|cancela|anular|anula)\\b.*\\b(compra|pedido|checkout|pago|link)\\b|"
                    + "\\b(cambiar|modificar)\\b.*\\b(carrito|compra|pedido)\\b");
    private static final Pattern REVIEW_CHECKOUT = Pattern.compile(
            "\\b(?:quiero|deseo|necesito)\\s+pagar\\b|"
                    + "\\bestoy\\s+list[oa]\\s+para\\s+pagar\\b|"
                    + "\\b(?:quiero|deseo|necesito)\\s+(?:hacer|realizar|iniciar)\\s+(?:el\\s+)?(?:pago|checkout)\\b");
    private static final Pattern CONFIRM = Pattern.compile(
            "\\b(confirmar|confirmo|confirmá|confirmame|finalizar|finalizo)\\b.*\\b(compra|pedido|carrito|pago)?\\b|"
                    + "\\b(generar|genera|generame|pasame)\\b.*\\b(link|enlace)\\b.*\\b(pago|carrito|compra)?\\b");
    private static final Pattern ADD = Pattern.compile(
            "\\b(agregar|agrega|agregame|agregue|sumar|suma|sumame|sume|anadir|anade|añadir|añade)\\b|"
                    + "\\b(al|a)\\s+(mi\\s+)?carrito\\b");
    private static final Pattern IMPLICIT_ADD = Pattern.compile(
            "\\b(?:tambien|ademas)\\s+(?:quiero|necesito|me\\s+llevo)\\b");
    private static final Pattern REMOVE = Pattern.compile(
            "\\b(sacar|saca|quita|quitar|eliminar|elimina|bajar|restar|resta|remove)\\b");
    private static final Pattern QUANTITY = Pattern.compile(
            "\\b(?:agregar|agrega|agregame|agregue|sumar|suma|sumame|sume|anadir|anade|añadir|añade|"
                    + "sacar|saca|quita|quitar|eliminar|elimina|bajar|restar|resta|remove)\\s+([1-9][0-9]?)\\b|"
                    + "\\b(?:quiero|necesito|me\\s+llevo)\\s+([1-9][0-9]?)\\b|"
                    + "\\b([1-9][0-9]?)\\s*(?:unidades?|u)\\b|\\bx\\s*([1-9][0-9]?)\\b");
    private static final Pattern DEFER = Pattern.compile(
            "\\bno\\s+(?:quiero|necesito|voy\\s+a)\\s+(?:comprar|comprarla|comprarlo|pagar|llevar|llevarme)\\b|"
                    + "\\btodavia\\s+no\\s+(?:quiero\\s+)?(?:comprar|pagar|llevar)\\b|"
                    + "\\bno\\s+(?:la|lo)\\s+(?:compro|llevo)\\b");

    private CartCommandParser() {
    }

    public static Command parse(String message) {
        return parse(message, false);
    }

    /**
     * Parses an implicit addition such as "también quiero un buzo" only when
     * the caller has already established that the conversation is managing a
     * cart. A plain "quiero un buzo" remains a catalog search.
     */
    public static Command parse(String message, boolean allowImplicitAdd) {
        if (message == null || message.isBlank()) {
            return new Command(Action.NONE, null, 1);
        }
        String normalized = normalize(message);
        if (DEFER.matcher(normalized).find()) {
            return new Command(Action.DEFER, null, 1);
        }
        if (CLEAR.matcher(normalized).find()) {
            return new Command(Action.CLEAR, null, 1);
        }
        if (CANCEL_CHECKOUT.matcher(normalized).find()) {
            return new Command(Action.CANCEL_CHECKOUT, null, 1);
        }
        if (REVIEW_CHECKOUT.matcher(normalized).find()) {
            return new Command(Action.REVIEW_CHECKOUT, null, 1);
        }
        if (CONFIRM.matcher(normalized).find()) {
            return new Command(Action.CONFIRM, null, 1);
        }
        if (ADD.matcher(normalized).find()) {
            return new Command(Action.ADD, itemQuery(normalized), quantity(normalized));
        }
        if (allowImplicitAdd && IMPLICIT_ADD.matcher(normalized).find()) {
            return new Command(Action.ADD, itemQuery(normalized), quantity(normalized));
        }
        if (REMOVE.matcher(normalized).find()) {
            return new Command(Action.REMOVE, itemQuery(normalized), quantity(normalized));
        }
        if (VIEW.matcher(normalized).find()) {
            return new Command(Action.VIEW, null, 1);
        }
        return new Command(Action.NONE, null, 1);
    }

    public static boolean isRecognized(String message) {
        return parse(message).action() != Action.NONE;
    }

    public static boolean isImplicitAddRequest(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        return IMPLICIT_ADD.matcher(normalize(message)).find();
    }

    private static CatalogQuery itemQuery(String normalized) {
        String itemMessage = normalized
                .replaceAll("\\b(?:agregar|agrega|agregame|agregue|sumar|suma|sumame|sume|anadir|anade|añadir|añade|"
                        + "sacar|saca|quita|quitar|eliminar|elimina|bajar|restar|resta|remove)\\b", " ")
                .replaceAll("\\b(?:quiero|necesito|llevo|tambien|también|ademas|además|otro|otra|"
                        + "al|a|mi|el|la|un|una|por|favor|carrito|cesta)\\b", " ")
                .replaceAll("\\b[1-9][0-9]?\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return CatalogQueryParser.parse(itemMessage).orElse(CatalogQuery.empty());
    }

    private static int quantity(String normalized) {
        Matcher matcher = QUANTITY.matcher(normalized);
        if (!matcher.find()) {
            return 1;
        }
        for (int index = 1; index <= matcher.groupCount(); index++) {
            if (matcher.group(index) != null) {
                try {
                    return Integer.parseInt(matcher.group(index));
                } catch (NumberFormatException ignored) {
                    return 1;
                }
            }
        }
        return 1;
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
