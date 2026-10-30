package com.ltld.app.legacyvault.service.dmsservice;

import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.UserStatus;

import java.util.Collection;

public final class ExecutorRescueRule {
    private ExecutorRescueRule() {
    }

    public static boolean isUsable(ExecutorAssignment a) {
        return a.getStatus() == ExecutorStatus.ACTIVE
                && a.getExecutorUser() != null
                && a.getExecutorUser().getStatus() == UserStatus.ACTIVE;
    }

    public static boolean rescueNeeded(DmsConfig config, Collection<ExecutorAssignment> assignmentsOfOwner) {
        return config != null
                && config.getStatus() == DmsStatus.TRIGGERED
                && assignmentsOfOwner.stream().noneMatch(ExecutorRescueRule::isUsable);
    }
}