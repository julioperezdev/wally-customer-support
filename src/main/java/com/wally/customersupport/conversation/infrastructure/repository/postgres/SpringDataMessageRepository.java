package com.wally.customersupport.conversation.infrastructure.repository.postgres;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

import com.wally.customersupport.conversation.infrastructure.repository.postgres.MessageJpaEntity;
import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.conversation.domain.model.MessageDirection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataMessageRepository extends JpaRepository<MessageJpaEntity, UUID> {

    boolean existsByChannelAndExternalMessageId(Channel channel, String externalMessageId);

    java.util.Optional<MessageJpaEntity> findByChannelAndExternalMessageId(
            Channel channel,
            String externalMessageId);

    @Modifying
    @Query(value = """
            insert into wcs.messages (
                id, conversation_id, channel, external_message_id, direction,
                message_type, body, occurred_at, created_at
            ) values (
                :id, :conversationId, :channel, :externalMessageId, :direction,
                :messageType, :body, :occurredAt, :createdAt
            ) on conflict (channel, external_message_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("conversationId") UUID conversationId,
            @Param("channel") String channel,
            @Param("externalMessageId") String externalMessageId,
            @Param("direction") String direction,
            @Param("messageType") String messageType,
            @Param("body") String body,
            @Param("occurredAt") Instant occurredAt,
            @Param("createdAt") Instant createdAt);

    List<MessageJpaEntity> findTop20ByConversationIdOrderByOccurredAtDesc(UUID conversationId);

    List<MessageJpaEntity> findTop20ByConversationIdAndDirectionOrderByOccurredAtDesc(
            UUID conversationId,
            MessageDirection direction);

    List<MessageJpaEntity> findTop20ByConversationIdAndDirectionAndOccurredAtAfterOrderByOccurredAtDesc(
            UUID conversationId,
            MessageDirection direction,
            Instant occurredAfter);
}
