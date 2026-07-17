package com.employeetracker.service;

import com.employeetracker.config.CustomUserDetailsService;
import com.employeetracker.dto.LoginRequest;
import com.employeetracker.dto.LoginResponse;
import com.employeetracker.dto.PasswordChangeRequest;
import com.employeetracker.entity.ActivityType;
import com.employeetracker.entity.User;
import com.employeetracker.entity.UserRole;
import com.employeetracker.exception.BadRequestException;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;
    private final ActivityService activityService;
    private final UserRepository userRepository;
    private final StopDetectionService stopDetectionService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AuthenticationManager authenticationManager,
                       CustomUserDetailsService userDetailsService,
                       ActivityService activityService,
                       UserRepository userRepository,
                       StopDetectionService stopDetectionService,
                       PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.activityService = activityService;
        this.userRepository = userRepository;
        this.stopDetectionService = stopDetectionService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
        } catch (AuthenticationException ex) {
            // Server-side diagnostic only - the client always gets the same generic
            // message below, so this does not change any external behavior.
            log.warn("Login failed for username '{}': {} - {}",
                    request.getUsername(), ex.getClass().getSimpleName(), ex.getMessage());
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

        if (user.getRole() == UserRole.EMPLOYEE) {
            user.setLoggedIn(true);
            user.setTrackingEnabled(true);
            user.setLastLoginTime(LocalDateTime.now());
            userRepository.save(user);
        }

        HttpSession session = httpRequest.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        session.setAttribute("userId", user.getUserId());

        // Activity logging is a "nice to have" audit trail - it must never be allowed
        // to break an otherwise successful login.
        try {
                if (user.getRole() == UserRole.EMPLOYEE) {
                    activityService.logActivity(user.getUserId(), ActivityType.LOGIN, "Employee logged in", null, null, null);
                    activityService.logActivity(user.getUserId(), ActivityType.TRACKING_ENABLED, "Tracking enabled", null, null, null);
                    activityService.logActivity(user.getUserId(), ActivityType.CHECK_IN, "Check In detected from tracking", null, null, null);
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
            try {
                if (user.getRole() == UserRole.EMPLOYEE) {
                    if (user.isTrackingEnabled()) {
                        activityService.logActivity(user.getUserId(), ActivityType.TRACKING_DISABLED, "Tracking disabled", null, null, null);
                    }
                    user.setLoggedIn(false);
                    user.setTrackingEnabled(false);
                    user.setLastLogoutTime(LocalDateTime.now());
                    userRepository.save(user);
                    stopDetectionService.closeOpenStopForUser(user.getUserId(), LocalDateTime.now());
                    activityService.logActivity(user.getUserId(), ActivityType.CHECK_OUT, "Check Out detected from tracking", null, null, null);
                }
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

    @Transactional
    public void changePassword(PasswordChangeRequest request) {
        User user = getCurrentUserEntity();
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        activityService.logActivity(user.getUserId(), ActivityType.PASSWORD_CHANGED, "Password changed", null, null, null);
    }

    @Transactional
    public String requestPasswordReset(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("No account found for the supplied email"));
        String token = UUID.randomUUID().toString();
        user.setPasswordResetToken(token);
        userRepository.save(user);
        activityService.logActivity(user.getUserId(), ActivityType.PASSWORD_RESET_REQUESTED,
                "Password reset requested. Email service is not configured; reset token generated.", null, null, null);
        return token;
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
        response.setLoggedIn(user.isLoggedIn());
        response.setTrackingEnabled(user.isTrackingEnabled());
        response.setPhotoUrl(user.getPhotoUrl());
        response.setPhone(user.getPhone());
        response.setDepartment(user.getDepartment());
        response.setDesignation(user.getDesignation());
        response.setManager(user.getManager());
        return response;
    }
}