package com.wally.customersupport.conversation.infrastructure.ai.typesafe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

import com.wally.customersupport.conversation.domain.model.ConversationAction;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.shared.infrastructure.config.ConversationRoutingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TypeSafeSystemOneClientTest {

    @Test
    void sendsChoiceRequestWithBearerKeyAndParsesTypedAnswer() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var properties = properties("synthetic-typesafe-api-key", 2_000, 6);
        var client = new TypeSafeSystemOneClient(builder.build(), properties);
        UUID conversationId = UUID.randomUUID();
        var context = new ConversationContext(
                conversationId, "synthetic-phone", "quiero agregarlo", List.of("quiero agregarlo", "buzo azul"),
                List.of());
        server.expect(requestTo("https://api.typesafe.ai/v1/systemone"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer synthetic-typesafe-api-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.model").value("jev-1.13.0"))
                .andExpect(jsonPath("$.questions.use_case.type").value("choice"))
                .andExpect(jsonPath("$.questions.use_case.criteria.ADD_TO_CART").exists())
                .andExpect(jsonPath("$.state").value(
                        "Recent conversation:\n- buzo azul\nCurrent customer message:\nquiero agregarlo"))
                .andExpect(content().string(not(containsString("synthetic-phone"))))
                .andExpect(content().string(not(containsString(conversationId.toString()))))
                .andRespond(withSuccess(choiceResponse("ADD_TO_CART", "0.93", 0.93),
                        MediaType.APPLICATION_JSON));

        var result = client.select(context);

        assertThat(result.successful()).isTrue();
        assertThat(result.action()).isEqualTo(ConversationAction.ADD_TO_CART);
        assertThat(result.confidence()).isEqualTo(0.93);
        assertThat(result.inputTokens()).isEqualTo(45);
        assertThat(result.outputTokens()).isEqualTo(7);
        server.verify();
    }

    @Test
    void excludesDuplicateLatestMessageAndBoundsState() {
        var properties = properties("synthetic-api-key", 128, 1);
        var context = new ConversationContext(
                UUID.randomUUID(), "synthetic-customer", "mensaje actual", List.of(
                        "mensaje actual", "mensaje previo más reciente", "mensaje previo antiguo"), List.of());
        var client = new TypeSafeSystemOneClient(RestClient.builder().build(), properties);
        String state = client.state(context);

        assertThat(state).hasSizeLessThanOrEqualTo(128);
        assertThat(state).contains("mensaje actual");
        assertThat(state).contains("mensaje previo más reciente");
        assertThat(state).doesNotContain("mensaje previo antiguo");
    }

    @Test
    void rejectsInvalidAnswersAndDoesNotCallProviderWithoutApiKey() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new TypeSafeSystemOneClient(builder.build(), properties("synthetic-key", 2_000, 6));
        server.expect(requestTo("https://api.typesafe.ai/v1/systemone"))
                .andRespond(withSuccess(choiceResponse("NONE", "0.9", 0.9), MediaType.APPLICATION_JSON));

        assertThat(client.select(new ConversationContext(null, null, "hola", List.of(), List.of())).successful())
                .isFalse();

        var missingKeyClient = new TypeSafeSystemOneClient(
                RestClient.builder().build(), properties(null, 2_000, 6));
        assertThat(missingKeyClient.select(new ConversationContext(null, null, "hola", List.of(), List.of()))
                .failureReason()).isEqualTo("MISSING_API_KEY");
        server.verify();
    }

    @Test
    void sanitizesHttpErrorsAndRejectsOutOfRangeConfidence() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new TypeSafeSystemOneClient(builder.build(), properties("synthetic-key", 2_000, 6));
        server.expect(requestTo("https://api.typesafe.ai/v1/systemone"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("provider error with private details"));
        server.expect(requestTo("https://api.typesafe.ai/v1/systemone"))
                .andRespond(withSuccess(choiceResponse("GREETING", "2.0", 0.9), MediaType.APPLICATION_JSON));

        var httpFailure = client.select(new ConversationContext(null, null, "hola", List.of(), List.of()));
        var invalidConfidence = client.select(new ConversationContext(null, null, "buenas", List.of(), List.of()));

        assertThat(httpFailure.failureReason()).isEqualTo("HTTP_401");
        assertThat(httpFailure.failureReason()).doesNotContain("private details");
        assertThat(invalidConfidence.failureReason()).isEqualTo("INVALID_RESPONSE");
        server.verify();
    }

    private static ConversationRoutingProperties properties(String key, int maxCharacters, int history) {
        return new ConversationRoutingProperties(
                "typesafe", "active", 0.65,
                new ConversationRoutingProperties.TypeSafe(
                        "https://api.typesafe.ai", "jev-1.13.0", key, java.time.Duration.ofSeconds(5), history,
                        maxCharacters));
    }

    private static String choiceResponse(String choice, String confidence, double selectedProbability) {
        List<ConversationAction> actions = java.util.Arrays.stream(ConversationAction.values())
                .filter(action -> action != ConversationAction.NONE)
                .toList();
        double otherProbability = (1.0 - selectedProbability) / (actions.size() - 1);
        String probabilities = actions.stream()
                .map(action -> "\"" + action.name() + "\":"
                        + String.format(Locale.ROOT, "%.8f", action.name().equals(choice)
                                ? selectedProbability
                                : otherProbability))
                .collect(Collectors.joining(","));
        return """
                {"model":"jev-1.13.0","answers":{"use_case":{"type":"choice",
                 "choice":"%s","confidence":%s,"probabilities":{%s}}},
                 "usage":{"input_tokens":45,"output_tokens":7}}
                """.formatted(choice, confidence, probabilities);
    }
}
