package com.wally.customersupport.conversation.infrastructure.repository.postgres;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wally.customersupport.conversation.infrastructure.repository.postgres.MessageJpaEntity;
import com.wally.customersupport.conversation.infrastructure.repository.postgres.SpringDataMessageRepository;
import com.wally.customersupport.conversation.application.port.out.MessageRepository;
import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.conversation.domain.model.Message;
import com.wally.customersupport.conversation.domain.model.MessageDirection;
import com.wally.customersupport.conversation.domain.model.MessageWriteResult;
import org.springframework.stereotype.Repository;

@Repository
public class JpaMessageRepositoryAdapter implements MessageRepository {

    private final SpringDataMessageRepository repository;

    public JpaMessageRepositoryAdapter(SpringDataMessageRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean existsByExternalMessageId(Channel channel, String externalMessageId) {
        return repository.existsByChannelAndExternalMessageId(channel, externalMessageId);
    }

    @Override
    public Optional<Message> findById(UUID id) {
        return repository.findById(id).map(MessageJpaEntity::toDomain);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public MessageWriteResult saveIfAbsent(Message message) {
        int inserted = repository.insertIfAbsent(
                message.id(),
                message.conversationId(),
                message.channel().name(),
                message.externalMessageId(),
                message.direction().name(),
                message.messageType().name(),
                message.body(),
                message.occurredAt(),
                message.createdAt());
        Message persisted = repository.findByChannelAndExternalMessageId(
                        message.channel(), message.externalMessageId())
                .map(MessageJpaEntity::toDomain)
                .orElseThrow(() -> new IllegalStateException("Message was not persisted"));
        return new MessageWriteResult(persisted, inserted == 1);
    }

    @Override
    public Message save(Message message) {
        return repository.save(new MessageJpaEntity(message)).toDomain();
    }

    @Override
    public List<String> findRecentBodies(UUID conversationId, int limit) {
        return repository.findTop20ByConversationIdAndDirectionOrderByOccurredAtDesc(
                        conversationId, MessageDirection.INBOUND).stream()
                .limit(Math.max(1, limit))
                .map(MessageJpaEntity::body)
                .toList();
    }

    @Override
    public List<String> findRecentBodiesAfter(UUID conversationId, Instant occurredAfter, int limit) {
        return repository.findTop20ByConversationIdAndDirectionAndOccurredAtAfterOrderByOccurredAtDesc(
                        conversationId, MessageDirection.INBOUND, occurredAfter).stream()
                .limit(Math.max(1, limit))
                .map(MessageJpaEntity::body)
                .toList();
    }
}
