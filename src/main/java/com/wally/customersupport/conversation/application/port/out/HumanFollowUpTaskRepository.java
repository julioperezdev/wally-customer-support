package com.wally.customersupport.conversation.application.port.out;

import java.util.List;
import java.util.UUID;

import com.wally.customersupport.conversation.domain.model.HumanFollowUpTask;

public interface HumanFollowUpTaskRepository {

    HumanFollowUpTask saveIfAbsent(HumanFollowUpTask task);

    List<HumanFollowUpTask> findOpen(int limit);

    long countOpen();
}
