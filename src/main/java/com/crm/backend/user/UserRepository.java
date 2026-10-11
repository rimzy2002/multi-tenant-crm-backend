package com.crm.backend.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByTenantIdAndEmail(Long tenantId, String email);

    Optional<User> findByTenantIdAndId(Long tenantId, Long id);

    boolean existsByTenantIdAndEmail(Long tenantId, String email);

    List<User> findAllByTenantId(Long tenantId);

    Page<User> findAllByTenantId(Long tenantId, Pageable pageable);

    long countByTenantIdAndRoleAndIsActiveTrue(Long tenantId, Role role);
}
