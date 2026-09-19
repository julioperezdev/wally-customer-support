package com.wally.customersupport.conversation.application.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Provider-neutral contracts for every business capability that can appear in
 * an agent definition.
 *
 * <p>The executable registry may contain only the capabilities implemented in
 * the current runtime. Keeping the complete contract catalog separate lets the
 * agent registry validate persisted allowlists before a provider adapter is
 * introduced, without creating fake executable tools.</p>
 */
public final class WcsToolContractCatalog {

    public static final String CATALOG_SEARCH = "catalog.search";
    public static final String CATALOG_STOCK = "catalog.stock";
    public static final String KNOWLEDGE_RETRIEVE = "knowledge.retrieve";
    public static final String CONVERSATION_STATE = "conversation.state";
    public static final String CART_MANAGE = "cart.manage";
    public static final String CHECKOUT_CREATE = "checkout.create";
    public static final String HUMAN_HANDOFF = "human-handoff";
    public static final String SAFE_FALLBACK = "safe-fallback";

    private static final Map<String, WcsToolDescriptor> CONTRACTS = contracts();

    private WcsToolContractCatalog() {
    }

    public static List<WcsToolDescriptor> all() {
        return List.copyOf(CONTRACTS.values());
    }

    public static Optional<WcsToolDescriptor> find(String name) {
        return Optional.ofNullable(CONTRACTS.get(name));
    }

    private static Map<String, WcsToolDescriptor> contracts() {
        Map<String, WcsToolDescriptor> contracts = new LinkedHashMap<>();
        put(contracts, CatalogSearchTool.DESCRIPTOR);
        put(contracts, descriptor(
                CATALOG_STOCK,
                "Consulta stock de una variante validada del catálogo.",
                "catalog-stock-input-v1",
                "catalog-stock-output-v1",
                "catalog.stock"));
        put(contracts, descriptor(
                KNOWLEDGE_RETRIEVE,
                "Recupera evidencia documental desde la Knowledge Base autorizada.",
                "knowledge-input-v1",
                "knowledge-output-v1",
                "knowledge.retrieve"));
        put(contracts, descriptor(
                CONVERSATION_STATE,
                "Lee o actualiza el estado tipado de una conversación con ownership validado.",
                "conversation-state-input-v1",
                "conversation-state-output-v1",
                "conversation.state"));
        put(contracts, descriptor(
                CART_MANAGE,
                "Gestiona una operación validada del carrito del cliente.",
                "cart-input-v1",
                "cart-output-v1",
                "cart.manage"));
        put(contracts, descriptor(
                CHECKOUT_CREATE,
                "Crea un checkout idempotente a partir de un carrito confirmado.",
                "checkout-input-v1",
                "checkout-output-v1",
                "checkout.create"));
        put(contracts, descriptor(
                HUMAN_HANDOFF,
                "Crea o actualiza una solicitud de atención humana con contexto sanitizado.",
                "handoff-input-v1",
                "handoff-output-v1",
                HUMAN_HANDOFF));
        put(contracts, descriptor(
                SAFE_FALLBACK,
                "Aplica una respuesta segura cuando la intención o los datos no son confiables.",
                "safety-input-v1",
                "safety-output-v1",
                SAFE_FALLBACK));
        return Map.copyOf(contracts);
    }

    private static WcsToolDescriptor descriptor(
            String name,
            String description,
            String inputVersion,
            String outputVersion,
            String capability) {
        return new WcsToolDescriptor(
                name,
                description,
                inputVersion,
                "{\"type\":\"object\",\"additionalProperties\":false}",
                outputVersion,
                "{\"type\":\"object\",\"additionalProperties\":false}",
                capability);
    }

    private static void put(Map<String, WcsToolDescriptor> contracts, WcsToolDescriptor descriptor) {
        if (contracts.putIfAbsent(descriptor.name(), descriptor) != null) {
            throw new IllegalStateException("Duplicate WCS tool contract: " + descriptor.name());
        }
    }
}
