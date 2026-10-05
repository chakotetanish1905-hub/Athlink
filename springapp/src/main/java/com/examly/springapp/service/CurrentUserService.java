package com.examly.springapp.service;

import java.util.Optional;

import com.examly.springapp.config.UserPrinciple;

/**
 * Reads the authenticated identity that JwtAuthenticationFilter placed in the SecurityContext.
 * Services use it for resource-ownership checks instead of trusting ids sent by the frontend.
 */
public interface CurrentUserService {

    Optional<UserPrinciple> getCurrentUser();

    boolean isManager();

    /** Returns the authenticated user's id or throws 401 if the request is anonymous. */
    Long requireCurrentUserId();

    /** Allows a Manager, or a Client whose id equals ownerId; otherwise throws 403. */
    void assertOwnerOrManager(Long ownerId);
}
