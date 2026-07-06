package com.employeetracker.service;

import com.employeetracker.config.CustomUserDetailsService;
import com.employeetracker.dto.LoginRequest;
import com.employeetracker.dto.LoginResponse;
import com.employeetracker.entity.ActivityType;
import com.employeetracker.entity.User;
import com.employeetracker.entity.UserRole;
import com.employeetracker.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;
    private final ActivityService activityService;

    public AuthService(AuthenticationManager authenticationManager,
                       CustomUserDetailsService userDetailsService,
                       ActivityService activityService) {
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.activityService = activityService;
    }

    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);

            HttpSession session = httpRequest.getSession(true);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
            session.setAttribute("userId", getUserEntity(authentication).getUserId());

            User user = getUserEntity(authentication);
            if (user.getRole() == UserRole.EMPLOYEE) {
                activityService.logActivity(user.getUserId(), ActivityType.LOGIN, "Employee logged in", null, null, null);
            } else {
                activityService.logActivity(user.getUserId(), ActivityType.LOGIN, "Admin logged in", null, null, null);
            }

            return mapToLoginResponse(user);
        } catch (Exception ex) {
    ex.printStackTrace();
    throw new RuntimeException(ex);
}
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

    public void logout(HttpServletRequest request) {
        User user = null;
        try {
            user = getCurrentUserEntity();
        } catch (UnauthorizedException ignored) {
            // Session may already be invalid
        }

        if (user != null) {
            activityService.logActivity(user.getUserId(), ActivityType.LOGOUT, "User logged out", null, null, null);
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
