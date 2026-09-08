package com.wally.customersupport.conversation.infrastructure.repository.postgres;

import java.util.UUID;
import java.time.Instant;
import java.util.List;

import com.wally.customersupport.conversation.infrastructure.repository.postgres.ProcessingAttemptJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataProcessingAttemptRepository extends JpaRepository<ProcessingAttemptJpaEntity, UUID> {

    @Query(value = """
            select p.*
            from wcs.processing_attempts p
            join wcs.messages m on m.id = p.message_id
            where p.status = 'PENDING'
              and p.available_at <= :now
              and not exists (
                  select 1
                  from wcs.processing_attempts earlier
                  join wcs.messages earlier_message on earlier_message.id = earlier.message_id
                  where earlier_message.conversation_id = m.conversation_id
                    and earlier.status in ('PENDING', 'PROCESSING')
                    and (
                        earlier.created_at < p.created_at
                        or (earlier.created_at = p.created_at and earlier.id < p.id)
                    )
              )
            order by p.created_at asc, p.id asc
            """, nativeQuery = true)
    List<ProcessingAttemptJpaEntity> findDue(@Param("now") Instant now, Pageable pageable);

    @Modifying
    @Query(value = """
            update wcs.processing_attempts
               set status = 'PENDING',
                   available_at = :now,
                   started_at = null,
                   updated_at = :now
             where status = 'PROCESSING'
               and started_at is not null
               and started_at <= :cutoff
            """, nativeQuery = true)
    int requeueStale(@Param("cutoff") Instant cutoff, @Param("now") Instant now);

    @Modifying
    @Query(value = """
            update wcs.processing_attempts target
               set status = 'PROCESSING',
                   attempt_count = target.attempt_count + 1,
                   started_at = :now,
                   updated_at = :now
             where target.id = :id
               and target.status = 'PENDING'
               and target.available_at <= :now
               and not exists (
                   select 1
                   from wcs.processing_attempts earlier
                   join wcs.messages earlier_message on earlier_message.id = earlier.message_id
                   join wcs.messages target_message on target_message.id = target.message_id
                   where earlier_message.conversation_id = target_message.conversation_id
                     and earlier.status in ('PENDING', 'PROCESSING')
                     and (
                         earlier.created_at < target.created_at
                         or (earlier.created_at = target.created_at and earlier.id < target.id)
                     )
               )
            """, nativeQuery = true)
    int claim(@Param("id") UUID id, @Param("now") Instant now);
}
