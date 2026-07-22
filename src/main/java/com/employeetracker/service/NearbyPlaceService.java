package com.employeetracker.service;

import com.employeetracker.config.NearbySearchProperties;
import com.employeetracker.dto.NearbyPlaceDto;
import com.employeetracker.entity.NearbyPlace;
import com.employeetracker.repository.NearbyPlaceRepository;
import com.employeetracker.util.HaversineUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service for managing nearby educational institutions detection and tracking.
 * Implements caching to avoid repeated API calls and handles place entry/exit detection.
 */
@Service 
public class NearbyPlaceService {

    private static final Logger logger = LoggerFactory.getLogger(NearbyPlaceService.class);

    private final NearbyPlaceRepository nearbyPlaceRepository;
    private final PlaceSearchService placeSearchService;
    private final NearbySearchProperties properties;
private final NotificationService notificationService;
    /**
     * In-memory cache for the last search location/results, keyed by userId.
     * IMPORTANT: this used to be a single set of instance fields shared by every
     * employee, which meant one employee's cached search results could be reused
     * for a completely different employee (as long as their coordinates happened
     * to fall within the cache-distance window of whoever searched last). That
     * caused wrong/missing "nearby college" detections in a multi-employee
     * deployment. Keying by userId isolates each employee's cache correctly.
     */
    private final Map<Long, LocationCache> caches = new ConcurrentHashMap<>();

    private static final class LocationCache {
        private BigDecimal latitude;
        private BigDecimal longitude;
        private LocalDateTime searchTime;
        private List<PlaceSearchService.NearbyPlaceResult> results;
        // Radius (meters) this cached search was performed with - see
        // processLocationUpdate(userId, lat, lng, radiusMeters).
        private int radiusMeters;
    }

   public NearbyPlaceService(
    NearbyPlaceRepository nearbyPlaceRepository,
    PlaceSearchService placeSearchService,
    NearbySearchProperties properties,
    NotificationService notificationService) {

    this.nearbyPlaceRepository = nearbyPlaceRepository;
    this.placeSearchService = placeSearchService;
    this.properties = properties;
    this.notificationService = notificationService;
}

    /**
     * Process location update and detect nearby educational institutions.
     * Implements caching to avoid repeated API calls for small movements.
     * 
     * @param userId Employee user ID
     * @param latitude Current latitude
     * @param longitude Current longitude
     * @return List of newly detected nearby places
     */
    @Transactional
    public List<NearbyPlaceDto> processLocationUpdate(Long userId, BigDecimal latitude, BigDecimal longitude) {
        return processLocationUpdate(userId, latitude, longitude, null);
    }

    /**
     * Same background nearby-college detection as above, but scoped to an
     * explicit radius (in meters) - normally the radius currently selected
     * on the employee's Radius dropdown (1/3/5/10 KM), forwarded from
     * LocationRequest#getRadiusMeters(). This is what keeps the
     * "notify only within the selected radius" behavior in sync with the
     * radius used for the map/circle/nearby search, instead of always
     * detecting against the server's hardcoded default radius regardless
     * of what the employee selected.
     * <p>
     * Results are also restricted to Colleges/Universities, matching the
     * "Show Nearby Colleges" feature (and its notification copy, which
     * always refers to "University") - schools are intentionally excluded
     * here, the same as the live radius-aware search used for the map.
     *
     * @param radiusMeters explicit radius to use, or {@code null} to fall
     *                      back to the configured default radius
     */
    @Transactional
    public List<NearbyPlaceDto> processLocationUpdate(Long userId, BigDecimal latitude, BigDecimal longitude,
                                                       Integer radiusMeters) {
        if (!properties.isEnabled()) {
            logger.debug("Nearby place search is disabled");
            return List.of();
        }

        int effectiveRadius = (radiusMeters != null && radiusMeters > 0) ? radiusMeters : properties.getRadius();

        // Check if we can use cached results (per-user). The cache is only
        // reused when the previously cached search also used the same
        // radius - otherwise a stale search made at a smaller/larger radius
        // could wrongly answer for a different radius selection.
        LocationCache cache = caches.get(userId);
        if (shouldUseCachedResults(cache, latitude, longitude) && cache.radiusMeters == effectiveRadius) {
            logger.debug("Using cached nearby place results for user {}", userId);
            return processCachedResults(userId, cache, latitude, longitude, effectiveRadius);
        }

        // Perform new search, scoped to the effective radius.
        logger.info("Searching for nearby places for user {} at {}, {} within {}m", userId, latitude, longitude, effectiveRadius);
        List<PlaceSearchService.NearbyPlaceResult> searchResults =
            placeSearchService.searchNearbyPlaces(latitude, longitude, effectiveRadius).stream()
                .filter(result -> isCollegeOrUniversity(result.getPlaceType()))
                .filter(result -> result.getDistance() != null && result.getDistance().doubleValue() <= effectiveRadius)
                .collect(Collectors.toList());

        // Update cache for this user only
        updateCache(userId, latitude, longitude, searchResults, effectiveRadius);

        // Process results
        return processSearchResults(userId, searchResults);
    }

    /**
     * Determine if cached results can be reused based on distance moved
     */
    private boolean shouldUseCachedResults(LocationCache cache, BigDecimal latitude, BigDecimal longitude) {
        if (cache == null || cache.results == null || cache.latitude == null || cache.longitude == null) {
            return false;
        }

        double distanceMoved = HaversineUtil.calculateDistanceMeters(
                cache.latitude, cache.longitude, latitude, longitude);

        return distanceMoved < properties.getCacheDistance();
    }

    /**
     * Update the in-memory cache for a specific user
     */
    private void updateCache(Long userId, BigDecimal latitude, BigDecimal longitude,
                             List<PlaceSearchService.NearbyPlaceResult> results) {
        updateCache(userId, latitude, longitude, results, properties.getRadius());
    }

    private void updateCache(Long userId, BigDecimal latitude, BigDecimal longitude,
                             List<PlaceSearchService.NearbyPlaceResult> results, int radiusMeters) {
        LocationCache cache = new LocationCache();
        cache.latitude = latitude;
        cache.longitude = longitude;
        cache.searchTime = LocalDateTime.now();
        cache.results = new ArrayList<>(results);
        cache.radiusMeters = radiusMeters;
        caches.put(userId, cache);
    }

    /**
     * Process cached results with current location
     */
    private List<NearbyPlaceDto> processCachedResults(Long userId, LocationCache cache,
                                                        BigDecimal latitude, BigDecimal longitude) {
        return processCachedResults(userId, cache, latitude, longitude, cache.radiusMeters);
    }

    private List<NearbyPlaceDto> processCachedResults(Long userId, LocationCache cache,
                                                        BigDecimal latitude, BigDecimal longitude,
                                                        int radiusMeters) {
        // Recalculate distances based on current location
        List<PlaceSearchService.NearbyPlaceResult> updatedResults = new ArrayList<>();
        for (PlaceSearchService.NearbyPlaceResult result : cache.results) {
double newDistance = HaversineUtil.calculateDistanceMeters(
                    latitude, longitude, result.getLatitude(), result.getLongitude());
            
            PlaceSearchService.NearbyPlaceResult updated = new PlaceSearchService.NearbyPlaceResult();
            updated.setName(result.getName());
            updated.setPlaceType(result.getPlaceType());
            updated.setLatitude(result.getLatitude());
            updated.setLongitude(result.getLongitude());
            updated.setDistance(BigDecimal.valueOf(newDistance));
            updated.setAddress(result.getAddress());
            
            updatedResults.add(updated);
        }

        // Re-apply the radius cutoff, since the employee may have moved
        // within the cache-distance window and a place that was within
        // range at the cached location could now have drifted just outside
        // the selected radius (or vice versa).
        List<PlaceSearchService.NearbyPlaceResult> withinRadius = updatedResults.stream()
                .filter(result -> result.getDistance() != null && result.getDistance().doubleValue() <= radiusMeters)
                .collect(Collectors.toList());

        return processSearchResults(userId, withinRadius);
    }

    /**
     * Process search results and detect new nearby places
     */
   private List<NearbyPlaceDto> processSearchResults(
        Long userId,
        List<PlaceSearchService.NearbyPlaceResult> results) {

    List<NearbyPlaceDto> newPlaces = new ArrayList<>();

    // Get currently active places for this user
    List<NearbyPlace> activePlaces =
            nearbyPlaceRepository.findByUserIdAndLeftTimeIsNullOrderByEnteredTimeDesc(userId);

    // Mark places that are no longer nearby as left
    for (NearbyPlace activePlace : activePlaces) {

        boolean stillNearby = results.stream()
                .anyMatch(r -> isSamePlace(r, activePlace));

        if (!stillNearby) {
            activePlace.setLeftTime(LocalDateTime.now());
            nearbyPlaceRepository.save(activePlace);

            logger.info("User {} left nearby place: {}",
                    userId,
                    activePlace.getPlaceName());
        }
    }

    // Detect new nearby places
    for (PlaceSearchService.NearbyPlaceResult result : results) {

        Optional<NearbyPlace> existingPlace =
                nearbyPlaceRepository.findActivePlaceByName(userId, result.getName());

        if (existingPlace.isEmpty()) {

            // New place detected
            NearbyPlace newPlace = new NearbyPlace();
            newPlace.setUserId(userId);
            newPlace.setPlaceName(result.getName());
            newPlace.setPlaceType(result.getPlaceType());
            newPlace.setLatitude(result.getLatitude());
            newPlace.setLongitude(result.getLongitude());
            newPlace.setDistance(result.getDistance());
            newPlace.setAddress(result.getAddress());
            newPlace.setEnteredTime(LocalDateTime.now());
            newPlace.setNotified(false);

            NearbyPlace saved = nearbyPlaceRepository.save(newPlace);

            // Add to response
            newPlaces.add(mapToDto(saved));

            // ==============================
            // CREATE EMPLOYEE NOTIFICATION
            // ==============================
            notificationService.createNearbyCollegeNotification(
        userId,
        result.getName(),
        result.getDistance().doubleValue(),
        result.getLatitude().doubleValue(),
result.getLongitude().doubleValue()
);

            // ==============================
            // CREATE ADMIN NOTIFICATION
            // ==============================
            notificationService.createAdminNearbyCollegeNotification(
                    "Employee " + userId,
                    result.getName(),
                    result.getDistance().doubleValue()
            );

            // Mark this place as notified
            saved.setNotified(true);
            nearbyPlaceRepository.save(saved);

            logger.info(
                    "New nearby place detected for user {}: {} ({})",
                    userId,
                    result.getName(),
                    result.getPlaceType());

        } else {

            // Update distance for existing place
            NearbyPlace place = existingPlace.get();
            place.setDistance(result.getDistance());

            nearbyPlaceRepository.save(place);
        }
    }

    return newPlaces;
}
    /**
     * Check if a search result matches an existing nearby place
     */
    private boolean isSamePlace(PlaceSearchService.NearbyPlaceResult result, NearbyPlace place) {
        // Compare by name (primary) and location proximity (secondary)
        if (result.getName().equals(place.getPlaceName())) {
            return true;
        }

        // Check if locations are very close (within 50 meters)
double distance = HaversineUtil.calculateDistanceMeters(
                result.getLatitude(), result.getLongitude(),
            place.getLatitude(), place.getLongitude());

        return distance < 50;
    }

    /**
     * Get active nearby places for a user
     */
    public List<NearbyPlaceDto> getActiveNearbyPlaces(Long userId) {
        return nearbyPlaceRepository.findByUserIdAndLeftTimeIsNullOrderByEnteredTimeDesc(userId)
            .stream()
            .map(this::mapToDto)
            .collect(Collectors.toList());
    }

    /**
     * Get all active nearby places (for admin dashboard)
     */
    public List<NearbyPlaceDto> getAllActiveNearbyPlaces() {
        return nearbyPlaceRepository.findByLeftTimeIsNullOrderByEnteredTimeDesc()
            .stream()
            .map(this::mapToDto)
            .collect(Collectors.toList());
    }

    /**
     * Get nearby places history for a user within a time range
     */
    public List<NearbyPlaceDto> getNearbyPlacesHistory(Long userId, LocalDateTime start, LocalDateTime end) {
        return nearbyPlaceRepository.findByUserIdAndEnteredTimeBetweenOrderByEnteredTimeDesc(userId, start, end)
            .stream()
            .map(this::mapToDto)
            .collect(Collectors.toList());
    }

    /**
     * Mark a place as notified
     */
    @Transactional
    public void markAsNotified(Long placeId) {
        nearbyPlaceRepository.findById(placeId).ifPresent(place -> {
            place.setNotified(true);
            nearbyPlaceRepository.save(place);
        });
    }

    /**
     * Clean up old records (can be called periodically)
     */
    @Transactional
    public void cleanupOldRecords(int daysToKeep) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(daysToKeep);
        nearbyPlaceRepository.deleteByEnteredTimeBefore(cutoff);
        logger.info("Cleaned up nearby places records older than {} days", daysToKeep);
    }
    /**
     * Live (non-persisted) search for nearby places using the service's
     * default radius. Kept for backward compatibility; prefer the
     * radius-aware overload below, which is what the Radius dropdown uses.
     */
    @Transactional(readOnly = true)
    public List<NearbyPlaceDto> searchNearbyPlaces(Long userId,
                                                   BigDecimal latitude,
                                                   BigDecimal longitude) {
        return searchNearbyPlaces(userId, latitude, longitude, properties.getRadius());
    }

    /**
     * Live (non-persisted) search for nearby colleges/universities within an
     * explicit radius. Used by the "Show Nearby Colleges" feature so the
     * Radius dropdown (1/3/5/10 KM) is respected on every request, for both
     * the employee dashboard and the admin dashboard (which passes the
     * viewed employee's userId/location).
     * <p>
     * Only "College" and "University" place types are returned (schools are
     * excluded per this feature's requirement), and each result only from
     * within the requested radius. Each DTO is given a stable, non-null
     * placeId derived from the place's identity so the frontend can key
     * markers without collisions or duplicates across repeated calls.
     */
    @Transactional(readOnly = true)
    public List<NearbyPlaceDto> searchNearbyPlaces(Long userId,
                                                   BigDecimal latitude,
                                                   BigDecimal longitude,
                                                   int radiusMeters) {

        List<PlaceSearchService.NearbyPlaceResult> results =
                placeSearchService.searchNearbyPlaces(latitude, longitude, radiusMeters);

        return results.stream()
                .filter(result -> isCollegeOrUniversity(result.getPlaceType()))
                .filter(result -> result.getDistance() != null
                        && result.getDistance().doubleValue() <= radiusMeters)
                .map(result -> {

                    NearbyPlaceDto dto = new NearbyPlaceDto();

                    dto.setPlaceId(stablePlaceId(result));
                    dto.setUserId(userId);
                    dto.setPlaceName(result.getName());
                    dto.setPlaceType(result.getPlaceType());
                    dto.setLatitude(result.getLatitude());
                    dto.setLongitude(result.getLongitude());
                    dto.setDistance(result.getDistance());
                    dto.setAddress(result.getAddress());

                    return dto;

                }).collect(Collectors.toList());
    }

    /**
     * Restricts "nearby colleges" results to colleges/universities, as
     * required by the Radius dropdown feature (schools are excluded here).
     * Used by both the live radius-aware search and the background
     * detection/notification flow in {@link #processLocationUpdate}, so
     * map, notifications, and admin notifications always agree on what
     * counts as "nearby".
     */
    private boolean isCollegeOrUniversity(String placeType) {
        if (placeType == null) {
            return false;
        }
        return placeType.equalsIgnoreCase("College") || placeType.equalsIgnoreCase("University");
    }

    /**
     * Derives a stable, non-null placeId for a live (non-persisted) search
     * result so the frontend can use it as a unique marker key. Without
     * this, every live result would carry a null placeId and the frontend's
     * "one marker per placeId" map would collapse distinct colleges into a
     * single marker.
     */
    private Long stablePlaceId(PlaceSearchService.NearbyPlaceResult result) {
        String key = result.getName() + "|" + result.getLatitude() + "|" + result.getLongitude();
        return (long) key.hashCode();
    }

    /**
     * Map entity to DTO
     */
    private NearbyPlaceDto mapToDto(NearbyPlace place) {
        NearbyPlaceDto dto = new NearbyPlaceDto();
        dto.setPlaceId(place.getPlaceId());
        dto.setUserId(place.getUserId());
        dto.setPlaceName(place.getPlaceName());
        dto.setPlaceType(place.getPlaceType());
        dto.setLatitude(place.getLatitude());
        dto.setLongitude(place.getLongitude());
        dto.setDistance(place.getDistance());
        dto.setAddress(place.getAddress());
        dto.setEnteredTime(place.getEnteredTime());
        dto.setLeftTime(place.getLeftTime());
        dto.setNotified(place.isNotified());
        return dto;
    }
}