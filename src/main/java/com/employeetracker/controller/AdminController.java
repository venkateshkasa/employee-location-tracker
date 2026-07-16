package com.employeetracker.controller;

import com.employeetracker.dto.AdminSummaryDto;
import com.employeetracker.dto.AdminNotificationDto;
import com.employeetracker.dto.ApiResponse;
import com.employeetracker.dto.EmployeeDto;
import com.employeetracker.dto.LocationResponse;
import com.employeetracker.dto.ReportDto;
import com.employeetracker.service.AdminService;
import com.employeetracker.service.NearbyPlaceService;
import com.employeetracker.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    public AdminController(AdminService adminService, ReportService reportService,
                            NearbyPlaceService nearbyPlaceService) {
        this.adminService = adminService;
        this.reportService = reportService;
        this.nearbyPlaceService = nearbyPlaceService;
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