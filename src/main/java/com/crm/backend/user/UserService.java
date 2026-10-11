package com.crm.backend.user;

import com.crm.backend.common.DuplicateResourceException;
import com.crm.backend.common.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class UserService {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "id", "email", "firstName", "lastName", "role", "isActive", "createdAt", "updatedAt"
    );

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse createUser(Long tenantId, CreateUserRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        if (userRepository.existsByTenantIdAndEmail(tenantId, normalizedEmail)) {
            throw new DuplicateResourceException(
                    "User with email '" + normalizedEmail + "' already exists in this tenant");
        }

        String passwordHash = passwordEncoder.encode(request.password());
        User user = new User(
                tenantId,
                normalizedEmail,
                passwordHash,
                request.firstName().trim(),
                request.lastName().trim(),
                request.role()
        );

        User saved = userRepository.save(user);
        return UserResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> getUsers(Long tenantId, Pageable pageable) {
        Pageable safePageable = sanitizePageable(pageable);
        return userRepository.findAllByTenantId(tenantId, safePageable)
                .map(UserResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long tenantId, Long userId) {
        User user = userRepository.findByTenantIdAndId(tenantId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        return UserResponse.fromEntity(user);
    }

    @Transactional
    public UserResponse updateUser(Long tenantId, Long userId, UpdateUserRequest request) {
        User user = userRepository.findByTenantIdAndId(tenantId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        if (request.email() != null && !request.email().isBlank()) {
            String normalizedEmail = request.email().trim().toLowerCase();
            if (!normalizedEmail.equalsIgnoreCase(user.getEmail())) {
                if (userRepository.existsByTenantIdAndEmail(tenantId, normalizedEmail)) {
                    throw new DuplicateResourceException(
                            "User with email '" + normalizedEmail + "' already exists in this tenant");
                }
                user.setEmail(normalizedEmail);
            }
        }

        if (user.getRole() == Role.TENANT_ADMIN && user.isActive()) {
            boolean demoting = request.role() != Role.TENANT_ADMIN;
            boolean deactivating = Boolean.FALSE.equals(request.active());
            if (demoting || deactivating) {
                long activeAdminCount = userRepository.countByTenantIdAndRoleAndIsActiveTrue(tenantId, Role.TENANT_ADMIN);
                if (activeAdminCount <= 1) {
                    throw new IllegalArgumentException("Cannot demote or deactivate the only active administrator for this tenant");
                }
            }
        }

        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setRole(request.role());
        if (request.active() != null) {
            user.setActive(request.active());
        }

        User saved = userRepository.save(user);
        return UserResponse.fromEntity(saved);
    }

    @Transactional
    public UserResponse updateUserStatus(Long tenantId, Long userId, UpdateUserStatusRequest request) {
        User user = userRepository.findByTenantIdAndId(tenantId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        if (user.getRole() == Role.TENANT_ADMIN && user.isActive() && Boolean.FALSE.equals(request.active())) {
            long activeAdminCount = userRepository.countByTenantIdAndRoleAndIsActiveTrue(tenantId, Role.TENANT_ADMIN);
            if (activeAdminCount <= 1) {
                throw new IllegalArgumentException("Cannot deactivate the only active administrator for this tenant");
            }
        }

        user.setActive(request.active());
        User saved = userRepository.save(user);
        return UserResponse.fromEntity(saved);
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
