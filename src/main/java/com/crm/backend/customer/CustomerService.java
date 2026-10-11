package com.crm.backend.customer;

import com.crm.backend.common.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class CustomerService {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "id", "firstName", "lastName", "email", "phone", "company", "address", "createdAt", "updatedAt"
    );

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional
    public CustomerResponse createCustomer(Long tenantId, CreateCustomerRequest request) {
        String firstName = request.resolveFirstName();
        String lastName = request.resolveLastName();
        if (firstName.isBlank()) {
            throw new IllegalArgumentException("Customer first name (or name) is required");
        }
        if (lastName.isBlank()) {
            lastName = firstName;
        }

        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setFirstName(firstName);
        customer.setLastName(lastName);
        customer.setEmail(request.email() != null && !request.email().isBlank() ? request.email().trim().toLowerCase() : null);
        customer.setPhone(request.phone() != null && !request.phone().isBlank() ? request.phone().trim() : null);
        customer.setCompany(request.company() != null && !request.company().isBlank() ? request.company().trim() : null);
        customer.setAddress(request.address() != null && !request.address().isBlank() ? request.address().trim() : null);
        customer.setNotes(request.notes() != null && !request.notes().isBlank() ? request.notes().trim() : null);
        customer.setActive(true);

        Customer saved = customerRepository.save(customer);
        return CustomerResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponse> getCustomers(Long tenantId, String search, Pageable pageable) {
        Pageable safePageable = sanitizePageable(pageable);
        if (search != null && !search.trim().isBlank()) {
            return customerRepository.searchCustomers(tenantId, search.trim(), safePageable)
                    .map(CustomerResponse::fromEntity);
        }
        return customerRepository.findAllByTenantIdAndIsActiveTrue(tenantId, safePageable)
                .map(CustomerResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomerById(Long tenantId, Long customerId) {
        Customer customer = customerRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + customerId));
        return CustomerResponse.fromEntity(customer);
    }

    @Transactional
    public CustomerResponse updateCustomer(Long tenantId, Long customerId, UpdateCustomerRequest request) {
        Customer customer = customerRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + customerId));

        if (request.resolveFirstName() != null && !request.resolveFirstName().isBlank()) {
            customer.setFirstName(request.resolveFirstName());
        }
        if (request.resolveLastName() != null && !request.resolveLastName().isBlank()) {
            customer.setLastName(request.resolveLastName());
        }
        if (request.email() != null) {
            customer.setEmail(request.email().isBlank() ? null : request.email().trim().toLowerCase());
        }
        if (request.phone() != null) {
            customer.setPhone(request.phone().isBlank() ? null : request.phone().trim());
        }
        if (request.company() != null) {
            customer.setCompany(request.company().isBlank() ? null : request.company().trim());
        }
        if (request.address() != null) {
            customer.setAddress(request.address().isBlank() ? null : request.address().trim());
        }
        if (request.notes() != null) {
            customer.setNotes(request.notes().isBlank() ? null : request.notes().trim());
        }

        Customer saved = customerRepository.save(customer);
        return CustomerResponse.fromEntity(saved);
    }

    @Transactional
    public void deleteCustomer(Long tenantId, Long customerId) {
        Customer customer = customerRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + customerId));

        customer.setActive(false);
        customerRepository.save(customer);
    }

    private Pageable sanitizePageable(Pageable pageable) {
        int page = Math.max(0, pageable.getPageNumber());
        int size = Math.min(Math.max(1, pageable.getPageSize()), 100);

        if (pageable.getSort().isUnsorted()) {
            return PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"));
        }

        List<Sort.Order> safeOrders = new ArrayList<>();
        for (Sort.Order order : pageable.getSort()) {
            if (!ALLOWED_SORT_PROPERTIES.contains(order.getProperty())) {
                throw new IllegalArgumentException(
                        "Invalid sort property: '" + order.getProperty() + "'. Allowed properties: " + ALLOWED_SORT_PROPERTIES);
            }
            safeOrders.add(order);
        }

        return PageRequest.of(page, size, Sort.by(safeOrders));
    }
}
