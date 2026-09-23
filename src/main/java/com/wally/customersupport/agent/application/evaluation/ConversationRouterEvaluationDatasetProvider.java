package com.wally.customersupport.agent.application.evaluation;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.wally.customersupport.agent.domain.model.AgentEvaluationScenario;
import com.wally.customersupport.catalog.domain.model.CatalogQuery;
import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.conversation.domain.model.ResponseHumanizationResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Versioned synthetic dataset for comparing immutable conversation-router prompt versions. */
public class ConversationRouterEvaluationDatasetProvider implements AgentEvaluationDataset {

    public static final String VERSION = "conversation-routing-v1";
    public static final String VERSION_2 = "conversation-routing-v2";
    private static final String V1_FIXTURE = "/fixtures/conversation-routing-v1.json";
    private static final String V2_FIXTURE = "/fixtures/conversation-routing-v2.json";

    private final String version;
    private final List<AgentEvaluationScenario> scenarios;

    public ConversationRouterEvaluationDatasetProvider(ObjectMapper objectMapper) {
        this(objectMapper, VERSION);
    }

    public ConversationRouterEvaluationDatasetProvider(ObjectMapper objectMapper, String version) {
        this.version = requireSupportedVersion(version);
        this.scenarios = load(objectMapper, this.version, fixtureFor(this.version));
    }

    @Override
    public String version() {
        return version;
    }

    @Override
    public String agentId() {
        return "conversation-router";
    }

    @Override
    public List<AgentEvaluationScenario> scenarios() {
        return scenarios;
    }

    private static List<AgentEvaluationScenario> load(ObjectMapper objectMapper, String version, String fixture) {
        try (InputStream input = ConversationRouterEvaluationDatasetProvider.class.getResourceAsStream(fixture)) {
            if (input == null) {
                throw new IllegalStateException("conversation-router evaluation fixture is missing");
            }
            JsonNode root = objectMapper.readTree(input);
            if (!root.isArray() || root.isEmpty()) {
                throw new IllegalStateException("conversation-router evaluation fixture must be a non-empty array");
            }
            List<AgentEvaluationScenario> scenarios = new ArrayList<>();
            for (JsonNode item : root) {
                scenarios.add(toScenario(item, version));
            }
            return List.copyOf(scenarios);
        } catch (IOException exception) {
            throw new IllegalStateException("conversation-router evaluation fixture is invalid", exception);
        }
    }

    private static AgentEvaluationScenario toScenario(JsonNode item, String version) {
        String message = requiredText(item, "message");
        List<String> history = textArray(item.path("history"));
        List<String> recentMessages = new ArrayList<>();
        recentMessages.add(message);
        for (int index = history.size() - 1; index >= 0; index--) {
            recentMessages.add(history.get(index));
        }
        CatalogQuery expectedQuery = expectedQuery(item);
        List<String> expectedEntities = queryAttributes(expectedQuery);
        ConversationContext context = new ConversationContext(
                null, null, message, recentMessages, List.of(), null, List.of(), Channel.TELEGRAM);
        return new AgentEvaluationScenario(
                requiredText(item, "name"),
                version,
                "ROUTING",
                Channel.TELEGRAM,
                null,
                ResponseHumanizationResult.Outcome.APPLIED,
                List.of(),
                List.of(),
                requiredText(item, "expectedIntent"),
                expectedEntities,
                null,
                null,
                context,
                requiredText(item, "expectedAction"),
                VERSION_2.equals(version) ? integerOrNull(item, "expectedQuantity") : null);
    }

    private static CatalogQuery expectedQuery(JsonNode item) {
        return new CatalogQuery(
                optionalText(item, "expectedName"),
                optionalText(item, "expectedSku"),
                optionalText(item, "expectedSize"),
                optionalText(item, "expectedColor"),
                optionalText(item, "expectedProductType"),
                decimalOrNull(item, "expectedMinPrice"),
                decimalOrNull(item, "expectedMaxPrice"));
    }

    private static List<String> queryAttributes(CatalogQuery query) {
        List<String> values = new ArrayList<>();
        add(values, "name", query.name());
        add(values, "sku", query.sku());
        add(values, "productType", query.productType());
        add(values, "color", query.color());
        add(values, "size", query.size());
        add(values, "minPrice", query.minPrice() == null ? null : decimal(query.minPrice()));
        add(values, "maxPrice", query.maxPrice() == null ? null : decimal(query.maxPrice()));
        return values.stream().sorted().toList();
    }

    private static void add(List<String> values, String key, String value) {
        if (value != null) values.add(key + "=" + value);
    }

    private static String decimal(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static BigDecimal decimalOrNull(JsonNode item, String field) {
        String value = optionalText(item, field);
        return value == null ? null : new BigDecimal(value);
    }

    private static Integer integerOrNull(JsonNode item, String field) {
        JsonNode value = item.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToInt()) return null;
        return value.intValue();
    }

    private static String requireSupportedVersion(String version) {
        if (!VERSION.equals(version) && !VERSION_2.equals(version)) {
            throw new IllegalArgumentException("unsupported conversation-router dataset version");
        }
        return version;
    }

    private static String fixtureFor(String version) {
        return VERSION.equals(version) ? V1_FIXTURE : V2_FIXTURE;
    }

    private static List<String> textArray(JsonNode node) {
        if (!node.isArray()) return List.of();
        List<String> values = new ArrayList<>();
        node.forEach(value -> {
            if (value.isTextual() && !value.asText().isBlank()) values.add(value.asText());
        });
        return List.copyOf(values);
    }

    private static String requiredText(JsonNode item, String field) {
        String value = optionalText(item, field);
        if (value == null) throw new IllegalStateException("missing required fixture field: " + field);
        return value;
    }

    private static String optionalText(JsonNode item, String field) {
        JsonNode value = item.path(field);
        if (value.isMissingNode() || value.isNull() || !value.isValueNode()) return null;
        String text = value.asText().strip();
        return text.isBlank() ? null : text;
    }
}
