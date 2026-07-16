package com.employeetracker.service;

import com.employeetracker.dto.ActivityDto;
import com.employeetracker.dto.LocationResponse;
import com.employeetracker.dto.ReportDto;
import com.employeetracker.dto.StopDto;
import com.employeetracker.entity.EmployeeActivity;
import com.employeetracker.entity.EmployeeLocation;
import com.employeetracker.entity.EmployeeStop;
import com.employeetracker.entity.User;
import com.employeetracker.entity.UserRole;
import com.employeetracker.exception.BadRequestException;
import com.employeetracker.exception.ResourceNotFoundException;
import com.employeetracker.repository.EmployeeActivityRepository;
import com.employeetracker.repository.EmployeeLocationRepository;
import com.employeetracker.repository.EmployeeStopRepository;
import com.employeetracker.repository.UserRepository;
import com.employeetracker.util.DateTimeUtil;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReportService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserRepository userRepository;
    private final EmployeeLocationRepository locationRepository;
    private final EmployeeStopRepository stopRepository;
    private final EmployeeActivityRepository activityRepository;
    private final DistanceCalculationService distanceCalculationService;
    private final LocationService locationService;
    private final SimplePdfService simplePdfService;

    public ReportService(UserRepository userRepository,
                         EmployeeLocationRepository locationRepository,
                         EmployeeStopRepository stopRepository,
                         EmployeeActivityRepository activityRepository,
                         DistanceCalculationService distanceCalculationService,
                         LocationService locationService,
                         SimplePdfService simplePdfService) {
        this.userRepository = userRepository;
        this.locationRepository = locationRepository;
        this.stopRepository = stopRepository;
        this.activityRepository = activityRepository;
        this.distanceCalculationService = distanceCalculationService;
        this.locationService = locationService;
        this.simplePdfService = simplePdfService;
    }

    public ReportDto generateReport(Long userId, LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null) {
            fromDate = LocalDate.now();
        }
        if (toDate == null) {
            toDate = LocalDate.now();
        }
        if (fromDate.isAfter(toDate)) {
            throw new BadRequestException("From Date must not be after To Date");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getRole() != UserRole.EMPLOYEE) {
            throw new BadRequestException("Reports are generated for employees only");
        }

        LocalDateTime start = DateTimeUtil.startOfDay(fromDate);
        LocalDateTime end = DateTimeUtil.endOfDay(toDate);

        List<EmployeeLocation> locations = locationRepository.findTodayLocations(userId, start, end);
        List<EmployeeActivity> activities = activityRepository.findTodayActivities(userId, start, end);

        ReportDto report = new ReportDto();
        report.setFromDate(fromDate.format(DATE_FORMATTER));
        report.setToDate(toDate.format(DATE_FORMATTER));
        report.setUserId(user.getUserId());
        report.setEmployeeName(user.getName());
        report.setEmployeeId(user.getEmployeeId());
        report.setTotalDistanceKm(distanceCalculationService.calculateDistanceKm(userId, fromDate, toDate));
        report.setTotalLocationUpdates(locations.size());

        report.setLocations(locations.stream()
                .map(loc -> {
                    LocationResponse lr = new LocationResponse();
                    lr.setLocationId(loc.getLocationId());
                    lr.setUserId(loc.getUserId());
                    lr.setEmployeeName(user.getName());
                    lr.setLatitude(loc.getLatitude());
                    lr.setLongitude(loc.getLongitude());
                    lr.setAccuracy(loc.getAccuracy());
                    lr.setLocationTime(loc.getLocationTime().format(DATETIME_FORMATTER));
                    String address = loc.getAddress();
                    lr.setAddress((address != null && !address.isBlank()) ? address : "Unknown Address");
                    return lr;
                })
                .collect(Collectors.toList()));

        // Use the requested report date range, not "today" - the previous implementation
        // cross-referenced against getTodayStops(), so any historical (non-today)
        // report silently ended up showing today's stops instead of its own range's.
        report.setStops(locationService.getStopsForRange(userId, fromDate, toDate));
        report.setTotalStops(report.getStops().size());

        report.setActivities(activities.stream().map(a -> {
            ActivityDto dto = new ActivityDto();
            dto.setActivityId(a.getActivityId());
            dto.setActivityType(a.getActivityType().name());
            dto.setDescription(a.getDescription());
            if (a.getLatitude() != null) {
                dto.setLatitude(a.getLatitude().doubleValue());
            }
            if (a.getLongitude() != null) {
                dto.setLongitude(a.getLongitude().doubleValue());
            }
            dto.setActivityTime(a.getActivityTime().format(DATETIME_FORMATTER));
            return dto;
        }).collect(Collectors.toList()));

        return report;
    }

    public List<ReportDto> generateAllEmployeeReports(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null) {
            fromDate = LocalDate.now();
        }
        if (toDate == null) {
            toDate = LocalDate.now();
        }

        List<User> employees = userRepository.findByRole(UserRole.EMPLOYEE);
        List<ReportDto> reports = new ArrayList<>();

        for (User employee : employees) {
            reports.add(generateReport(employee.getUserId(), fromDate, toDate));
        }

        return reports;
    }

    public byte[] exportReportToExcel(Long userId, LocalDate fromDate, LocalDate toDate) throws IOException {
        ReportDto report = generateReport(userId, fromDate, toDate);

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle headerStyle = createHeaderStyle(workbook);

            Sheet summarySheet = workbook.createSheet("Summary");
            createSummarySheet(summarySheet, report, headerStyle);

            Sheet locationsSheet = workbook.createSheet("Locations");
            createLocationsSheet(locationsSheet, report, headerStyle);

            Sheet stopsSheet = workbook.createSheet("Stops");
            createStopsSheet(stopsSheet, report, headerStyle);

            Sheet activitiesSheet = workbook.createSheet("Activities");
            createActivitiesSheet(activitiesSheet, report, headerStyle);

            workbook.write(out);
            return out.toByteArray();
        }
    }

    public byte[] exportReportToPdf(Long userId, LocalDate fromDate, LocalDate toDate) {
        return simplePdfService.buildReportPdf(generateReport(userId, fromDate, toDate));
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private void createSummarySheet(Sheet sheet, ReportDto report, CellStyle headerStyle) {
        String[][] data = {
                {"From Date", report.getFromDate()},
                {"To Date", report.getToDate()},
                {"Employee ID", report.getEmployeeId()},
                {"Employee Name", report.getEmployeeName()},
                {"Total Distance (km)", String.valueOf(report.getTotalDistanceKm())},
                {"Total Stops", String.valueOf(report.getTotalStops())},
                {"Total Location Updates", String.valueOf(report.getTotalLocationUpdates())}
        };

        for (int i = 0; i < data.length; i++) {
            Row row = sheet.createRow(i);
            Cell labelCell = row.createCell(0);
            labelCell.setCellValue(data[i][0]);
            labelCell.setCellStyle(headerStyle);
            row.createCell(1).setCellValue(data[i][1]);
        }

        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private void createLocationsSheet(Sheet sheet, ReportDto report, CellStyle headerStyle) {
        Row header = sheet.createRow(0);
        String[] columns = {"Location ID", "Latitude", "Longitude", "Accuracy", "Address", "Time"};
        for (int i = 0; i < columns.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowNum = 1;
        for (LocationResponse loc : report.getLocations()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(loc.getLocationId());
            row.createCell(1).setCellValue(loc.getLatitude().doubleValue());
            row.createCell(2).setCellValue(loc.getLongitude().doubleValue());
            row.createCell(3).setCellValue(loc.getAccuracy() != null ? loc.getAccuracy().doubleValue() : 0);
            row.createCell(4).setCellValue(loc.getAddress() != null && !loc.getAddress().isBlank() ? loc.getAddress() : "Unknown Address");
            row.createCell(5).setCellValue(loc.getLocationTime());
        }

        for (int i = 0; i < columns.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createStopsSheet(Sheet sheet, ReportDto report, CellStyle headerStyle) {
        Row header = sheet.createRow(0);
        String[] columns = {"Stop ID", "Latitude", "Longitude", "Address", "Start Time", "End Time", "Duration", "Google Maps Link"};
        for (int i = 0; i < columns.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowNum = 1;
        for (StopDto stop : report.getStops()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(stop.getStopId());
            row.createCell(1).setCellValue(stop.getLatitude());
            row.createCell(2).setCellValue(stop.getLongitude());
            row.createCell(3).setCellValue(stop.getAddress() != null ? stop.getAddress() : "");
            row.createCell(4).setCellValue(stop.getStartTime());
            row.createCell(5).setCellValue(stop.getEndTime() != null ? stop.getEndTime() : "Ongoing");
            row.createCell(6).setCellValue(stop.getDuration() != null ? stop.getDuration() : "");
            row.createCell(7).setCellValue(stop.getGoogleMapsUrl() != null ? stop.getGoogleMapsUrl() : "");
        }

        for (int i = 0; i < columns.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createActivitiesSheet(Sheet sheet, ReportDto report, CellStyle headerStyle) {
        Row header = sheet.createRow(0);
        String[] columns = {"Activity ID", "Type", "Description", "Latitude", "Longitude", "Time"};
        for (int i = 0; i < columns.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowNum = 1;
        for (ActivityDto activity : report.getActivities()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(activity.getActivityId());
            row.createCell(1).setCellValue(activity.getActivityType());
            row.createCell(2).setCellValue(activity.getDescription());
            row.createCell(3).setCellValue(activity.getLatitude() != null ? activity.getLatitude() : 0);
            row.createCell(4).setCellValue(activity.getLongitude() != null ? activity.getLongitude() : 0);
            row.createCell(5).setCellValue(activity.getActivityTime());
        }

        for (int i = 0; i < columns.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }
}