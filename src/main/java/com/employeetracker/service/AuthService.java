package com.employeetracker.service;

import com.employeetracker.config.CustomUserDetailsService;
import com.employeetracker.dto.LoginRequest;
import com.employeetracker.dto.LoginResponse;
import com.employeetracker.entity.ActivityType;
import com.employeetracker.entity.User;
import com.employeetracker.entity.UserRole;
import com.employeetracker.exception.UnauthorizedException;
import com.employeetracker.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;
    private final ActivityService activityService;
    private final UserRepository userRepository;
    private final StopDetectionService stopDetectionService;

    public AuthService(AuthenticationManager authenticationManager,
                       CustomUserDetailsService userDetailsService,
                       ActivityService activityService,
                       UserRepository userRepository,
                       StopDetectionService stopDetectionService) {
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.activityService = activityService;
        this.userRepository = userRepository;
        this.stopDetectionService = stopDetectionService;
    }

    @Transactional
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
        } catch (AuthenticationException ex) {
            // Do not leak whether the username exists or the password was wrong.
            throw new UnauthorizedException("Invalid username or password");
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        // Prevent session fixation: invalidate any pre-existing (e.g. anonymous) session
        // before issuing a fresh one for the newly authenticated user.
        HttpSession existingSession = httpRequest.getSession(false);
        if (existingSession != null) {
            existingSession.invalidate();
        }

        User user = getUserEntity(authentication);

        // Employee must appear OFFLINE by default and only become ONLINE after a
        // successful login - track that here rather than inferring it from
        // whatever location row happens to already be in the database.
        user.setIsLoggedIn(true);
        user.setIsTracking(false);
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        HttpSession session = httpRequest.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        session.setAttribute("userId", user.getUserId());

        // Activity logging is a "nice to have" audit trail - it must never be allowed
        // to break an otherwise successful login.
        try {
            if (user.getRole() == UserRole.EMPLOYEE) {
                activityService.logActivity(user.getUserId(), ActivityType.LOGIN, "Employee logged in", null, null, null);
            } else {
                activityService.logActivity(user.getUserId(), ActivityType.LOGIN, "Admin logged in", null, null, null);
            }
        } catch (Exception ex) {
            log.warn("Failed to record login activity for user {}", user.getUserId(), ex);
        }

        return mapToLoginResponse(user);
    }

    public LoginResponse getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new UnauthorizedException("Not authenticated");
        }
        return mapToLoginResponse(getUserEntity(authentication));
    }

    public User getCurrentUserEntity() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("Not authenticated");
        }
        return getUserEntity(authentication);
    }

    @Transactional
    public void logout(HttpServletRequest request) {
        User user = null;
        try {
            user = getCurrentUserEntity();
        } catch (UnauthorizedException ignored) {
            // Session may already be invalid
        }

        if (user != null) {
            // GPS tracking must stop and the employee must immediately become
            // OFFLINE on logout - never leave the previous ONLINE/MOVING state
            // hanging around for the admin dashboard to pick up.
            try {
                LocalDateTime now = LocalDateTime.now();
                stopDetectionService.closeOpenStopForUser(user.getUserId(), now);
                user.setIsLoggedIn(false);
                user.setIsTracking(false);
                user.setLastLogout(now);
                userRepository.save(user);
            } catch (Exception ex) {
                log.warn("Failed to update tracking state on logout for user {}", user.getUserId(), ex);
            }

            try {
                activityService.logActivity(user.getUserId(), ActivityType.LOGOUT, "User logged out", null, null, null);
            } catch (Exception ex) {
                log.warn("Failed to record logout activity for user {}", user.getUserId(), ex);
            }
        }

        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    private User getUserEntity(Authentication authentication) {
        return userDetailsService.getUserByUsername(authentication.getName());
    }

    private LoginResponse mapToLoginResponse(User user) {
        LoginResponse response = new LoginResponse();
        response.setUserId(user.getUserId());
        response.setEmployeeId(user.getEmployeeId());
        response.setName(user.getName());
        response.setEmail(user.getEmail());
        response.setUsername(user.getUsername());
        response.setRole(user.getRole().name());
        return response;
    }
}