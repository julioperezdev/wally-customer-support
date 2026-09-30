package com.wally.customersupport.conversation.infrastructure.ai.typesafe;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.wally.customersupport.conversation.application.port.out.TypeSafeUseCaseSelector;
import com.wally.customersupport.conversation.application.port.out.TypeSafeUseCaseSelector.TypeSafeSelection;
import com.wally.customersupport.conversation.domain.model.ConversationAction;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.shared.infrastructure.config.ConversationRoutingProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.databind.JsonNode;

/** HTTP adapter for TypeSafe System One. Request state is bounded and contains no WCS identifiers. */
@Component
public final class TypeSafeSystemOneClient implements TypeSafeUseCaseSelector {

    private static final String QUESTION_ID = "use_case";
    private static final String QUESTION_INSTRUCTIONS = """
            Choose the single WCS conversation action requested by the latest customer message, using recent messages only to resolve explicit references. Looking at or selecting a product is CATALOG_SEARCH, not ADD_TO_CART. Choose a cart or checkout mutation only when the customer explicitly requests that operation. If the request is unclear or none of the listed actions fits, choose UNKNOWN.
            """.strip();
    private static final Map<String, String> ACTION_CRITERIA = actionCriteria();

    private final RestClient restClient;
    private final ConversationRoutingProperties properties;

    @Autowired
    public TypeSafeSystemOneClient(ConversationRoutingProperties properties) {
        this(createRestClient(properties), properties);
    }

    TypeSafeSystemOneClient(
            RestClient restClient,
            ConversationRoutingProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public TypeSafeSelection select(ConversationContext context) {
        if (properties.typesafe().apiKey() == null) {
            return TypeSafeSelection.failure("MISSING_API_KEY");
        }
        if (context == null || context.latestMessage() == null || context.latestMessage().isBlank()) {
            return TypeSafeSelection.failure("EMPTY_STATE");
        }

        try {
            JsonNode response = restClient.post()
                    .uri(URI.create(properties.typesafe().endpoint() + "/v1/systemone"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.typesafe().apiKey())
                    .body(request(context))
                    .retrieve()
                    .body(JsonNode.class);
            return parseSelection(response);
        } catch (RestClientResponseException exception) {
            return TypeSafeSelection.failure("HTTP_" + exception.getStatusCode().value());
        } catch (ResourceAccessException exception) {
            return TypeSafeSelection.failure("TRANSPORT_ERROR");
        } catch (RuntimeException exception) {
            return TypeSafeSelection.failure("INVALID_RESPONSE");
        }
    }

    private SystemOneRequest request(ConversationContext context) {
        Map<String, SystemOneQuestion> questions = new LinkedHashMap<>();
        questions.put(QUESTION_ID, new SystemOneQuestion("choice", QUESTION_INSTRUCTIONS, ACTION_CRITERIA));
        return new SystemOneRequest(state(context), properties.typesafe().model(), questions);
    }

    String state(ConversationContext context) {
        int maxCharacters = properties.typesafe().maxStateCharacters();
        String currentLabel = "Current customer message:\n";
        String currentMessage = context.latestMessage().strip();
        currentMessage = truncate(currentMessage, Math.max(0, maxCharacters - currentLabel.length()));
        int remaining = Math.max(0, maxCharacters - currentLabel.length() - currentMessage.length());

        List<String> recent = context.recentMessages();
        List<String> chronological = new ArrayList<>(recent.reversed());
        if (!chronological.isEmpty() && context.latestMessage().equals(chronological.getLast())) {
            chronological.removeLast();
        }
        int start = Math.max(0, chronological.size() - properties.typesafe().maxHistoryMessages());
        List<String> boundedHistory = new ArrayList<>();
        String historyLabel = "Recent conversation:\n";
        for (int index = chronological.size() - 1; index >= start; index--) {
            String message = chronological.get(index);
            if (message == null || message.isBlank()) {
                continue;
            }
            String line = "- " + message.strip() + "\n";
            if (line.length() + historyLabel.length() > remaining) {
                break;
            }
            if (boundedHistory.isEmpty()) {
                remaining -= historyLabel.length();
            }
            boundedHistory.add(0, line);
            remaining -= line.length();
        }

        StringBuilder state = new StringBuilder(maxCharacters);
        if (!boundedHistory.isEmpty()) {
            state.append(historyLabel);
            boundedHistory.forEach(state::append);
        }
        state.append(currentLabel).append(currentMessage);
        return state.toString();
    }

    private static String truncate(String value, int maxCharacters) {
        if (value.length() <= maxCharacters) {
            return value;
        }
        int end = maxCharacters;
        if (end > 0 && Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }

    private TypeSafeSelection parseSelection(JsonNode response) {
        if (response == null) {
            return TypeSafeSelection.failure("INVALID_RESPONSE");
        }
        JsonNode answer = response.path("answers").path(QUESTION_ID);
        if (!"choice".equalsIgnoreCase(answer.path("type").asText(""))) {
            return TypeSafeSelection.failure("INVALID_RESPONSE");
        }

        String choice = answer.path("choice").asText(null);
        ConversationAction action = parseAction(choice);
        JsonNode confidenceNode = answer.path("confidence");
        if (action == null || !confidenceNode.isNumber() || !validProbabilities(answer.path("probabilities"))) {
            return TypeSafeSelection.failure("INVALID_RESPONSE");
        }
        double confidence = confidenceNode.doubleValue();
        if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
            return TypeSafeSelection.failure("INVALID_RESPONSE");
        }

        String model = response.path("model").asText(null);
        JsonNode usage = response.path("usage");
        Integer inputTokens = nonNegativeInt(usage.path("input_tokens"));
        Integer outputTokens = nonNegativeInt(usage.path("output_tokens"));
        if (model == null || model.isBlank() || inputTokens == null || outputTokens == null) {
            return TypeSafeSelection.failure("INVALID_RESPONSE");
        }
        return new TypeSafeSelection(
                action,
                confidence,
                model,
                inputTokens,
                outputTokens,
                null);
    }

    private static boolean validProbabilities(JsonNode probabilities) {
        if (probabilities == null || !probabilities.isObject()
                || probabilities.size() != ACTION_CRITERIA.size()) {
            return false;
        }
        double sum = 0.0;
        for (var field : probabilities.properties()) {
            String option = field.getKey();
            JsonNode probability = field.getValue();
            if (!ACTION_CRITERIA.containsKey(option) || !probability.isNumber()) {
                return false;
            }
            double value = probability.doubleValue();
            if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
                return false;
            }
            sum += value;
        }
        return Math.abs(sum - 1.0) <= 0.001;
    }

    private static ConversationAction parseAction(String value) {
        if (value == null) {
            return null;
        }
        try {
            ConversationAction action = ConversationAction.valueOf(value.trim().toUpperCase(Locale.ROOT));
            return action == ConversationAction.NONE ? null : action;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static Integer nonNegativeInt(JsonNode value) {
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            return null;
        }
        int parsed = value.intValue();
        return parsed >= 0 ? parsed : null;
    }

    private static RestClient createRestClient(ConversationRoutingProperties properties) {
        var timeout = properties.typesafe().requestTimeout();
        var httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    private static Map<String, String> actionCriteria() {
        Map<String, String> criteria = new LinkedHashMap<>();
        criteria.put("GREETING", "A greeting or conversational opener without a business request.");
        criteria.put("CATALOG_SEARCH", "Find, browse, or filter products; viewing a product is not a cart mutation.");
        criteria.put("ADD_TO_CART", "Explicitly add an identified product or variant to the customer's cart.");
        criteria.put("VIEW_CART", "View or summarize the customer's current cart.");
        criteria.put("REMOVE_FROM_CART", "Explicitly remove an identified item from the cart.");
        criteria.put("CLEAR_CART", "Explicitly clear the entire cart.");
        criteria.put("REVIEW_CHECKOUT", "Review the current checkout or order summary.");
        criteria.put("CONFIRM_CHECKOUT", "Explicitly confirm the current checkout.");
        criteria.put("CANCEL_CHECKOUT", "Explicitly cancel the current checkout.");
        criteria.put("PURCHASE_LINK", "Request a purchase or payment link for an identified product.");
        criteria.put("BUSINESS_HOURS", "Ask about the store's opening hours.");
        criteria.put("POLICY_QUERY", "Ask about shipping, payment, changes, returns, or another store policy.");
        criteria.put("HUMAN_HANDOFF", "Ask to speak with a person or human support agent.");
        criteria.put("GENERAL_SUPPORT", "Ask for help that does not fit another listed action.");
        criteria.put("UNKNOWN", "The request is unclear or none of the listed actions fits.");
        return Collections.unmodifiableMap(criteria);
    }

    record SystemOneRequest(String state, String model, Map<String, SystemOneQuestion> questions) {
    }

    record SystemOneQuestion(String type, String instructions, Map<String, String> criteria) {
    }
}
