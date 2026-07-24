package com.employeetracker.controller;

import com.employeetracker.dto.ApiResponse;
import com.employeetracker.dto.ForgotPasswordRequest;
import com.employeetracker.dto.LoginRequest;
import com.employeetracker.dto.LoginResponse;
import com.employeetracker.dto.MyProfileDto;
import com.employeetracker.dto.PasswordChangeRequest;
import com.employeetracker.dto.SetupPasswordRequest;
import com.employeetracker.dto.UpdateMyProfileRequest;
import com.employeetracker.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request,
                                                            HttpServletRequest httpRequest) {
        LoginResponse response = authService.login(request, httpRequest);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request) {
        authService.logout(request);
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully", null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<LoginResponse>> getCurrentUser() {
        LoginResponse response = authService.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Read-only "My Profile" details for the logged-in user (Profile Photo,
     * Full Name, Employee ID, Email, Mobile Number, Department,
     * Designation, Joining Date, Office Location, Address).
     */
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<MyProfileDto>> getMyProfile() {
        return ResponseEntity.ok(ApiResponse.success(authService.getMyProfile()));
    }

    /**
     * Self-service "Edit Profile": only Profile Photo, Mobile Number and
     * Address can be changed here - see UpdateMyProfileRequest.
     */
    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<MyProfileDto>> updateMyProfile(@Valid @RequestBody UpdateMyProfileRequest request) {
        MyProfileDto updated = authService.updateMyProfile(request);
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully", updated));
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        authService.changePassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully", null));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        String token = authService.requestPasswordReset(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success(
                "Email is not configured. Placeholder reset token generated for admin/testing use.",
                token
        ));
    }

    /**
     * Used by the Password Setup page on load to decide whether to render
     * the New Password / Confirm Password form or the "Invalid or Expired
     * Link" state, without consuming the token.
     */
    @GetMapping("/validate-setup-token")
    public ResponseEntity<ApiResponse<Boolean>> validateSetupToken(@RequestParam String token) {
        boolean valid = authService.isSetupTokenValid(token);
        return ResponseEntity.ok(ApiResponse.success(valid));
    }

    /**
     * Completes account activation from the welcome-email link: sets the
     * employee's chosen password (stored via BCrypt) and invalidates the
     * setup token.
     */
    @PostMapping("/setup-password")
    public ResponseEntity<ApiResponse<Void>> setupPassword(@Valid @RequestBody SetupPasswordRequest request) {
        authService.setupPassword(request.getToken(), request.getNewPassword(), request.getConfirmPassword());
        return ResponseEntity.ok(ApiResponse.success("Password created successfully", null));
    }
}
