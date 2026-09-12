package com.wally.customersupport.shared.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.shared.infrastructure.config.ObservabilityProperties;
import org.junit.jupiter.api.Test;

class ActorKeyGeneratorTest {

    private final ActorKeyGenerator generator = new ActorKeyGenerator(
            new ObservabilityProperties("unit-test-actor-key"));

    @Test
    void generatesTheSameKeyForTheSameChannelAndExternalIdentifier() {
        assertThat(generator.generate(Channel.TELEGRAM, "customer-1"))
                .isEqualTo(generator.generate(Channel.TELEGRAM, "customer-1"))
                .hasValueSatisfying(key -> assertThat(key).hasSize(64));
    }

    @Test
    void isolatesChannelsAndIdentifiersAndDoesNotExposeTheExternalIdentifier() {
        String telegramKey = generator.generate(Channel.TELEGRAM, "customer-1").orElseThrow();

        assertThat(telegramKey).doesNotContain("customer-1");
        assertThat(telegramKey).isNotEqualTo(generator.generate(Channel.WHATSAPP, "customer-1").orElseThrow());
        assertThat(telegramKey).isNotEqualTo(generator.generate(Channel.TELEGRAM, "customer-2").orElseThrow());
    }

    @Test
    void omitsTheKeyWhenTheServerSecretIsNotConfigured() {
        ActorKeyGenerator withoutSecret = new ActorKeyGenerator(new ObservabilityProperties(" "));

        assertThat(withoutSecret.generate(Channel.TELEGRAM, "customer-1")).isEmpty();
    }
}
