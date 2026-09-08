package com.wally.customersupport.conversation.infrastructure.repository.postgres;

import java.util.Optional;
import java.time.Instant;
import java.util.UUID;

import com.wally.customersupport.conversation.infrastructure.repository.postgres.ConversationJpaEntity;
import com.wally.customersupport.conversation.domain.model.Channel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataConversationRepository extends JpaRepository<ConversationJpaEntity, UUID> {

    Optional<ConversationJpaEntity> findByChannelAndExternalConversationId(
            Channel channel,
            String externalConversationId);

    @Modifying
    @Query(value = """
            insert into wcs.conversations (
                id, channel, external_conversation_id, external_customer_id,
                status, created_at, updated_at
            ) values (
                :id, :channel, :externalConversationId, :externalCustomerId,
                :status, :createdAt, :updatedAt
            ) on conflict (channel, external_conversation_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("channel") String channel,
            @Param("externalConversationId") String externalConversationId,
            @Param("externalCustomerId") String externalCustomerId,
            @Param("status") String status,
            @Param("createdAt") Instant createdAt,
            @Param("updatedAt") Instant updatedAt);
}
