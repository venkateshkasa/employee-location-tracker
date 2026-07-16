package com.employeetracker.service;

import com.employeetracker.entity.ActivityType;
import com.employeetracker.entity.User;
import com.employeetracker.repository.UserRepository;
import com.employeetracker.util.HaversineUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class OfficeGeofenceService {

    private final UserRepository userRepository;
    private final ActivityService activityService;
    private final List<OfficeDefinition> offices;

    public OfficeGeofenceService(UserRepository userRepository,
                                 ActivityService activityService,
                                 @Value("${tracking.offices:Head Office:28.613939,77.209021,250}") String officesConfig) {
        this.userRepository = userRepository;
        this.activityService = activityService;
        this.offices = parseOffices(officesConfig);
    }

    @Transactional
    public OfficeResult evaluateAndStore(User user, BigDecimal latitude, BigDecimal longitude, Long referenceId) {
        Optional<OfficeDefinition> match = findMatchingOffice(latitude, longitude);
        boolean nowInside = match.isPresent();
        boolean wasInside = user.isInsideOffice();
        String officeName = match.map(OfficeDefinition::name).orElse(null);

        if (nowInside != wasInside || (nowInside && !officeName.equals(user.getCurrentOfficeName()))) {
            user.setInsideOffice(nowInside);
            user.setCurrentOfficeName(officeName);
            userRepository.save(user);

            activityService.logActivity(
                    user.getUserId(),
                    nowInside ? ActivityType.ENTERED_OFFICE : ActivityType.EXITED_OFFICE,
                    nowInside ? "Entered Office: " + officeName : "Exited Office",
                    latitude,
                    longitude,
                    referenceId
            );
        }

        return new OfficeResult(nowInside, officeName);
    }

    public Optional<OfficeDefinition> findMatchingOffice(BigDecimal latitude, BigDecimal longitude) {
        return offices.stream()
                .filter(office -> HaversineUtil.calculateDistanceMeters(
                        latitude,
                        longitude,
                        BigDecimal.valueOf(office.latitude()),
                        BigDecimal.valueOf(office.longitude())) <= office.radiusMeters())
                .findFirst();
    }

    private List<OfficeDefinition> parseOffices(String config) {
        List<OfficeDefinition> parsed = new ArrayList<>();
        if (config == null || config.isBlank()) {
            return parsed;
        }
        for (String officeConfig : config.split(";")) {
            String[] parts = officeConfig.split(":");
            if (parts.length != 2) {
                continue;
            }
            String[] values = parts[1].split(",");
            if (values.length != 3) {
                continue;
            }
            try {
                parsed.add(new OfficeDefinition(
                        parts[0].trim(),
                        Double.parseDouble(values[0].trim()),
                        Double.parseDouble(values[1].trim()),
                        Double.parseDouble(values[2].trim())
                ));
            } catch (NumberFormatException ignored) {
                // Ignore malformed office config entries.
            }
        }
        return parsed;
    }

    public record OfficeDefinition(String name, double latitude, double longitude, double radiusMeters) {
    }

    public record OfficeResult(boolean insideOffice, String officeName) {
    }
}
