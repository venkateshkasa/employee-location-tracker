package com.employeetracker.service;

import com.employeetracker.dto.ActivityDto;
import com.employeetracker.dto.LocationResponse;
import com.employeetracker.dto.ReportDto;
import com.employeetracker.dto.StopDto;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class SimplePdfService {

    public byte[] buildReportPdf(ReportDto report) {
        List<String> lines = new ArrayList<>();
        lines.add("Employee Location Report");
        lines.add("Employee: " + report.getEmployeeName() + " (" + report.getEmployeeId() + ")");
        lines.add("Range: " + report.getFromDate() + " to " + report.getToDate());
        lines.add("Distance: " + String.format("%.2f km", report.getTotalDistanceKm()));
        lines.add("Working Hours: " + valueOrDash(report.getTotalWorkingHours()));
        lines.add("Idle Time: " + valueOrDash(report.getTotalIdleTime()));
        lines.add("Check In: " + valueOrDash(report.getCheckInTime()));
        lines.add("Check Out: " + valueOrDash(report.getCheckOutTime()));
        lines.add("");
        lines.add("Location History");
        for (LocationResponse loc : report.getLocations()) {
            String address = loc.getAddress() != null && !loc.getAddress().isBlank() ? loc.getAddress() : "Unknown Address";
            lines.add(valueOrDash(loc.getLocationTime())
                    + " | Lat: " + loc.getLatitude()
                    + " | Lng: " + loc.getLongitude()
                    + " | " + address);
        }
        lines.add("");
        lines.add("Activities");
        for (ActivityDto activity : report.getActivities()) {
            lines.add(activity.getActivityTime() + " - " + activity.getActivityType() + " - " + valueOrDash(activity.getDescription()));
        }
        lines.add("");
        lines.add("Stop History");
        int stopNumber = 1;
        for (StopDto stop : report.getStops()) {
            lines.add("Stop #" + stopNumber++ + ": " + valueOrDash(stop.getAddress()));
            lines.add("Coordinates: " + stop.getLatitude() + ", " + stop.getLongitude());
            lines.add("Start: " + valueOrDash(stop.getStartTime())
                    + " | End: " + valueOrDash(stop.getEndTime())
                    + " | Duration: " + valueOrDash(stop.getDuration()));
            lines.add("Google Maps: " + valueOrDash(stop.getGoogleMapsUrl()));
        }
        return createPdf(lines);
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static final int LINES_PER_PAGE = 45;

    private byte[] createPdf(List<String> lines) {
        List<List<String>> pages = paginate(lines);

        // Object numbering: 1=Catalog, 2=Pages, 3=Font, then for each page a
        // Page object followed by its Contents stream object.
        int fontObj = 3;
        int firstPageObj = 4;
        int pageCount = pages.size();
        int totalObjects = 3 + (pageCount * 2);

        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < pageCount; i++) {
            if (i > 0) kids.append(' ');
            kids.append(firstPageObj + (i * 2)).append(" 0 R");
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        write(out, "%PDF-1.4\n");

        offsets.add(out.size());
        write(out, "1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n");

        offsets.add(out.size());
        write(out, "2 0 obj << /Type /Pages /Kids [" + kids + "] /Count " + pageCount + " >> endobj\n");

        offsets.add(out.size());
        write(out, fontObj + " 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n");

        for (int i = 0; i < pageCount; i++) {
            int pageObj = firstPageObj + (i * 2);
            int contentObj = pageObj + 1;

            offsets.add(out.size());
            write(out, pageObj + " 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                    + "/Resources << /Font << /F1 " + fontObj + " 0 R >> >> /Contents " + contentObj + " 0 R >> endobj\n");

            String content = buildContent(pages.get(i));
            offsets.add(out.size());
            write(out, contentObj + " 0 obj << /Length " + content.getBytes(StandardCharsets.US_ASCII).length + " >> stream\n");
            write(out, content);
            write(out, "\nendstream endobj\n");
        }

        int xref = out.size();
        write(out, "xref\n0 " + (totalObjects + 1) + "\n0000000000 65535 f \n");
        for (Integer offset : offsets) {
            write(out, String.format("%010d 00000 n \n", offset));
        }
        write(out, "trailer << /Size " + (totalObjects + 1) + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF");
        return out.toByteArray();
    }

    /**
     * Splits report lines into fixed-size pages instead of silently dropping
     * every line past the first page, so multi-day / all-employee reports with
     * many activities and stops render in full across as many pages as needed.
     */
    private List<List<String>> paginate(List<String> lines) {
        List<List<String>> pages = new ArrayList<>();
        for (int i = 0; i < lines.size(); i += LINES_PER_PAGE) {
            pages.add(lines.subList(i, Math.min(i + LINES_PER_PAGE, lines.size())));
        }
        if (pages.isEmpty()) {
            pages.add(new ArrayList<>());
        }
        return pages;
    }

    private String buildContent(List<String> lines) {
        StringBuilder content = new StringBuilder("BT\n/F1 11 Tf\n50 750 Td\n");
        for (String line : lines) {
            content.append("(").append(escape(line)).append(") Tj\n0 -16 Td\n");
        }
        content.append("ET");
        return content.toString();
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private void write(ByteArrayOutputStream out, String value) {
        out.writeBytes(value.getBytes(StandardCharsets.US_ASCII));
    }
}