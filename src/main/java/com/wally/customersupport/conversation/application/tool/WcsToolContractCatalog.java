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
                "{\"type\":\"object\",\"properties\":{\"sku\":{\"type\":\"string\",\"minLength\":1,\"maxLength\":64}},\"required\":[\"sku\"],\"additionalProperties\":false}",
                "catalog-stock-output-v1",
                "{\"type\":\"object\",\"properties\":{\"status\":{\"type\":\"string\",\"enum\":[\"AVAILABLE\",\"OUT_OF_STOCK\",\"NOT_FOUND\"]},\"sku\":{\"type\":\"string\"},\"stock\":{\"type\":\"integer\",\"minimum\":0}},\"required\":[\"status\",\"sku\",\"stock\"],\"additionalProperties\":false}",
                "catalog.stock"));
        put(contracts, descriptor(
                KNOWLEDGE_RETRIEVE,
                "Recupera evidencia documental desde la Knowledge Base autorizada.",
                "knowledge-input-v1",
                "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\",\"minLength\":1,\"maxLength\":1000},\"topic\":{\"type\":[\"string\",\"null\"],\"enum\":[\"hours\",\"location\",\"shipping\",\"payments\",\"changes\",\"returns\",\"faq\",null]}},\"required\":[\"query\",\"topic\"],\"additionalProperties\":false}",
                "knowledge-output-v1",
                "{\"type\":\"object\",\"properties\":{\"status\":{\"type\":\"string\",\"enum\":[\"GROUNDED\",\"NO_EVIDENCE\",\"ERROR\"]},\"evidenceCount\":{\"type\":\"integer\",\"minimum\":0},\"groundingScore\":{\"type\":[\"number\",\"null\"],\"minimum\":0,\"maximum\":1}},\"required\":[\"status\",\"evidenceCount\",\"groundingScore\"],\"additionalProperties\":false}",
                "knowledge.retrieve"));
        put(contracts, descriptor(
                CONVERSATION_STATE,
                "Lee o actualiza el estado tipado de una conversación con ownership validado.",
                "conversation-state-input-v1",
                "{\"type\":\"object\",\"properties\":{\"operation\":{\"type\":\"string\",\"enum\":[\"READ\",\"MERGE_SELECTION\",\"RESET\"]},\"selectionField\":{\"type\":[\"string\",\"null\"],\"enum\":[\"name\",\"sku\",\"productType\",\"size\",\"color\",\"minPrice\",\"maxPrice\",null]},\"selectionValue\":{\"type\":[\"string\",\"null\"],\"maxLength\":128}},\"required\":[\"operation\",\"selectionField\",\"selectionValue\"],\"additionalProperties\":false}",
                "conversation-state-output-v1",
                "{\"type\":\"object\",\"properties\":{\"status\":{\"type\":\"string\",\"enum\":[\"READ\",\"UPDATED\",\"RESET\",\"NOT_FOUND\"]},\"stateVersion\":{\"type\":\"integer\",\"minimum\":0},\"selectionFieldCount\":{\"type\":\"integer\",\"minimum\":0}},\"required\":[\"status\",\"stateVersion\",\"selectionFieldCount\"],\"additionalProperties\":false}",
                "conversation.state"));
        put(contracts, descriptor(
                CART_MANAGE,
                "Gestiona una operación validada del carrito del cliente.",
                "cart-input-v1",
                "{\"type\":\"object\",\"properties\":{\"operation\":{\"type\":\"string\",\"enum\":[\"ADD\",\"VIEW\",\"REMOVE\",\"CLEAR\",\"REVIEW\"]},\"sku\":{\"type\":[\"string\",\"null\"],\"maxLength\":64},\"quantity\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":100}},\"required\":[\"operation\",\"sku\",\"quantity\"],\"additionalProperties\":false}",
                "cart-output-v1",
                "{\"type\":\"object\",\"properties\":{\"status\":{\"type\":\"string\",\"enum\":[\"UPDATED\",\"VIEWED\",\"CLEARED\",\"REJECTED\"]},\"itemCount\":{\"type\":\"integer\",\"minimum\":0},\"total\":{\"type\":\"number\",\"minimum\":0},\"requiresConfirmation\":{\"type\":\"boolean\"}},\"required\":[\"status\",\"itemCount\",\"total\",\"requiresConfirmation\"],\"additionalProperties\":false}",
                "cart.manage"));
        put(contracts, descriptor(
                CHECKOUT_CREATE,
                "Crea un checkout idempotente a partir de un carrito confirmado.",
                "checkout-input-v1",
                "{\"type\":\"object\",\"properties\":{\"confirmed\":{\"type\":\"boolean\",\"const\":true},\"cartVersion\":{\"type\":\"integer\",\"minimum\":1}},\"required\":[\"confirmed\",\"cartVersion\"],\"additionalProperties\":false}",
                "checkout-output-v1",
                "{\"type\":\"object\",\"properties\":{\"status\":{\"type\":\"string\",\"enum\":[\"CREATED\",\"REUSED\",\"REJECTED\"]},\"orderCreated\":{\"type\":\"boolean\"},\"paymentLinkCreated\":{\"type\":\"boolean\"}},\"required\":[\"status\",\"orderCreated\",\"paymentLinkCreated\"],\"additionalProperties\":false}",
                "checkout.create"));
        put(contracts, descriptor(
                HUMAN_HANDOFF,
                "Crea o actualiza una solicitud de atención humana con contexto sanitizado.",
                "handoff-input-v1",
                "{\"type\":\"object\",\"properties\":{\"reason\":{\"type\":\"string\",\"enum\":[\"CUSTOMER_REQUEST\",\"LOW_CONFIDENCE\",\"PAYMENT_ISSUE\",\"OTHER\"]},\"priority\":{\"type\":\"string\",\"enum\":[\"LOW\",\"NORMAL\",\"HIGH\"]},\"contextAvailable\":{\"type\":\"boolean\"}},\"required\":[\"reason\",\"priority\",\"contextAvailable\"],\"additionalProperties\":false}",
                "handoff-output-v1",
                "{\"type\":\"object\",\"properties\":{\"status\":{\"type\":\"string\",\"enum\":[\"CREATED\",\"REUSED\",\"REJECTED\"]},\"priority\":{\"type\":\"string\"},\"contextIncluded\":{\"type\":\"boolean\"}},\"required\":[\"status\",\"priority\",\"contextIncluded\"],\"additionalProperties\":false}",
                HUMAN_HANDOFF));
        put(contracts, descriptor(
                SAFE_FALLBACK,
                "Aplica una respuesta segura cuando la intención o los datos no son confiables.",
                "safety-input-v1",
                "{\"type\":\"object\",\"properties\":{\"reason\":{\"type\":\"string\",\"enum\":[\"UNKNOWN_INTENT\",\"LOW_CONFIDENCE\",\"INVALID_TOOL_INPUT\",\"TOOL_UNAVAILABLE\",\"EXECUTION_FAILED\"]}},\"required\":[\"reason\"],\"additionalProperties\":false}",
                "safety-output-v1",
                "{\"type\":\"object\",\"properties\":{\"status\":{\"type\":\"string\",\"enum\":[\"FALLBACK\"]},\"handoffSuggested\":{\"type\":\"boolean\"}},\"required\":[\"status\",\"handoffSuggested\"],\"additionalProperties\":false}",
                SAFE_FALLBACK));
        return Map.copyOf(contracts);
    }

    private static WcsToolDescriptor descriptor(
            String name,
            String description,
            String inputVersion,
            String inputSchema,
            String outputVersion,
            String outputSchema,
            String capability) {
        return new WcsToolDescriptor(
                name,
                description,
                inputVersion,
                inputSchema,
                outputVersion,
                outputSchema,
                capability);
    }

    private static void put(Map<String, WcsToolDescriptor> contracts, WcsToolDescriptor descriptor) {
        if (contracts.putIfAbsent(descriptor.name(), descriptor) != null) {
            throw new IllegalStateException("Duplicate WCS tool contract: " + descriptor.name());
        }
    }
}
