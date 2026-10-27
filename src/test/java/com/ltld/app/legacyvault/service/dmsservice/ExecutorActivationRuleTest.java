package com.ltld.app.legacyvault.service.dmsservice;

import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B-01a: các tổ hợp dưới đây PHẢI khớp với cách DmsActivationProcessor.trigger chọn người khi DMS kích hoạt:
 * Primary có status != REVOKED, nếu không có thì Backup có status == INACTIVE, nếu không có nữa thì không ai
 * (DMS_TRIGGERED_NO_EXECUTOR). Trigger chưa dùng ExecutorActivationRule; khi chuyển trigger sang dùng helper
 * (PR riêng) thì bảng này là hợp đồng để đối chiếu, và đổi một bên phải đổi cả bên kia.
 */
class ExecutorActivationRuleTest {

    // Cột trống = Owner chưa có dòng cho vị trí đó. Cột 3: vị trí được chọn (trống = không ai).
    @ParameterizedTest(name = "Primary={0}, Backup={1} -> {2}")
    @CsvSource({
            "INACTIVE, , PRIMARY",
            "ACTIVE, , PRIMARY",
            "INACTIVE, INACTIVE, PRIMARY",
            "INACTIVE, REVOKED, PRIMARY",
            "ACTIVE, ACTIVE, PRIMARY",
            "REVOKED, INACTIVE, BACKUP",
            ", INACTIVE, BACKUP",
            "REVOKED, ACTIVE, ",
            "REVOKED, REVOKED, ",
            ", ACTIVE, ",
            ", REVOKED, ",
            "REVOKED, , ",
            ", , "
    })
    @DisplayName("B-01a: chọn Primary chưa REVOKED, nếu không có thì Backup INACTIVE (khớp DmsActivationProcessor.trigger)")
    void pickActivationTarget_matchesTriggerSelection(ExecutorStatus primary, ExecutorStatus backup, ExecutorType expected) {
        List<ExecutorAssignment> rows = new ArrayList<>();
        // Backup trước Primary: thứ tự từ DB không được ảnh hưởng kết quả.
        if (backup != null) rows.add(assignment(ExecutorType.BACKUP, backup));
        if (primary != null) rows.add(assignment(ExecutorType.PRIMARY, primary));

        assertThat(ExecutorActivationRule.pickActivationTarget(rows).map(ExecutorAssignment::getExecutorType))
                .isEqualTo(Optional.ofNullable(expected));
    }

    @Test
    @DisplayName("B-01a: isUsablePrimary / isUsableBackup kiểm cả loại vị trí, không chỉ trạng thái")
    void predicates_checkExecutorType() {
        assertThat(ExecutorActivationRule.isUsablePrimary(assignment(ExecutorType.BACKUP, ExecutorStatus.INACTIVE))).isFalse();
        assertThat(ExecutorActivationRule.isUsableBackup(assignment(ExecutorType.PRIMARY, ExecutorStatus.INACTIVE))).isFalse();
        assertThat(ExecutorActivationRule.isUsablePrimary(assignment(ExecutorType.PRIMARY, ExecutorStatus.ACTIVE))).isTrue();
        assertThat(ExecutorActivationRule.isUsableBackup(assignment(ExecutorType.BACKUP, ExecutorStatus.ACTIVE))).isFalse();
    }

    private static ExecutorAssignment assignment(ExecutorType type, ExecutorStatus status) {
        return ExecutorAssignment.builder().id(UUID.randomUUID()).executorType(type).status(status).build();
    }
}
