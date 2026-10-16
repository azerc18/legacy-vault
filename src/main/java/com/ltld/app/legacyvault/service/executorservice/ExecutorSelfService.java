package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.MyExecutorAssignmentResponse;

import java.util.List;

/** B1: Executor tự xem các assignment của mình. */
public interface ExecutorSelfService {

    /** Mọi assignment chưa REVOKED của người gọi (có thể thuộc nhiều Owner); rỗng nếu không có. */
    List<MyExecutorAssignmentResponse> listMyAssignments(String executorLogin);
}
