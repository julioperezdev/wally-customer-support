package com.wally.customersupport.agent.application.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.wally.customersupport.conversation.application.tool.WcsToolContractCatalog;
import com.wally.customersupport.conversation.application.tool.WcsToolDescriptor;
import com.wally.customersupport.conversation.domain.model.ConversationExecutionPlan;
import com.wally.customersupport.conversation.domain.model.ConversationExecutionStep;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Provider-neutral catalog of specialist responsibilities and their tool
 * boundaries. It is deliberately independent from Bedrock and PostgreSQL.
 */
@Component
public final class AgentSpecialistRegistry {

    private final Map<String, AgentSpecialistDefinition> definitions;

    @Autowired
    public AgentSpecialistRegistry() {
        this(defaultRegistry().all());
    }

    public AgentSpecialistRegistry(List<AgentSpecialistDefinition> definitions) {
        Objects.requireNonNull(definitions, "definitions");
        Map<String, AgentSpecialistDefinition> indexed = new LinkedHashMap<>();
        for (AgentSpecialistDefinition definition : definitions) {
            if (indexed.putIfAbsent(definition.agentId(), definition) != null) {
                throw new IllegalArgumentException("Duplicate specialist agent: " + definition.agentId());
            }
            for (String tool : definition.allowedTools()) {
                if (WcsToolContractCatalog.find(tool).isEmpty()) {
                    throw new IllegalArgumentException(
                            "Specialist " + definition.agentId() + " references unknown tool: " + tool);
                }
            }
        }
        this.definitions = Map.copyOf(indexed);
    }

    public static AgentSpecialistRegistry defaultRegistry() {
        return new AgentSpecialistRegistry(List.of(
                new AgentSpecialistDefinition(
                        "conversation-router",
                        "Mapea el mensaje y el contexto a un caso de uso estructurado.",
                        "conversation-context",
                        Set.of(),
                        Set.of("GREETING", "CATALOG_SEARCH", "PURCHASE_LINK", "BUSINESS_HOURS",
                                "POLICY_QUERY", "GENERAL_SUPPORT", "HUMAN_HANDOFF")),
                new AgentSpecialistDefinition(
                        "catalog-specialist",
                        "Busca productos, variantes, precio y stock.",
                        "postgresql",
                        Set.of(WcsToolContractCatalog.CATALOG_SEARCH, WcsToolContractCatalog.CATALOG_STOCK),
                        Set.of("CATALOG_SEARCH")),
                new AgentSpecialistDefinition(
                        "conversation-state",
                        "Mantiene selección, filtros y carrito con ownership validado.",
                        "postgresql",
                        Set.of(WcsToolContractCatalog.CONVERSATION_STATE, WcsToolContractCatalog.CART_MANAGE),
                        Set.of("CATALOG_SEARCH", "CART", "PURCHASE_LINK")),
                new AgentSpecialistDefinition(
                        "knowledge-specialist",
                        "Responde conocimiento estático con evidencia de Knowledge Base.",
                        "bedrock-knowledge-base",
                        Set.of(WcsToolContractCatalog.KNOWLEDGE_RETRIEVE),
                        Set.of("BUSINESS_HOURS", "POLICY_QUERY", "GENERAL_SUPPORT")),
                new AgentSpecialistDefinition(
                        "checkout-specialist",
                        "Gestiona carrito, confirmación, pedido y link de pago.",
                        "wcs-payment-adapter",
                        Set.of(WcsToolContractCatalog.CART_MANAGE, WcsToolContractCatalog.CHECKOUT_CREATE),
                        Set.of("CART", "PURCHASE_LINK", "PURCHASE_DEFERRED")),
                new AgentSpecialistDefinition(
                        "support-safety",
                        "Aplica fallback, baja confianza y derivación humana.",
                        "wcs-safety-policy",
                        Set.of(WcsToolContractCatalog.HUMAN_HANDOFF, WcsToolContractCatalog.SAFE_FALLBACK),
                        Set.of("HUMAN_HANDOFF", "LOW_CONFIDENCE", "SAFE_FALLBACK")),
                new AgentSpecialistDefinition(
                        "response-humanizer",
                        "Adapta tono y formato sin modificar hechos validados.",
                        "validated-facts",
                        Set.of(),
                        Set.of("GREETING", "GENERAL_SUPPORT", "CATALOG_SEARCH"))));
    }

    public List<AgentSpecialistDefinition> all() {
        return List.copyOf(definitions.values());
    }

    public Optional<AgentSpecialistDefinition> find(String agentId) {
        return Optional.ofNullable(definitions.get(agentId));
    }

    public Validation validateDefinition(AgentRuntimeDefinition definition) {
        if (definition == null) {
            return Validation.invalid("DEFINITION_REQUIRED", null, null, List.of());
        }
        AgentSpecialistDefinition specialist = definitions.get(definition.agentId());
        if (specialist == null) {
            return Validation.invalid("SPECIALIST_NOT_REGISTERED", definition.agentId(), null, List.of());
        }
        List<String> unauthorized = definition.allowedTools().stream()
                .filter(tool -> !specialist.allows(tool))
                .sorted()
                .toList();
        if (!unauthorized.isEmpty()) {
            return Validation.invalid("TOOL_NOT_ALLOWED_BY_SPECIALIST", definition.agentId(), null, unauthorized);
        }
        List<String> unknown = definition.allowedTools().stream()
                .filter(tool -> WcsToolContractCatalog.find(tool).isEmpty())
                .sorted()
                .toList();
        if (!unknown.isEmpty()) {
            return Validation.invalid("TOOL_CONTRACT_NOT_FOUND", definition.agentId(), null, unknown);
        }
        return Validation.valid(definition.agentId());
    }

    public Validation validateExecution(
            AgentRuntimeDefinition definition,
            ConversationExecutionPlan plan) {
        Validation definitionValidation = validateDefinition(definition);
        if (!definitionValidation.valid()) {
            return definitionValidation;
        }
        if (plan == null || plan.steps().isEmpty()) {
            return Validation.invalid("PLAN_REQUIRED", definition.agentId(), null, List.of());
        }
        ConversationExecutionStep step = plan.steps().getFirst();
        if (!definition.agentId().equals(step.owner())) {
            return Validation.invalid("PLAN_OWNER_MISMATCH", definition.agentId(), step.toolName(), List.of());
        }
        AgentSpecialistDefinition specialist = definitions.get(definition.agentId());
        if (!specialist.supports(plan.useCase())) {
            return Validation.invalid("USE_CASE_NOT_SUPPORTED", definition.agentId(), step.toolName(), List.of());
        }
        if (step.toolName() == null) {
            return Validation.valid(definition.agentId());
        }
        if (!definition.allowedTools().contains(step.toolName())) {
            return Validation.invalid("TOOL_NOT_ALLOWED", definition.agentId(), step.toolName(), List.of());
        }
        WcsToolDescriptor contract = WcsToolContractCatalog.find(step.toolName()).orElse(null);
        if (contract == null
                || !contract.inputSchemaVersion().equals(step.inputSchemaVersion())
                || !contract.outputSchemaVersion().equals(step.outputSchemaVersion())) {
            return Validation.invalid("TOOL_SCHEMA_MISMATCH", definition.agentId(), step.toolName(), List.of());
        }
        return Validation.valid(definition.agentId());
    }

    public record Validation(
            boolean valid,
            String reason,
            String agentId,
            String toolName,
            List<String> invalidTools) {

        public Validation {
            reason = Objects.requireNonNull(reason, "reason");
            invalidTools = invalidTools == null ? List.of() : List.copyOf(invalidTools);
        }

        static Validation valid(String agentId) {
            return new Validation(true, "VALID", agentId, null, List.of());
        }

        static Validation invalid(String reason, String agentId, String toolName, List<String> invalidTools) {
            return new Validation(false, reason, agentId, toolName, invalidTools);
        }
    }
}
