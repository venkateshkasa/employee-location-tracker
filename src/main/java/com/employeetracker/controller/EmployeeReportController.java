package com.employeetracker.controller;

import com.employeetracker.dto.ApiResponse;
import com.employeetracker.dto.ReportDto;
import com.employeetracker.entity.User;
import com.employeetracker.service.AuthService;
import com.employeetracker.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;

/**
 * Exposes "My Reports" for the logged-in employee.
 *
 * Unlike AdminController, this controller never accepts a userId from the
 * client. The employee is always resolved server-side via
 * authService.getCurrentUserEntity(), so an employee can never view or
 * export another employee's report. All report-building logic is reused
 * from ReportService (the same service AdminController uses) to avoid
 * duplicating business logic.
 */
@RestController
@RequestMapping("/api/employee")
public class EmployeeReportController {

    private final ReportService reportService;
    private final AuthService authService;

    public EmployeeReportController(ReportService reportService, AuthService authService) {
        this.reportService = reportService;
        this.authService = authService;
    }

    @GetMapping("/report")
    public ResponseEntity<ApiResponse<ReportDto>> getMyReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {

        User user = authService.getCurrentUserEntity();

        if (fromDate == null) {
            fromDate = LocalDate.now();
        }
        if (toDate == null) {
            toDate = LocalDate.now();
        }

        ReportDto report = reportService.generateReport(user.getUserId(), fromDate, toDate);
        return ResponseEntity.ok(ApiResponse.success(report));
    }

    @GetMapping("/report/export")
    public ResponseEntity<byte[]> exportMyReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) throws IOException {

        User user = authService.getCurrentUserEntity();

        if (fromDate == null) {
            fromDate = LocalDate.now();
        }
        if (toDate == null) {
            toDate = LocalDate.now();
        }

        byte[] excelData = reportService.exportReportToExcel(user.getUserId(), fromDate, toDate);
        String filename = "my-report-" + user.getEmployeeId() + "-" + fromDate + "-to-" + toDate + ".xlsx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelData);
    }

    @GetMapping("/report/export/pdf")
    public ResponseEntity<byte[]> exportMyReportPdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {

        User user = authService.getCurrentUserEntity();

        if (fromDate == null) {
            fromDate = LocalDate.now();
        }
        if (toDate == null) {
            toDate = LocalDate.now();
        }

        byte[] pdfData = reportService.exportReportToPdf(user.getUserId(), fromDate, toDate);
        String filename = "my-report-" + user.getEmployeeId() + "-" + fromDate + "-to-" + toDate + ".pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfData);
    }
}
