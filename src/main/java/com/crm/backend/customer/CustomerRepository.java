package com.crm.backend.customer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByTenantIdAndIdAndIsActiveTrue(Long tenantId, Long id);

    Optional<Customer> findByTenantIdAndId(Long tenantId, Long id);

    Page<Customer> findAllByTenantIdAndIsActiveTrue(Long tenantId, Pageable pageable);

    @Query("""
        SELECT c FROM Customer c
        WHERE c.tenantId = :tenantId
          AND c.isActive = true
          AND (
              LOWER(c.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
              OR LOWER(c.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
              OR LOWER(CONCAT(c.firstName, ' ', c.lastName)) LIKE LOWER(CONCAT('%', :search, '%'))
              OR LOWER(c.email) LIKE LOWER(CONCAT('%', :search, '%'))
              OR LOWER(c.company) LIKE LOWER(CONCAT('%', :search, '%'))
          )
    """)
    Page<Customer> searchCustomers(
            @Param("tenantId") Long tenantId,
            @Param("search") String search,
            Pageable pageable
    );
}
