package com.wally.customersupport.adapter.out.persistence;

import java.util.Optional;

import com.wally.customersupport.adapter.out.persistence.entity.ConversationJpaEntity;
import com.wally.customersupport.adapter.out.persistence.repository.SpringDataConversationRepository;
import com.wally.customersupport.application.port.out.ConversationRepository;
import com.wally.customersupport.domain.model.Channel;
import com.wally.customersupport.domain.model.Conversation;
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
    public Conversation save(Conversation conversation) {
        return repository.save(new ConversationJpaEntity(conversation)).toDomain();
    }
}
