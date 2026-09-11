package com.wally.customersupport.conversation.infrastructure.repository.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import com.wally.customersupport.conversation.application.port.out.ContactSuppressionRepository;
import com.wally.customersupport.conversation.application.port.out.ConversationRepository;
import com.wally.customersupport.conversation.application.port.out.HumanFollowUpTaskRepository;
import com.wally.customersupport.conversation.application.port.out.MessageRepository;
import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.conversation.domain.model.ContactSuppression;
import com.wally.customersupport.conversation.domain.model.Conversation;
import com.wally.customersupport.conversation.domain.model.ConversationStatus;
import com.wally.customersupport.conversation.domain.model.HumanFollowUpPriority;
import com.wally.customersupport.conversation.domain.model.HumanFollowUpTask;
import com.wally.customersupport.conversation.domain.model.Message;
import com.wally.customersupport.conversation.domain.model.MessageDirection;
import com.wally.customersupport.conversation.domain.model.MessageType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class HumanFollowUpAndSuppressionPersistenceIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("wcs_follow_up_test")
            .withUsername("wcs")
            .withPassword("test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private HumanFollowUpTaskRepository followUpTaskRepository;

    @Autowired
    private ContactSuppressionRepository suppressionRepository;

    @Test
    void persistsIdempotentFollowUpAndPseudonymizedSuppression() {
        String suffix = UUID.randomUUID().toString();
        Conversation conversation = conversationRepository.findOrCreate(
                Channel.TELEGRAM,
                "chat-" + suffix,
                "customer-" + suffix,
                NOW);
        Message source = new Message(
                UUID.randomUUID(),
                conversation.id(),
                Channel.TELEGRAM,
                "message-" + suffix,
                MessageDirection.INBOUND,
                MessageType.TEXT,
                "Quiero hablar con una persona",
                NOW,
                NOW);
        Message persistedMessage = messageRepository.saveIfAbsent(source).message();

        HumanFollowUpTask task = HumanFollowUpTask.open(
                conversation.id(),
                persistedMessage.id(),
                "HUMAN_REQUEST",
                HumanFollowUpPriority.HIGH,
                NOW.plusSeconds(86400),
                NOW);
        HumanFollowUpTask first = followUpTaskRepository.saveIfAbsent(task);
        HumanFollowUpTask retry = followUpTaskRepository.saveIfAbsent(
                HumanFollowUpTask.open(
                        conversation.id(),
                        persistedMessage.id(),
                        "HUMAN_REQUEST",
                        HumanFollowUpPriority.HIGH,
                        NOW.plusSeconds(86400),
                        NOW.plusSeconds(1)));

        String actorKey = "a".repeat(64);
        ContactSuppression suppression = ContactSuppression.doNotContact(actorKey, persistedMessage.id(), NOW);
        ContactSuppression persistedSuppression = suppressionRepository.saveIfAbsent(suppression);

        assertThat(first.id()).isEqualTo(task.id());
        assertThat(retry.id()).isEqualTo(first.id());
        assertThat(followUpTaskRepository.countOpen()).isEqualTo(1);
        assertThat(suppressionRepository.existsActiveByActorKey(actorKey)).isTrue();
        assertThat(persistedSuppression.actorKey()).isEqualTo(actorKey);
        assertThat(suppressionRepository.reactivate(actorKey, NOW.plusSeconds(10))).isTrue();
        assertThat(suppressionRepository.existsActiveByActorKey(actorKey)).isFalse();
        assertThat(suppressionRepository.findLastReactivationAt(actorKey))
                .contains(NOW.plusSeconds(10));
        assertThat(suppressionRepository.saveIfAbsent(
                ContactSuppression.doNotContact(actorKey, persistedMessage.id(), NOW.plusSeconds(20)))
                .status()).isEqualTo("DO_NOT_CONTACT");
        assertThat(suppressionRepository.existsActiveByActorKey(actorKey)).isTrue();
        assertThat(conversation.status()).isEqualTo(ConversationStatus.OPEN);
    }
}
