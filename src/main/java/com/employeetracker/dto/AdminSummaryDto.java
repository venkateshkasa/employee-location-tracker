package com.employeetracker.dto;

public class AdminSummaryDto {

    private long totalEmployees;
    private long onlineEmployees;
    private long offlineEmployees;

    public long getTotalEmployees() {
        return totalEmployees;
    }

    public void setTotalEmployees(long totalEmployees) {
        this.totalEmployees = totalEmployees;
    }

    public long getOnlineEmployees() {
        return onlineEmployees;
    }

    public void setOnlineEmployees(long onlineEmployees) {
        this.onlineEmployees = onlineEmployees;
    }

    public long getOfflineEmployees() {
        return offlineEmployees;
    }

    public void setOfflineEmployees(long offlineEmployees) {
        this.offlineEmployees = offlineEmployees;
    }
}
