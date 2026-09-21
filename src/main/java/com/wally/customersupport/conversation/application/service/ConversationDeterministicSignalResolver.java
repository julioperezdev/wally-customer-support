package com.wally.customersupport.conversation.application.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import com.wally.customersupport.catalog.application.service.CatalogConversationQueryResolver;
import com.wally.customersupport.catalog.application.service.CatalogQueryParser;
import com.wally.customersupport.catalog.domain.model.CatalogQuery;
import com.wally.customersupport.conversation.domain.model.ConversationAction;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.conversation.domain.model.ConversationIntent;
import com.wally.customersupport.conversation.domain.model.ConversationIntentDecision;

/**
 * Resolves only unambiguous, read-only message signals when the model proposal
 * is missing or conflicts with the customer's words.
 *
 * <p>This is a safety net, not a replacement for the LLM. It never resolves a
 * purchase or cart operation and it never invents catalog facts. Its purpose
 * is to keep a clear message such as "quiero una remera" usable when the
 * provider returns an unknown or incomplete structured proposal.</p>
 */
public final class ConversationDeterministicSignalResolver {

    private static final double DETERMINISTIC_CONFIDENCE = 0.99;
    private static final Set<String> GREETINGS = Set.of(
            "hola", "buen dia", "buenos dias", "buenas tardes", "buenas noches",
            "hey", "hello", "hi");
    private static final Pattern HUMAN_HANDOFF = Pattern.compile(
            "\\b(humano|humana|persona|agente|asesor|asesora|atencion humana|atencion personal)\\b");
    private static final Pattern BUSINESS_HOURS = Pattern.compile(
            "\\b(horario|horarios|abren|abrir|abierto|abierta|cierran|cerrado|cerrada|"
                    + "a que hora|que hora)\\b");
    private static final Pattern SHIPPING_POLICY = Pattern.compile(
            "\\b(envio|envios|entrega|entregas|despacho|despachos|mandan|llega|llegan)\\b");
    private static final Pattern PAYMENT_POLICY = Pattern.compile(
            "\\b(medio de pago|medios de pago|tarjeta|tarjetas|transferencia|transferencias|"
                    + "mercado pago|pago|pagos)\\b");
    private static final Pattern CHANGE_POLICY = Pattern.compile(
            "\\b(cambio|cambios|cambiar|cambias|modificar|modificacion)\\b");
    private static final Pattern RETURN_POLICY = Pattern.compile(
            "\\b(devolucion|devoluciones|devolver|devuelvo|reembolso|reembolsos|reintegro|reintegros)\\b");
    private static final Pattern GENERAL_SUPPORT = Pattern.compile(
            "\\b(ubicacion|ubicaciones|ubicados|ubicadas|direccion|direcciones|sede|sedes|"
                    + "contacto|telefono|telefonos|donde estan|donde queda|donde quedan)\\b");

    private final CatalogConversationQueryResolver catalogQueryResolver =
            new CatalogConversationQueryResolver();

    public Optional<ConversationIntentDecision> resolve(ConversationContext context) {
        if (context == null || context.latestMessage() == null || context.latestMessage().isBlank()) {
            return Optional.empty();
        }

        String normalized = normalize(context.latestMessage());
        if (isGreetingOnly(normalized)) {
            return Optional.of(decision(ConversationIntent.GREETING, ConversationAction.GREETING,
                    null, null));
        }
        if (HUMAN_HANDOFF.matcher(normalized).find()) {
            return Optional.of(decision(ConversationIntent.HUMAN_HANDOFF, ConversationAction.HUMAN_HANDOFF,
                    null, null));
        }
        if (BUSINESS_HOURS.matcher(normalized).find()) {
            return Optional.of(decision(ConversationIntent.BUSINESS_HOURS, ConversationAction.BUSINESS_HOURS,
                    null, null));
        }

        Optional<CatalogQuery> catalogQuery = catalogQuery(context);
        if (catalogQuery.isPresent()) {
            return Optional.of(decision(
                    ConversationIntent.CATALOG_SEARCH,
                    ConversationAction.CATALOG_SEARCH,
                    catalogQuery.get(),
                    null));
        }

        String policyKey = policyKey(normalized);
        if (policyKey != null) {
            return Optional.of(decision(ConversationIntent.POLICY_QUERY, ConversationAction.POLICY_QUERY,
                    null, policyKey));
        }
        if (GENERAL_SUPPORT.matcher(normalized).find()) {
            return Optional.of(decision(ConversationIntent.GENERAL_SUPPORT, ConversationAction.GENERAL_SUPPORT,
                    null, null));
        }
        return Optional.empty();
    }

    private Optional<CatalogQuery> catalogQuery(ConversationContext context) {
        String latest = context.latestMessage();
        boolean generalRequest = CatalogQueryParser.isGeneralCatalogRequest(latest);
        boolean unsupportedCategory = CatalogQueryParser.isUnsupportedCatalogCategory(latest);
        boolean unsupportedAttribute = CatalogQueryParser.isUnsupportedCatalogAttribute(latest);
        boolean followUp = CatalogQueryParser.followUpKind(latest) != CatalogQueryParser.FollowUpKind.NONE;
        boolean refinement = CatalogQueryParser.isFilterOnlyRefinement(latest)
                || CatalogQueryParser.isContextualContinuation(latest);
        Optional<CatalogQuery> parsed = catalogQueryResolver.parseConversation(
                context.recentMessages(), latest);
        Optional<CatalogQuery> currentTurn = CatalogQueryParser.parse(latest);

        if (!generalRequest && !unsupportedCategory && !unsupportedAttribute && !followUp && !refinement
                && parsed.filter(query -> !query.isEmpty()).isEmpty()
                && currentTurn.filter(query -> !query.isEmpty()).isEmpty()) {
            return Optional.empty();
        }
        if (generalRequest && !CatalogQueryParser.isContextualContinuation(latest)) {
            return Optional.of(CatalogQuery.empty());
        }
        return parsed.or(() -> currentTurn).or(() -> Optional.of(CatalogQuery.empty()));
    }

    private static ConversationIntentDecision decision(
            ConversationIntent intent,
            ConversationAction action,
            CatalogQuery query,
            String policyKey) {
        return new ConversationIntentDecision(
                intent,
                action,
                DETERMINISTIC_CONFIDENCE,
                query,
                policyKey,
                1,
                java.util.List.of());
    }

    private static String policyKey(String normalized) {
        if (SHIPPING_POLICY.matcher(normalized).find()) {
            return "shipping";
        }
        if (RETURN_POLICY.matcher(normalized).find()) {
            return "returns";
        }
        if (CHANGE_POLICY.matcher(normalized).find()) {
            return "changes";
        }
        if (PAYMENT_POLICY.matcher(normalized).find()) {
            return "payments";
        }
        return null;
    }

    private static boolean isGreetingOnly(String normalized) {
        return GREETINGS.contains(normalized);
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
