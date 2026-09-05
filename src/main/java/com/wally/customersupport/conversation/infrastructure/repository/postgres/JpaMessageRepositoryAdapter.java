package com.wally.customersupport.conversation.infrastructure.repository.postgres;

import java.util.List;
import java.util.UUID;

import com.wally.customersupport.conversation.infrastructure.repository.postgres.MessageJpaEntity;
import com.wally.customersupport.conversation.infrastructure.repository.postgres.SpringDataMessageRepository;
import com.wally.customersupport.conversation.application.port.out.MessageRepository;
import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.conversation.domain.model.Message;
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
    public Message save(Message message) {
        return repository.save(new MessageJpaEntity(message)).toDomain();
    }

    @Override
    public List<String> findRecentBodies(UUID conversationId, int limit) {
        return repository.findTop20ByConversationIdOrderByOccurredAtDesc(conversationId).stream()
                .limit(Math.max(1, limit))
                .map(MessageJpaEntity::body)
                .toList();
    }
}
