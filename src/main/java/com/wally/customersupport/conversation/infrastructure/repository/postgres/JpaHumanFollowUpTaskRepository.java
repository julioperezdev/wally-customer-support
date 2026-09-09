package com.wally.customersupport.conversation.infrastructure.repository.postgres;

import java.util.List;

import com.wally.customersupport.conversation.application.port.out.HumanFollowUpTaskRepository;
import com.wally.customersupport.conversation.domain.model.HumanFollowUpStatus;
import com.wally.customersupport.conversation.domain.model.HumanFollowUpTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class JpaHumanFollowUpTaskRepository implements HumanFollowUpTaskRepository {

    private final SpringDataHumanFollowUpTaskRepository repository;

    @Override
    @Transactional
    public HumanFollowUpTask saveIfAbsent(HumanFollowUpTask task) {
        repository.insertIfAbsent(
                task.id(),
                task.conversationId(),
                task.sourceMessageId(),
                task.reason(),
                task.priority().name(),
                task.status().name(),
                task.dueAt(),
                task.createdAt(),
                task.updatedAt(),
                task.completedAt());
        return repository.findBySourceMessageIdAndReason(task.sourceMessageId(), task.reason())
                .map(HumanFollowUpTaskJpaEntity::toDomain)
                .orElseThrow(() -> new IllegalStateException("Human follow-up task was not persisted"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<HumanFollowUpTask> findOpen(int limit) {
        return repository.findByStatusInOrderByDueAtAsc(
                        List.of(HumanFollowUpStatus.OPEN, HumanFollowUpStatus.IN_PROGRESS))
                .stream()
                .limit(Math.max(1, limit))
                .map(HumanFollowUpTaskJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countOpen() {
        return repository.countByStatusIn(
                List.of(HumanFollowUpStatus.OPEN, HumanFollowUpStatus.IN_PROGRESS));
    }
}
