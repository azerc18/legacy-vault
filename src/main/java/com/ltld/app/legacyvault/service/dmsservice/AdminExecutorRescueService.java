package com.ltld.app.legacyvault.service.dmsservice;

import com.ltld.app.legacyvault.dto.executordto.ExecutorAssignmentResponse;

import java.util.UUID;

/** B-01b: Admin gán Executor cho Owner có DMS đã TRIGGERED mà không còn Executor dùng được. */
public interface AdminExecutorRescueService {
    ExecutorAssignmentResponse rescue(UUID adminId, UUID ownerId, String email);
}