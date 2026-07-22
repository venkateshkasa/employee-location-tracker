package com.employeetracker.entity;

/**
 * Reason selected by an employee when manually turning tracking OFF via the
 * "Stop Reason" popup. This is independent from the automatic GPS-based
 * stop-detection handled by {@link com.employeetracker.service.StopDetectionService}
 * / {@link EmployeeStop} - that feature keeps working exactly as before.
 */
public enum StopReason {
    LUNCH_BREAK("Lunch Break"),
    TEA_BREAK("Tea Break"),
    CLIENT_MEETING("Client Meeting"),
    VEHICLE_ISSUE("Vehicle Issue"),
    PERSONAL_WORK("Personal Work"),
    OTHER("Other");

    private final String label;

    StopReason(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
