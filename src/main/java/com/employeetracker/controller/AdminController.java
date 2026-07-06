package com.employeetracker.controller;

import com.employeetracker.dto.AdminSummaryDto;
import com.employeetracker.dto.ApiResponse;
import com.employeetracker.dto.EmployeeDto;
import com.employeetracker.dto.LocationResponse;
import com.employeetracker.dto.ReportDto;
import com.employeetracker.service.AdminService;
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
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final ReportService reportService;

    public AdminController(AdminService adminService, ReportService reportService) {
        this.adminService = adminService;
        this.reportService = reportService;
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

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<AdminSummaryDto>> getSummary() {
        AdminSummaryDto summary = adminService.getSummary();
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    @GetMapping("/report")
    public ResponseEntity<ApiResponse<Object>> getReport(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false, defaultValue = "false") boolean allEmployees) throws IOException {

        if (date == null) {
            date = LocalDate.now();
        }

        if (allEmployees) {
            List<ReportDto> reports = reportService.generateAllEmployeeReports(date);
            return ResponseEntity.ok(ApiResponse.success(reports));
        }

        if (userId == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("userId is required when allEmployees is false"));
        }

        ReportDto report = reportService.generateReport(userId, date);
        return ResponseEntity.ok(ApiResponse.success(report));
    }

    @GetMapping("/report/export")
    public ResponseEntity<byte[]> exportReport(
            @RequestParam Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) throws IOException {

        if (date == null) {
            date = LocalDate.now();
        }

        byte[] excelData = reportService.exportReportToExcel(userId, date);
        String filename = "employee-report-" + userId + "-" + date + ".xlsx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelData);
    }
}
