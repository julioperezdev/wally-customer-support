package com.wally.customersupport.conversation.infrastructure.repository.postgres;

import java.util.Optional;
import java.time.Instant;
import java.util.UUID;

import com.wally.customersupport.conversation.infrastructure.repository.postgres.ConversationJpaEntity;
import com.wally.customersupport.conversation.infrastructure.repository.postgres.SpringDataConversationRepository;
import com.wally.customersupport.conversation.application.port.out.ConversationRepository;
import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.conversation.domain.model.Conversation;
import org.springframework.stereotype.Repository;

@Repository
public class JpaConversationRepositoryAdapter implements ConversationRepository {

    private final SpringDataConversationRepository repository;

    public JpaConversationRepositoryAdapter(SpringDataConversationRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Conversation> findByChannelAndExternalConversationId(
            Channel channel,
            String externalConversationId) {
        return repository.findByChannelAndExternalConversationId(channel, externalConversationId)
                .map(ConversationJpaEntity::toDomain);
    }

    @Override
    public Optional<Conversation> findById(UUID id) {
        return repository.findById(id).map(ConversationJpaEntity::toDomain);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public Conversation findOrCreate(
            Channel channel,
            String externalConversationId,
            String externalCustomerId,
            Instant now) {
        Conversation candidate = new Conversation(
                UUID.randomUUID(),
                channel,
                externalConversationId,
                externalCustomerId,
                com.wally.customersupport.conversation.domain.model.ConversationStatus.OPEN,
                now,
                now);
        repository.insertIfAbsent(
                candidate.id(),
                candidate.channel().name(),
                candidate.externalConversationId(),
                candidate.externalCustomerId(),
                candidate.status().name(),
                candidate.createdAt(),
                candidate.updatedAt());
        return repository.findByChannelAndExternalConversationId(channel, externalConversationId)
                .map(ConversationJpaEntity::toDomain)
                .orElseThrow(() -> new IllegalStateException("Conversation was not persisted"));
    }

    @Override
    public Conversation save(Conversation conversation) {
        return repository.save(new ConversationJpaEntity(conversation)).toDomain();
    }
}
