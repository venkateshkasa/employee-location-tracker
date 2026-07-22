package com.employeetracker.dto;

import jakarta.validation.constraints.NotBlank;

/** Payload for the admin "Activate / Deactivate" action on the Employee List. */
public class StatusUpdateRequest {

    @NotBlank(message = "Status is required")
    private String status; // ACTIVE / INACTIVE

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
