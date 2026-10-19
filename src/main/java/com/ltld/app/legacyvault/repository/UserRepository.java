package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.UserStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    boolean existsByEmail(String email);
    Optional<User> findByEmail(String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.email = :email")
    Optional<User> findByEmailForUpdate(@Param("email") String email);

    // FR-06: khóa dòng User trước khi thêm/gỡ role EXECUTOR, để hai Owner thao tác trên cùng một người
    // không ghi đè quyết định của nhau.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT DISTINCT u FROM User u JOIN u.roles r WHERE r.code = :roleCode AND u.status = com.ltld.app.legacyvault.enums.UserStatus.ACTIVE")
    List<User> findActiveByRoleCode(@Param("roleCode") String roleCode);

    @Query(value = """
    select distinct u from User u left join u.roles r
    where (:q is null or lower(u.email) like :q or lower(u.fullName) like :q)
      and (:status is null or u.status = :status)
      and (:roleCode is null or r.code = :roleCode)
    """,
            countQuery = """
                    select count(distinct u) from User u left join u.roles r
                    where (:q is null or lower(u.email) like :q or lower(u.fullName) like :q)
                      and (:status is null or u.status = :status)
                      and (:roleCode is null or r.code = :roleCode)
                    """)
    Page<User> searchForAdmin(@Param("q") String q,
                              @Param("roleCode") String roleCode,
                              @Param("status") UserStatus status,
                              Pageable pageable);
}
