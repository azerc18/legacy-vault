package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.Vault;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface VaultRepository extends JpaRepository<Vault, UUID> {
    List<Vault> findAllByOwnerId(UUID ownerId);

    // FR-08: lấy Vault của nhiều Owner trong một truy vấn
    List<Vault> findAllByOwnerIdIn(Collection<UUID> ownerIds);

    // FR-09: khóa bi quan các Vault khi tạo yêu cầu xác minh, tránh 2 yêu cầu trùng gửi song song
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Vault v WHERE v.id IN :ids")
    List<Vault> findAllByIdInForUpdate(@Param("ids") Collection<UUID> ids);
}
