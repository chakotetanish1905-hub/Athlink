package com.examly.springapp.service;

import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.examly.springapp.config.UserPrinciple;
import com.examly.springapp.exceptions.ForbiddenOperationException;

@Service
public class CurrentUserServiceImpl implements CurrentUserService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CurrentUserServiceImpl.class);

    @Override
    public Optional<UserPrinciple> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrinciple principle) {
            return Optional.of(principle);
        }
        return Optional.empty();
    }

    @Override
    public boolean isManager() {
        return getCurrentUser().map(UserPrinciple::isManager).orElse(false);
    }

    @Override
    public Long requireCurrentUserId() {
        return getCurrentUser().map(UserPrinciple::getUserId)
                .orElseThrow(() -> new InsufficientAuthenticationException("Authentication is required."));
    }

    @Override
    public void assertOwnerOrManager(Long ownerId) {
        Optional<UserPrinciple> current = getCurrentUser();
        if (current.isEmpty()) {
            throw new InsufficientAuthenticationException("Authentication is required.");
        }
        UserPrinciple principle = current.get();
        if (principle.isManager()) {
            return;
        }
        if (!Objects.equals(principle.getUserId(), ownerId)) {
            LOGGER.debug("Ownership check failed: authenticated userId={} requested ownerId={}",
                    principle.getUserId(), ownerId);
            throw new ForbiddenOperationException("You are not authorized to access this resource.");
        }
    }
}
