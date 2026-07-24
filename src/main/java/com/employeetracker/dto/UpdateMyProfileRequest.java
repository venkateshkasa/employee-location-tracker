package com.employeetracker.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload for the self-service "Edit Profile" action. Deliberately only
 * carries the three fields an employee/admin is allowed to change about
 * their own profile - Employee ID, Department, Designation, Role, Joining
 * Date and Username are intentionally NOT part of this request and can
 * only be changed by an admin via the Employee Management screens.
 */
public class UpdateMyProfileRequest {

    @NotBlank(message = "Mobile Number is required")
    private String mobile;

    private String address;

    // Either a new "data:image/...;base64,..." upload, an existing
    // "/uploads/profile-photos/..." URL left unchanged, or null/blank to
    // remove the photo - see FileStorageService#resolvePhotoUrl.
    private String photoUrl;

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }
}
