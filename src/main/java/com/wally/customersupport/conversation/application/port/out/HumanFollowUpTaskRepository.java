package com.wally.customersupport.conversation.application.port.out;

import java.util.List;
import java.util.UUID;

import com.wally.customersupport.conversation.domain.model.HumanFollowUpTask;
import com.wally.customersupport.conversation.domain.model.HumanFollowUpPriority;
import com.wally.customersupport.conversation.domain.model.HumanFollowUpStatus;

public interface HumanFollowUpTaskRepository {

    HumanFollowUpTask saveIfAbsent(HumanFollowUpTask task);

    List<HumanFollowUpTask> findOpen(int limit);

    List<HumanFollowUpTask> findOpen(int limit, HumanFollowUpStatus status, HumanFollowUpPriority priority);

    long countOpen();
}
