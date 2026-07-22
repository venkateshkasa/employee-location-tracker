package com.employeetracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SetupPasswordRequest {

    @NotBlank
    private String token;

    @NotBlank
    @Size(min = 8, message = "Password must be at least 8 characters long")
    private String newPassword;

    @NotBlank
    private String confirmPassword;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

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
