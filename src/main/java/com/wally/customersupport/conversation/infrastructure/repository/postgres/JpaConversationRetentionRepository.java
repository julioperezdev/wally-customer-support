package com.wally.customersupport.conversation.infrastructure.repository.postgres;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.wally.customersupport.conversation.application.port.out.ConversationRetentionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaConversationRetentionRepository implements ConversationRetentionRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public int redactMessageBodiesBefore(Instant cutoff, String replacement, int limit) {
        return entityManager.createNativeQuery("""
                update wcs.messages
                   set body = :replacement
                 where id in (
                       select id
                         from wcs.messages
                        where created_at < :cutoff
                          and body <> :replacement
                        order by created_at
                        limit :limit
                 )
                """)
                .setParameter("replacement", replacement)
                .setParameter("cutoff", cutoff)
                .setParameter("limit", Math.max(1, limit))
                .executeUpdate();
    }

    @Override
    @Transactional
    public int deleteMessagesBefore(Instant cutoff, int limit) {
        List<UUID> ids = entityManager.createNativeQuery("""
                select id
                  from wcs.messages
                 where created_at < :cutoff
                 order by created_at
                 limit :limit
                """, UUID.class)
                .setParameter("cutoff", cutoff)
                .setParameter("limit", Math.max(1, limit))
                .getResultList();
        if (ids.isEmpty()) {
            return 0;
        }

        entityManager.createNativeQuery("""
                delete from wcs.processing_attempts where message_id in (:ids)
                """)
                .setParameter("ids", ids)
                .executeUpdate();
        return entityManager.createNativeQuery("""
                delete from wcs.messages where id in (:ids)
                """)
                .setParameter("ids", ids)
                .executeUpdate();
    }
}
