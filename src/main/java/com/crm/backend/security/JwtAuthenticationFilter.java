package com.crm.backend.security;

import com.crm.backend.tenant.Tenant;
import com.crm.backend.tenant.TenantRepository;
import com.crm.backend.user.User;
import com.crm.backend.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            TenantRepository tenantRepository,
            UserRepository userRepository) {
        this.jwtService = jwtService;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        try {
            String token = extractBearerToken(request);

            if (token != null && jwtService.validateToken(token)) {
                Long tenantId = jwtService.extractTenantId(token);
                Long userId = jwtService.extractUserId(token);

                if (tenantId != null && userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    Optional<Tenant> tenantOpt = tenantRepository.findById(tenantId);
                    Optional<User> userOpt = userRepository.findByTenantIdAndId(tenantId, userId);

                    // Enforce tenant and user active status on every request
                    if (tenantOpt.isPresent() && tenantOpt.get().isActive()
                            && userOpt.isPresent() && userOpt.get().isActive()) {

                        Tenant tenant = tenantOpt.get();
                        User user = userOpt.get();
                        UserPrincipal principal = UserPrincipal.create(user, tenant.getTenantCode());

                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        TenantContext.setTenantId(tenantId);
                        TenantContext.setTenantCode(tenant.getTenantCode());
                    }
                }
            }

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private String extractBearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
