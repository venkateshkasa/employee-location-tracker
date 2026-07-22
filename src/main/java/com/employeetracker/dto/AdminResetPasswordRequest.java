package com.employeetracker.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload for the admin "Reset Password" action on the Employee List.
 * Distinct from PasswordChangeRequest (used by an employee changing their
 * own password), since an admin resetting someone else's password does not
 * know/need their current password.
 */
public class AdminResetPasswordRequest {

    @NotBlank(message = "New Password is required")
    private String newPassword;

    @NotBlank(message = "Confirm Password is required")
    private String confirmPassword;

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}
