package com.employeetracker.controller;

import com.employeetracker.dto.AdminResetPasswordRequest;
import com.employeetracker.dto.AdminSummaryDto;
import com.employeetracker.dto.AdminNotificationDto;
import com.employeetracker.dto.ApiResponse;
import com.employeetracker.dto.CreateEmployeeRequest;
import com.employeetracker.dto.EmployeeDto;
import com.employeetracker.dto.LocationResponse;
import com.employeetracker.dto.ReportDto;
import com.employeetracker.dto.StatusUpdateRequest;
import com.employeetracker.dto.TrackingStopDto;
import com.employeetracker.dto.UpdateEmployeeRequest;
import com.employeetracker.service.AdminService;
import com.employeetracker.service.NearbyPlaceService;
import com.employeetracker.service.ReportService;
import com.employeetracker.service.TrackingStopService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final ReportService reportService;
    private final NearbyPlaceService nearbyPlaceService;
    private final TrackingStopService trackingStopService;

    public AdminController(AdminService adminService, ReportService reportService,
                            NearbyPlaceService nearbyPlaceService, TrackingStopService trackingStopService) {
        this.adminService = adminService;
        this.reportService = reportService;
        this.nearbyPlaceService = nearbyPlaceService;
        this.trackingStopService = trackingStopService;
    }

    /**
     * Stop History report: manual Tracking OFF stops with reason, remarks,
     * start/end time, duration, address and coordinates. Supports optional
     * Employee, Date From, Date To and Stop Reason filters.
     */
    @GetMapping("/stop-history")
    public ResponseEntity<ApiResponse<List<TrackingStopDto>>> getStopHistory(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String stopReason) {
        List<TrackingStopDto> stops = trackingStopService.searchStopHistory(userId, fromDate, toDate, stopReason);
        return ResponseEntity.ok(ApiResponse.success(stops));
    }

    @GetMapping("/employees")
    public ResponseEntity<ApiResponse<List<EmployeeDto>>> getEmployees() {
        List<EmployeeDto> employees = adminService.getAllEmployees();
        return ResponseEntity.ok(ApiResponse.success(employees));
    }

    @GetMapping("/employee/{id}")
    public ResponseEntity<ApiResponse<EmployeeDto>> getEmployee(@PathVariable Long id) {
        EmployeeDto employee = adminService.getEmployeeById(id);
        return ResponseEntity.ok(ApiResponse.success(employee));
    }

    @PostMapping("/employees")
    public ResponseEntity<ApiResponse<EmployeeDto>> createEmployee(@Valid @RequestBody CreateEmployeeRequest request) {
        EmployeeDto created = adminService.createEmployee(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Employee created successfully", created));
    }

    @PutMapping("/employees/{id}")
    public ResponseEntity<ApiResponse<EmployeeDto>> updateEmployee(@PathVariable Long id,
                                                                    @Valid @RequestBody UpdateEmployeeRequest request) {
        EmployeeDto updated = adminService.updateEmployee(id, request);
        return ResponseEntity.ok(ApiResponse.success("Employee updated successfully", updated));
    }

    @PutMapping("/employees/{id}/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@PathVariable Long id,
                                                             @Valid @RequestBody AdminResetPasswordRequest request) {
        adminService.resetPassword(id, request);
        return ResponseEntity.ok(ApiResponse.success("Password reset successfully", null));
    }

    @PutMapping("/employees/{id}/status")
    public ResponseEntity<ApiResponse<EmployeeDto>> updateStatus(@PathVariable Long id,
                                                                  @Valid @RequestBody StatusUpdateRequest request) {
        EmployeeDto updated = adminService.updateStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success("Employee status updated successfully", updated));
    }

    @DeleteMapping("/employees/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteEmployee(@PathVariable Long id) {
        adminService.deleteEmployee(id);
        return ResponseEntity.ok(ApiResponse.success("Employee deleted successfully", null));
    }

    @GetMapping("/live-locations")
    public ResponseEntity<ApiResponse<List<LocationResponse>>> getLiveLocations() {
        List<LocationResponse> locations = adminService.getLiveLocations();
        return ResponseEntity.ok(ApiResponse.success(locations));
    }

    @GetMapping("/notifications")
    public ResponseEntity<ApiResponse<List<AdminNotificationDto>>> getNotifications() {
        List<AdminNotificationDto> notifications = adminService.getNotifications();
        return ResponseEntity.ok(ApiResponse.success(notifications));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<AdminSummaryDto>> getSummary() {
        AdminSummaryDto summary = adminService.getSummary();
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    @GetMapping("/report")
    public ResponseEntity<ApiResponse<Object>> getReport(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false, defaultValue = "false") boolean allEmployees) throws IOException {

        if (fromDate == null) {
            fromDate = LocalDate.now();
        }
        if (toDate == null) {
            toDate = LocalDate.now();
        }

        if (allEmployees) {
            List<ReportDto> reports = reportService.generateAllEmployeeReports(fromDate, toDate);
            return ResponseEntity.ok(ApiResponse.success(reports));
        }

        if (userId == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("userId is required when allEmployees is false"));
        }

        ReportDto report = reportService.generateReport(userId, fromDate, toDate);
        return ResponseEntity.ok(ApiResponse.success(report));
    }

    @GetMapping("/report/export")
    public ResponseEntity<byte[]> exportReport(
            @RequestParam Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) throws IOException {

        if (fromDate == null) {
            fromDate = LocalDate.now();
        }
        if (toDate == null) {
            toDate = LocalDate.now();
        }

        byte[] excelData = reportService.exportReportToExcel(userId, fromDate, toDate);
        String filename = "employee-report-" + userId + "-" + fromDate + "-to-" + toDate + ".xlsx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelData);
    }

    @GetMapping("/report/export/pdf")
    public ResponseEntity<byte[]> exportReportPdf(
            @RequestParam Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {

        if (fromDate == null) {
            fromDate = LocalDate.now();
        }
        if (toDate == null) {
            toDate = LocalDate.now();
        }

        byte[] pdfData = reportService.exportReportToPdf(userId, fromDate, toDate);
        String filename = "employee-report-" + userId + "-" + fromDate + "-to-" + toDate + ".pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfData);
    }

    /**
     * Get nearby places for the admin dashboard.
     * <p>
     * When {@code userId}, {@code radius}, {@code latitude} and
     * {@code longitude} are all supplied (as sent when the admin focuses on
     * a specific employee with a live geofence circle), this performs the
     * same radius-scoped, colleges/universities-only live search used by the
     * employee dashboard's Radius dropdown, reusing NearbyPlaceService so
     * the two views stay consistent. Otherwise it falls back to the
     * original behavior of returning all currently active nearby places
     * across every employee, so existing callers are unaffected.
     */
    @GetMapping("/nearby-places")
    public ResponseEntity<ApiResponse<List<com.employeetracker.dto.NearbyPlaceDto>>> getNearbyPlaces(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Integer radius,
            @RequestParam(required = false) BigDecimal latitude,
            @RequestParam(required = false) BigDecimal longitude) {

        if (userId != null && radius != null && latitude != null && longitude != null) {
            List<com.employeetracker.dto.NearbyPlaceDto> places =
                    nearbyPlaceService.searchNearbyPlaces(userId, latitude, longitude, radius);
            return ResponseEntity.ok(ApiResponse.success(places));
        }

        List<com.employeetracker.dto.NearbyPlaceDto> places = adminService.getNearbyPlaces();
        return ResponseEntity.ok(ApiResponse.success(places));
    }
}