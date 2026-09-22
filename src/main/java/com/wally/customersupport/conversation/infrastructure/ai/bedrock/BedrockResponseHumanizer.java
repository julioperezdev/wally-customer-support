package com.wally.customersupport.conversation.infrastructure.ai.bedrock;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.wally.customersupport.catalog.application.service.CatalogFact;
import com.wally.customersupport.catalog.application.service.CatalogResponseFormatter;
import com.wally.customersupport.catalog.application.service.CatalogSearchResult;
import com.wally.customersupport.conversation.application.port.out.ResponseHumanizer;
import com.wally.customersupport.conversation.domain.model.ResponseHumanizationRequest;
import com.wally.customersupport.conversation.domain.model.ResponseHumanizationResult;
import com.wally.customersupport.conversation.infrastructure.ai.prompt.PromptDefinition;
import com.wally.customersupport.conversation.infrastructure.ai.prompt.PromptRegistry;
import com.wally.customersupport.shared.infrastructure.config.AiResponseProperties;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Bedrock-backed presentation policy for already validated catalog facts.
 *
 * <p>The model is never asked to search, select, or calculate catalog data.
 * It only rewrites a bounded deterministic rendering. The response is
 * accepted only when the business identifiers and claims exposed by the
 * source-backed result are preserved; otherwise the deterministic rendering
 * is returned.</p>
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "wcs.ai.provider", havingValue = "bedrock")
public class BedrockResponseHumanizer implements ResponseHumanizer {

    public static final String POLICY_ID = "bedrock-response-humanizer";
    public static final String POLICY_VERSION = "v1";

    private static final String SAFE_FALLBACK = "No pude preparar una respuesta segura para esa consulta. "
            + "Podés volver a intentarlo o pedir ayuda a un agente.";
    private static final Pattern SKU_PATTERN = Pattern.compile("\\bRP-[A-Za-z0-9-]+\\b");
    private static final Pattern MONEY_PATTERN = Pattern.compile(
            "(?i)(\\d[\\d.,]*)\\s*(ARS|USD|EUR|BRL|CLP|MXN)");
    private static final Pattern COUNT_CLAIM_PATTERN = Pattern.compile(
            "(?i)\\b(?:stock|disponibles?|unidades?)\\D{0,12}(\\d+)\\b");

    private final BedrockConverseClient converseClient;
    private final AiResponseProperties responseProperties;
    private final PromptDefinition prompt;

    @Autowired
    public BedrockResponseHumanizer(
            BedrockConverseClient converseClient,
            AiResponseProperties responseProperties,
            PromptRegistry promptRegistry) {
        this.converseClient = Objects.requireNonNull(converseClient, "converseClient");
        this.responseProperties = Objects.requireNonNull(responseProperties, "responseProperties");
        Objects.requireNonNull(promptRegistry, "promptRegistry");
        this.prompt = promptRegistry.responsePrompt(responseProperties.effectivePromptVersion());
    }

    @Override
    public ResponseHumanizationResult humanize(ResponseHumanizationRequest request) {
        long startedAt = System.nanoTime();
        if (request == null) {
            return fallback(SAFE_FALLBACK, "INVALID_INPUT", null, startedAt);
        }

        String deterministicText;
        try {
            deterministicText = CatalogResponseFormatter.render(request.catalogResult());
        } catch (RuntimeException exception) {
            logFallback(request, "RENDERING_FAILED", exception, startedAt, notEvaluated(), 0);
            return ResponseHumanizationResult.fallback(
                    SAFE_FALLBACK, POLICY_ID, POLICY_VERSION, "RENDERING_FAILED");
        }

        try {
            String generated = converseClient.complete(
                    "response-humanization",
                    "conversation.response.humanize",
                    prompt.content(),
                    buildUserPrompt(request, deterministicText),
                    responseProperties.effectiveMaxOutputTokens(),
                    responseProperties.effectiveTemperature(),
                    prompt.version(),
                    prompt.sha256());
            if (generated == null || generated.isBlank()) {
                return fallback(deterministicText, "EMPTY_RESPONSE", request, startedAt);
            }
            String normalized = generated.trim();
            ValidationDiagnostics validation = validateApprovedFacts(normalized, request.catalogResult());
            if (!validation.valid()) {
                return fallback(
                        deterministicText,
                        "FACTS_NOT_PRESERVED",
                        request,
                        startedAt,
                        validation,
                        normalized.length());
            }

            ResponseHumanizationResult result = ResponseHumanizationResult.applied(
                    normalized, POLICY_ID, POLICY_VERSION);
            logApplied(request, startedAt, validation, normalized.length());
            return result;
        } catch (RuntimeException exception) {
            logFallback(request, "PROVIDER_ERROR", exception, startedAt, notEvaluated(), 0);
            return ResponseHumanizationResult.fallback(
                    deterministicText, POLICY_ID, POLICY_VERSION, "PROVIDER_ERROR");
        }
    }

    private String buildUserPrompt(ResponseHumanizationRequest request, String deterministicText) {
        String boundedFacts = limit(deterministicText, responseProperties.effectiveMaxInputCharacters());
        return """
                <request_metadata>
                <use_case>%s</use_case>
                <channel>%s</channel>
                </request_metadata>
                <approved_knowledge>
                %s
                </approved_knowledge>
                <response_contract>
                Redactá una respuesta breve y natural en español argentino.
                Conservá literalmente todos los productos, SKU, talles, colores,
                precios, monedas y cantidades de stock presentes en los hechos.
                Si hay varios productos, conservá exactamente su orden; el cliente
                puede referirse a ellos por posición.
                No agregues datos, promociones, políticas, envíos ni instrucciones.
                Devolvé únicamente el mensaje final para el cliente.
                </response_contract>
                """.formatted(request.useCase(), request.channel().name(), boundedFacts);
    }

    private ValidationDiagnostics validateApprovedFacts(String generated, CatalogSearchResult result) {
        String normalizedGenerated = normalize(generated);
        List<CatalogFact> facts = result.facts();
        if (result.status() == CatalogSearchResult.Status.MATCHED && facts.isEmpty()) {
            return new ValidationDiagnostics(
                    false,
                    "RESULT_SHAPE",
                    List.of("MATCHED_FACTS"),
                    List.of(),
                    0,
                    0);
        }

        Set<String> unapprovedClaims = new LinkedHashSet<>();
        if (result.status() != CatalogSearchResult.Status.MATCHED && facts.isEmpty()) {
            unapprovedClaims.addAll(findUnapprovedStructuredClaims(
                    normalizedGenerated,
                    Set.of(),
                    Set.of()));
            return diagnostics(unapprovedClaims.isEmpty(), Set.of(), unapprovedClaims, 0, 0);
        }

        Set<String> approvedSkus = facts.stream()
                .map(CatalogFact::sku)
                .map(BedrockResponseHumanizer::normalize)
                .collect(Collectors.toSet());
        Set<String> approvedMoney = facts.stream()
                .map(this::moneyClaim)
                .collect(Collectors.toSet());
        Set<String> approvedCounts = facts.stream()
                .map(fact -> Integer.toString(fact.stock()))
                .collect(Collectors.toSet());

        unapprovedClaims.addAll(findUnapprovedStructuredClaims(
                normalizedGenerated,
                approvedSkus,
                approvedMoney));
        if (extractCounts(normalizedGenerated).stream().anyMatch(count -> !approvedCounts.contains(count))) {
            unapprovedClaims.add("STOCK");
        }

        Set<String> missingFacts = new LinkedHashSet<>();
        int preservedFactCount = 0;
        for (CatalogFact fact : facts) {
            List<String> missingForFact = missingFacts(normalizedGenerated, fact, result.followUpKind());
            if (missingForFact.isEmpty()) {
                preservedFactCount++;
            } else {
                missingFacts.addAll(missingForFact);
            }
        }
        if (!catalogOrderPreserved(normalizedGenerated, facts)) {
            missingFacts.add("CATALOG_ORDER");
        }
        return diagnostics(
                unapprovedClaims.isEmpty() && missingFacts.isEmpty(),
                missingFacts,
                unapprovedClaims,
                facts.size(),
                preservedFactCount);
    }

    private static boolean catalogOrderPreserved(String generated, List<CatalogFact> facts) {
        int previousSkuPosition = -1;
        for (CatalogFact fact : facts) {
            int skuPosition = generated.indexOf(normalize(fact.sku()));
            if (skuPosition < 0 || skuPosition <= previousSkuPosition) {
                return false;
            }
            previousSkuPosition = skuPosition;
        }
        return true;
    }

    private List<String> missingFacts(
            String generated,
            CatalogFact fact,
            CatalogSearchResult.FollowUpKind followUpKind) {
        Set<String> missing = new LinkedHashSet<>();
        if (!contains(generated, fact.productName())) {
            missing.add("PRODUCT_NAME");
        }
        if (!contains(generated, fact.sku())) {
            missing.add("SKU");
        }
        switch (followUpKind) {
            case NONE -> {
                if (!contains(generated, fact.size())) {
                    missing.add("SIZE");
                }
                if (!contains(generated, fact.color())) {
                    missing.add("COLOR");
                }
                if (!contains(generated, fact.currency())) {
                    missing.add("CURRENCY");
                }
                if (!containsMoney(generated, fact)) {
                    missing.add("PRICE");
                }
                if (fact.available()
                        ? !contains(generated, Integer.toString(fact.stock()))
                        : !contains(generated, "sin stock")) {
                    missing.add("STOCK");
                }
            }
            case AVAILABILITY -> {
                if (fact.available()
                        ? !contains(generated, Integer.toString(fact.stock()))
                        : !contains(generated, "sin stock")) {
                    missing.add("STOCK");
                }
            }
            case PRICE -> {
                if (!containsMoney(generated, fact)) {
                    missing.add("PRICE");
                }
            }
            case SIZE -> {
                if (!contains(generated, fact.size())) {
                    missing.add("SIZE");
                }
            }
            case COLOR -> {
                if (!contains(generated, fact.color())) {
                    missing.add("COLOR");
                }
            }
        }
        return List.copyOf(missing);
    }

    private Set<String> findUnapprovedStructuredClaims(
            String generated,
            Set<String> approvedSkus,
            Set<String> approvedMoney) {
        Set<String> unapprovedClaims = new LinkedHashSet<>();
        Matcher skuMatcher = SKU_PATTERN.matcher(generated);
        while (skuMatcher.find()) {
            if (!approvedSkus.contains(normalize(skuMatcher.group()))) {
                unapprovedClaims.add("SKU");
            }
        }
        Matcher moneyMatcher = MONEY_PATTERN.matcher(generated);
        while (moneyMatcher.find()) {
            String claim = normalizeDigits(moneyMatcher.group(1)) + ":" + moneyMatcher.group(2).toUpperCase();
            if (!approvedMoney.contains(claim)) {
                unapprovedClaims.add("PRICE");
            }
        }
        return unapprovedClaims;
    }

    private static ValidationDiagnostics diagnostics(
            boolean valid,
            Set<String> missingFacts,
            Set<String> unapprovedClaims,
            int expectedFactCount,
            int preservedFactCount) {
        return new ValidationDiagnostics(
                valid,
                "APPROVED_FACTS",
                List.copyOf(missingFacts),
                List.copyOf(unapprovedClaims),
                expectedFactCount,
                preservedFactCount);
    }

    private Set<String> extractCounts(String value) {
        Matcher matcher = COUNT_CLAIM_PATTERN.matcher(value);
        java.util.LinkedHashSet<String> counts = new java.util.LinkedHashSet<>();
        while (matcher.find()) {
            counts.add(matcher.group(1));
        }
        return counts;
    }

    private String moneyClaim(CatalogFact fact) {
        return normalizeDigits(fact.price().toPlainString()) + ":" + fact.currency().toUpperCase();
    }

    private boolean containsMoney(String generated, CatalogFact fact) {
        Matcher matcher = MONEY_PATTERN.matcher(generated);
        String expected = moneyClaim(fact);
        while (matcher.find()) {
            String actual = normalizeDigits(matcher.group(1)) + ":" + matcher.group(2).toUpperCase();
            if (expected.equals(actual)) {
                return true;
            }
        }
        return false;
    }

    private ResponseHumanizationResult fallback(
            String text,
            String reason,
            ResponseHumanizationRequest request,
            long startedAt) {
        return fallback(text, reason, request, startedAt, notEvaluated(), 0);
    }

    private ResponseHumanizationResult fallback(
            String text,
            String reason,
            ResponseHumanizationRequest request,
            long startedAt,
            ValidationDiagnostics validation,
            int generatedCharacters) {
        if (request == null) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("policyId", POLICY_ID);
            fields.put("policyVersion", POLICY_VERSION);
            fields.put("outcome", ResponseHumanizationResult.Outcome.FALLBACK.name());
            fields.put("fallbackReason", reason);
            fields.put("durationMs", elapsedMillis(startedAt));
            addValidationFields(fields, validation, generatedCharacters);
            StructuredEventLog.warn(log, "RESPONSE_POLICY_FALLBACK", fields);
        } else {
            logFallback(request, reason, null, startedAt, validation, generatedCharacters);
        }
        return ResponseHumanizationResult.fallback(text, POLICY_ID, POLICY_VERSION, reason);
    }

    private void logApplied(
            ResponseHumanizationRequest request,
            long startedAt,
            ValidationDiagnostics validation,
            int generatedCharacters) {
        Map<String, Object> fields = baseFields(request, startedAt);
        fields.put("outcome", ResponseHumanizationResult.Outcome.APPLIED.name());
        addValidationFields(fields, validation, generatedCharacters);
        StructuredEventLog.info(log, "RESPONSE_POLICY_APPLIED", fields);
    }

    private void logFallback(
            ResponseHumanizationRequest request,
            String reason,
            RuntimeException exception,
            long startedAt,
            ValidationDiagnostics validation,
            int generatedCharacters) {
        Map<String, Object> fields = baseFields(request, startedAt);
        fields.put("outcome", ResponseHumanizationResult.Outcome.FALLBACK.name());
        fields.put("fallbackReason", reason);
        addValidationFields(fields, validation, generatedCharacters);
        if (exception != null) {
            fields.put("errorType", exception.getClass().getSimpleName());
        }
        StructuredEventLog.warn(log, "RESPONSE_POLICY_FALLBACK", fields);
    }

    private Map<String, Object> baseFields(
            ResponseHumanizationRequest request,
            long startedAt) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("useCase", request.useCase());
        fields.put("channel", request.channel().name());
        fields.put("policyId", POLICY_ID);
        fields.put("policyVersion", POLICY_VERSION);
        fields.put("resultStatus", request.catalogResult().status().name());
        fields.put("resultCount", request.catalogResult().resultCount());
        fields.put("durationMs", elapsedMillis(startedAt));
        fields.put("promptVersion", prompt.version());
        fields.put("promptHash", prompt.sha256());
        return fields;
    }

    private void addValidationFields(
            Map<String, Object> fields,
            ValidationDiagnostics validation,
            int generatedCharacters) {
        fields.put("validationStage", validation.stage());
        fields.put("generatedCharacters", generatedCharacters);
        fields.put("expectedFactCount", validation.expectedFactCount());
        fields.put("preservedFactCount", validation.preservedFactCount());
        fields.put("missingFacts", validation.missingFacts());
        fields.put("unapprovedClaims", validation.unapprovedClaims());
    }

    private static ValidationDiagnostics notEvaluated() {
        return new ValidationDiagnostics(true, "NOT_EVALUATED", List.of(), List.of(), 0, 0);
    }

    private static String limit(String value, int maxChars) {
        return value.length() <= maxChars ? value : value.substring(0, maxChars);
    }

    private static boolean contains(String value, String expected) {
        return value.contains(normalize(expected));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String normalizeDigits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private static long elapsedMillis(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }

    private record ValidationDiagnostics(
            boolean valid,
            String stage,
            List<String> missingFacts,
            List<String> unapprovedClaims,
            int expectedFactCount,
            int preservedFactCount) {
    }
}
