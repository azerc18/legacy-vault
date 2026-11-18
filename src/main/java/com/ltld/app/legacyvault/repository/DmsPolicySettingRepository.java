package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.DmsPolicySetting;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DmsPolicySettingRepository extends JpaRepository<DmsPolicySetting, Byte> {

    // Khoá dòng khi Admin lưu: 2 Admin lưu cùng lúc thì người sau chờ người trước commit,
    // rồi so sánh / ghi audit trên giá trị mới nhất (không ghi đè mù), giống UserRepository.findByIdForUpdate.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DmsPolicySetting s where s.id = :id")
    Optional<DmsPolicySetting> findByIdForUpdate(@Param("id") Byte id);
}
