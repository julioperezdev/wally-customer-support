package com.wally.customersupport.application.service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import com.wally.customersupport.application.port.out.ConversationIntentClassifier;
import com.wally.customersupport.application.port.out.KnowledgeRetriever;
import com.wally.customersupport.application.port.out.LlmClient;
import com.wally.customersupport.domain.model.BusinessHour;
import com.wally.customersupport.domain.model.ConversationContext;
import com.wally.customersupport.domain.model.ConversationIntent;
import com.wally.customersupport.domain.model.ConversationIntentDecision;
import com.wally.customersupport.domain.model.KnowledgeChunk;
import com.wally.customersupport.domain.model.KnowledgeQuery;
import com.wally.customersupport.domain.model.SupportPolicy;
import com.wally.customersupport.infrastructure.config.RagProperties;
import org.springframework.stereotype.Service;

@Service
public class ConversationOrchestrator {

    private static final double MIN_CONFIDENCE = 0.65;
    private static final String GREETING = "Hola, ¿cómo te puedo ayudar?";
    private static final String LOW_CONFIDENCE = "No estoy seguro de haber entendido tu consulta. "
            + "Podés preguntarme por productos, stock, horarios, envíos o cambios.";
    private static final String SAFE_FALLBACK = "No pude interpretar la consulta. "
            + "Podés preguntarme por productos, stock, horarios o políticas de la tienda.";
    private static final String HUMAN_HANDOFF = "Entiendo. Un agente revisará tu consulta con el contexto "
            + "de esta conversación dentro de las próximas 24 horas.";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final List<String> DAY_NAMES = List.of(
            "lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo");

    private final ConversationIntentClassifier intentClassifier;
    private final CatalogConversationService catalogConversationService;
    private final SupportConfigurationQueryService supportConfigurationQueryService;
    private final KnowledgeRetriever knowledgeRetriever;
    private final LlmClient llmClient;
    private final RagProperties ragProperties;

    public ConversationOrchestrator(
            ConversationIntentClassifier intentClassifier,
            CatalogConversationService catalogConversationService,
            SupportConfigurationQueryService supportConfigurationQueryService,
            KnowledgeRetriever knowledgeRetriever,
            LlmClient llmClient,
            RagProperties ragProperties) {
        this.intentClassifier = intentClassifier;
        this.catalogConversationService = catalogConversationService;
        this.supportConfigurationQueryService = supportConfigurationQueryService;
        this.knowledgeRetriever = knowledgeRetriever;
        this.llmClient = llmClient;
        this.ragProperties = ragProperties;
    }

    public String replyFor(ConversationContext context) {
        if (context == null || context.latestMessage() == null || context.latestMessage().isBlank()) {
            return SAFE_FALLBACK;
        }

        ConversationIntentDecision decision;
        try {
            decision = intentClassifier.classify(context.latestMessage());
        } catch (RuntimeException exception) {
            return safeGeneralSupport(context);
        }
        if (decision == null) {
            return SAFE_FALLBACK;
        }
        if (decision.confidence() < MIN_CONFIDENCE) {
            return LOW_CONFIDENCE;
        }

        return switch (decision.intent()) {
            case GREETING -> GREETING;
            case CATALOG_SEARCH -> catalogConversationService.replyFor(decision.catalogQuery())
                    .orElse(LOW_CONFIDENCE);
            case BUSINESS_HOURS -> formatBusinessHours();
            case POLICY_QUERY -> formatPolicy(decision.policyKey());
            case HUMAN_HANDOFF -> HUMAN_HANDOFF;
            case GENERAL_SUPPORT, UNKNOWN -> safeGeneralSupport(context);
        };
    }

    private String safeGeneralSupport(ConversationContext context) {
        try {
            List<KnowledgeChunk> knowledge = knowledgeRetriever.retrieve(new KnowledgeQuery(
                    context.latestMessage(),
                    context.conversationId(),
                    Math.max(1, ragProperties.maxResults())));
            return llmClient.generateReply(new ConversationContext(
                    context.conversationId(),
                    context.externalCustomerId(),
                    context.latestMessage(),
                    context.recentMessages(),
                    knowledge));
        } catch (RuntimeException exception) {
            return SAFE_FALLBACK;
        }
    }

    private String formatBusinessHours() {
        List<BusinessHour> hours = supportConfigurationQueryService.businessHours();
        if (hours.isEmpty()) {
            return "No tengo horarios publicados. Un agente puede confirmarlos por vos.";
        }

        StringBuilder response = new StringBuilder("Nuestro horario de atención es:\n");
        for (BusinessHour hour : hours) {
            String day = hour.dayOfWeek() >= 1 && hour.dayOfWeek() <= DAY_NAMES.size()
                    ? DAY_NAMES.get(hour.dayOfWeek() - 1)
                    : "día " + hour.dayOfWeek();
            response.append("- ").append(capitalize(day)).append(": ");
            if (hour.closed()) {
                response.append("cerrado");
            } else {
                response.append(hour.opensAt().format(TIME_FORMATTER))
                        .append(" a ")
                        .append(hour.closesAt().format(TIME_FORMATTER));
            }
            response.append("\n");
        }
        response.append("Zona horaria: ")
                .append(hours.getFirst().timezone().getId());
        return response.toString();
    }

    private String formatPolicy(String policyKey) {
        if (policyKey == null || policyKey.isBlank()) {
            return LOW_CONFIDENCE;
        }
        return supportConfigurationQueryService.activePolicy(policyKey)
                .map(this::formatPolicy)
                .orElse("No tengo una política publicada para esa consulta. "
                        + "Un agente puede confirmarla por vos.");
    }

    private String formatPolicy(SupportPolicy policy) {
        return policy.title() + ": " + policy.content();
    }

    private static String capitalize(String value) {
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }
}
