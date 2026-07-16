package com.employeetracker.service;

import com.employeetracker.config.NearbySearchProperties;
import com.employeetracker.util.HaversineUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Searches the Geoapify Places API for nearby educational institutions
 * (colleges and universities) around a given coordinate.
 * <p>
 * The Geoapify API key is read from the {@code geoapify.api.key} property
 * (see {@link #geoapifyApiKey}) and is never hardcoded. The search radius
 * and HTTP timeouts are driven by {@link NearbySearchProperties} /
 * {@code nearby.search.*} properties so they stay in sync with the rest of
 * the "nearby place" feature.
 * <p>
 * <b>Call throttling:</b> the Geoapify API is rate-limited, so this service
 * does NOT call it on every location update. It only queries Geoapify when
 * the "Show Nearby Colleges" feature is enabled AND either the radius
 * changed or the employee moved far enough from the last searched location
 * (see {@link #minMoveDistanceMeters}). Otherwise the last cached result set
 * for that location is returned.
 * <p>
 * <b>Resilience:</b> this service fails soft: if Geoapify is unreachable,
 * times out, or returns something unexpected, an empty list is returned and
 * the failure is logged, rather than propagating an exception up to the
 * caller.
 */
@Service
public class PlaceSearchService {

    private static final Logger logger = LoggerFactory.getLogger(PlaceSearchService.class);

    /**
     * Geoapify Places API base endpoint.
     */
    private static final String GEOAPIFY_PLACES_URL = "https://api.geoapify.com/v2/places";

    /**
     * Geoapify categories that represent educational institutions we want to
     * surface to employees (colleges and universities only).
     */
    private static final String CATEGORY_FILTER = "education.college,education.university";

    /**
     * Maximum number of places requested per Geoapify query.
     */
    private static final int RESULT_LIMIT = 50;

    private final NearbySearchProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate;

    /**
     * Geoapify API key, injected from the {@code geoapify.api.key} property.
     * Never hardcoded in source.
     */
    @Value("${geoapify.api.key}")
    private String geoapifyApiKey;

    /**
     * Minimum distance (meters) the employee must move from the last
     * searched location before we hit Geoapify again. Clamped to the
     * 200-500m window requested for this feature.
     */
    private final double minMoveDistanceMeters;

    // --- Last-search cache (per-instance; single most recent location) ---
    private final Object cacheLock = new Object();
    private BigDecimal lastSearchLat;
    private BigDecimal lastSearchLon;
    private int lastSearchRadius = -1;
    private List<NearbyPlaceResult> cachedResults = new ArrayList<>();
    private boolean hasCachedResults = false;

    public PlaceSearchService(
            NearbySearchProperties properties,
            @Value("${nearby.search.overpass.connect-timeout-ms:5000}") int connectTimeoutMillis,
            @Value("${nearby.search.overpass.read-timeout-ms:8000}") int readTimeoutMillis,
            @Value("${nearby.search.min-move-distance-meters:300}") double minMoveDistanceMeters) {
        this.properties = properties;
        this.minMoveDistanceMeters = clampMoveDistance(minMoveDistanceMeters);
        this.restTemplate = new RestTemplate(buildRequestFactory(connectTimeoutMillis, readTimeoutMillis));
    }

    private SimpleClientHttpRequestFactory buildRequestFactory(int connectTimeoutMillis, int readTimeoutMillis) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMillis);
        factory.setReadTimeout(readTimeoutMillis);
        return factory;
    }

    /** Keeps the "moved far enough to re-search" threshold within 200-500m. */
    private double clampMoveDistance(double requestedMeters) {
        double min = 200.0;
        double max = 500.0;
        if (requestedMeters < min) {
            return min;
        }
        if (requestedMeters > max) {
            return max;
        }
        return requestedMeters;
    }

    /**
     * Search for nearby educational institutions using the configured radius
     * ({@code nearby.search.radius}, clamped between 1km and 10km).
     * Assumes the "Show Nearby Colleges" feature is enabled.
     *
     * @param latitude  current latitude
     * @param longitude current longitude
     * @return list of nearby places, nearest first; empty list if none found,
     *         the feature is disabled, or the search failed
     */
    public List<NearbyPlaceResult> searchNearbyPlaces(BigDecimal latitude, BigDecimal longitude) {
        return searchNearbyPlaces(latitude, longitude, properties.getRadius(), true);
    }

    /**
     * Search for nearby educational institutions using an explicit radius.
     * Assumes the "Show Nearby Colleges" feature is enabled.
     *
     * @param latitude     current latitude
     * @param longitude    current longitude
     * @param radiusMeters requested radius in meters; clamped to the 1km-10km
     *                     range supported by the Radius dropdown (1/3/5/10 KM)
     * @return list of nearby places, nearest first; empty list if none found
     *         or the search failed
     */
    public List<NearbyPlaceResult> searchNearbyPlaces(BigDecimal latitude, BigDecimal longitude, int radiusMeters) {
        return searchNearbyPlaces(latitude, longitude, radiusMeters, true);
    }

    /**
     * Gated entry point intended to be called on every employee location
     * update. Geoapify is only actually queried when:
     * <ul>
     *   <li>{@code showNearbyCollegesEnabled} is {@code true}, AND</li>
     *   <li>the requested radius differs from the last searched radius, OR</li>
     *   <li>the employee has moved more than {@link #minMoveDistanceMeters}
     *       from the last searched location, OR</li>
     *   <li>there is no cached result yet.</li>
     * </ul>
     * Otherwise the last cached result set is returned so we don't hammer the
     * Geoapify API on every location ping.
     *
     * @param latitude                  current latitude
     * @param longitude                 current longitude
     * @param radiusMeters              requested radius in meters; clamped to 1km-10km
     * @param showNearbyCollegesEnabled whether the "Show Nearby Colleges" toggle is on
     * @return list of nearby places, nearest first; empty list if the feature
     *         is disabled, coordinates are missing, or the search failed
     */
    public List<NearbyPlaceResult> searchNearbyPlaces(BigDecimal latitude, BigDecimal longitude,
                                                       int radiusMeters, boolean showNearbyCollegesEnabled) {
        if (!showNearbyCollegesEnabled) {
            logger.debug("Nearby colleges search skipped: 'Show Nearby Colleges' is disabled");
            return new ArrayList<>();
        }

        if (latitude == null || longitude == null) {
            logger.warn("Cannot search nearby places: latitude/longitude is null");
            return new ArrayList<>();
        }

        int radius = clampRadius(radiusMeters);

        synchronized (cacheLock) {
            if (hasCachedResults && radius == lastSearchRadius && lastSearchLat != null && lastSearchLon != null) {
                double movedMeters = HaversineUtil.calculateDistanceMeters(
                        lastSearchLat.doubleValue(), lastSearchLon.doubleValue(),
                        latitude.doubleValue(), longitude.doubleValue());

                if (movedMeters < minMoveDistanceMeters) {
                    logger.debug("Reusing cached nearby colleges: moved {}m (< {}m threshold), radius unchanged ({}m)",
                            String.format(Locale.ROOT, "%.1f", movedMeters), minMoveDistanceMeters, radius);
                    return new ArrayList<>(cachedResults);
                }
            }
        }

        List<NearbyPlaceResult> results = fetchFromGeoapify(latitude, longitude, radius);

        synchronized (cacheLock) {
            lastSearchLat = latitude;
            lastSearchLon = longitude;
            lastSearchRadius = radius;
            cachedResults = results;
            hasCachedResults = true;
        }

        return new ArrayList<>(results);
    }

    /**
     * Queries the Geoapify Places API for educational institutions
     * (colleges and universities) within the given radius. Fails soft: any
     * non-success response or exception results in an empty list.
     */
    private List<NearbyPlaceResult> fetchFromGeoapify(BigDecimal latitude, BigDecimal longitude, int radius) {
        URI uri = buildGeoapifyRequestUri(latitude, longitude, radius);

        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "EmployeeLocationTracker/1.0 (nearby educational institutions search)");
        headers.set("Accept", "application/json");

        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            logger.debug("Querying Geoapify Places API for educational institutions within {}m of [{}, {}]",
                    radius, latitude, longitude);

            ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, request, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                logger.warn("Geoapify Places API returned non-success status: {}", response.getStatusCode());
                return new ArrayList<>();
            }

            List<NearbyPlaceResult> results = parseResponse(response.getBody(), latitude, longitude);
            results.sort((a, b) -> a.getDistance().compareTo(b.getDistance()));

            logger.info("Found {} nearby educational institution(s) within {}m of [{}, {}] via Geoapify",
                    results.size(), radius, latitude, longitude);
            return results;
        } catch (RestClientException ex) {
            logger.warn("Geoapify Places API request failed: {}", ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while searching nearby places via Geoapify at [{}, {}]: {}",
                    latitude, longitude, ex.getMessage(), ex);
        }

        logger.error("Geoapify Places API search failed for nearby place search at [{}, {}]", latitude, longitude);
        return new ArrayList<>();
    }

    /**
     * Keeps the effective search radius within the 1km-10km range required by
     * the Radius dropdown (1/3/5/10 KM), regardless of what value is
     * configured or requested.
     */
    private int clampRadius(int requestedRadiusMeters) {
        int min = 1000;
        int max = 10000;
        if (requestedRadiusMeters < min) {
            return min;
        }
        if (requestedRadiusMeters > max) {
            return max;
        }
        return requestedRadiusMeters;
    }

    /**
     * Builds the Geoapify Places API request URI: a circular filter and
     * proximity bias centered on the given coordinate, restricted to college
     * and university categories.
     */
    private URI buildGeoapifyRequestUri(BigDecimal latitude, BigDecimal longitude, int radiusMeters) {
        String lat = latitude.toPlainString();
        String lon = longitude.toPlainString();

        return UriComponentsBuilder.fromHttpUrl(GEOAPIFY_PLACES_URL)
                .queryParam("categories", CATEGORY_FILTER)
                .queryParam("filter", String.format(Locale.ROOT, "circle:%s,%s,%d", lon, lat, radiusMeters))
                .queryParam("bias", String.format(Locale.ROOT, "proximity:%s,%s", lon, lat))
                .queryParam("limit", RESULT_LIMIT)
                .queryParam("apiKey", geoapifyApiKey)
                .build()
                .toUri();
    }

    private List<NearbyPlaceResult> parseResponse(String responseBody, BigDecimal originLat, BigDecimal originLon) {
        List<NearbyPlaceResult> results = new ArrayList<>();

        JsonNode root = readTree(responseBody);
        if (root == null) {
            return results;
        }

        JsonNode features = root.path("features");
        if (!features.isArray()) {
            return results;
        }

        for (JsonNode feature : features) {
            NearbyPlaceResult result = toResult(feature, originLat, originLon);
            if (result != null) {
                results.add(result);
            }
        }

        return dedupeByName(results);
    }

    private JsonNode readTree(String responseBody) {
        try {
            return objectMapper.readTree(responseBody);
        } catch (Exception ex) {
            logger.error("Failed to parse Geoapify Places API response: {}", ex.getMessage());
            return null;
        }
    }

    private NearbyPlaceResult toResult(JsonNode feature, BigDecimal originLat, BigDecimal originLon) {
        JsonNode props = feature.path("properties");
        if (props.isMissingNode()) {
            return null;
        }

        String name = props.path("name").asText(null);
        if (name == null || name.isBlank()) {
            // Skip unnamed institutions - not useful to show employees
            return null;
        }

        BigDecimal lat = extractCoordinate(feature, props, "lat", 1);
        BigDecimal lon = extractCoordinate(feature, props, "lon", 0);
        if (lat == null || lon == null) {
            return null;
        }

        double distanceMeters = HaversineUtil.calculateDistanceMeters(
                originLat.doubleValue(), originLon.doubleValue(), lat.doubleValue(), lon.doubleValue());

        NearbyPlaceResult result = new NearbyPlaceResult();
        result.setName(name);
        result.setPlaceType(mapCategoryToType(props.path("categories")));
        result.setLatitude(lat);
        result.setLongitude(lon);
        result.setDistance(BigDecimal.valueOf(distanceMeters).setScale(2, RoundingMode.HALF_UP));
        result.setAddress(buildAddress(props));

        return result;
    }

    /**
     * Geoapify normally carries lat/lon directly on the feature's
     * "properties" object; the GeoJSON "geometry.coordinates" array
     * ([lon, lat]) is used as a fallback.
     */
    private BigDecimal extractCoordinate(JsonNode feature, JsonNode props, String propertyField, int geometryIndex) {
        JsonNode direct = props.path(propertyField);
        if (direct.isNumber()) {
            return BigDecimal.valueOf(direct.asDouble());
        }

        JsonNode coordinates = feature.path("geometry").path("coordinates");
        if (coordinates.isArray() && coordinates.size() > geometryIndex) {
            JsonNode value = coordinates.get(geometryIndex);
            if (value.isNumber()) {
                return BigDecimal.valueOf(value.asDouble());
            }
        }

        return null;
    }

    private String mapCategoryToType(JsonNode categories) {
        if (categories.isArray()) {
            for (JsonNode category : categories) {
                String value = category.asText("");
                if ("education.university".equals(value)) {
                    return "University";
                }
                if ("education.college".equals(value)) {
                    return "College";
                }
            }
        }
        return "Educational Institution";
    }

    private String buildAddress(JsonNode props) {
        StringBuilder sb = new StringBuilder();

        appendPart(sb, props.path("housenumber").asText(null));
        appendPart(sb, props.path("street").asText(null));
        appendPart(sb, props.path("suburb").asText(null));
        appendPart(sb, props.path("city").asText(null));
        appendPart(sb, props.path("state").asText(null));
        appendPart(sb, props.path("postcode").asText(null));

        if (sb.length() == 0) {
            // Fall back to whatever free-text address info is available
            String fallback = props.path("formatted").asText(null);
            return (fallback == null || fallback.isBlank()) ? "Address not available" : fallback;
        }

        return sb.toString();
    }

    private void appendPart(StringBuilder sb, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(", ");
        }
        sb.append(value.trim());
    }

    /**
     * Geoapify can occasionally return the same institution more than once.
     * Keep the first (closest, since results are later sorted) occurrence
     * per name.
     */
    private List<NearbyPlaceResult> dedupeByName(List<NearbyPlaceResult> results) {
        List<NearbyPlaceResult> deduped = new ArrayList<>();
        java.util.Set<String> seenNames = new java.util.HashSet<>();

        Iterator<NearbyPlaceResult> iterator = results.iterator();
        while (iterator.hasNext()) {
            NearbyPlaceResult result = iterator.next();
            String key = result.getName().toLowerCase(Locale.ROOT).trim();
            if (seenNames.add(key)) {
                deduped.add(result);
            }
        }

        return deduped;
    }

    /**
     * Represents a single educational institution found near a given
     * location, along with its distance from the search origin.
     */
    public static class NearbyPlaceResult {

        private String name;
        private String placeType;
        private BigDecimal latitude;
        private BigDecimal longitude;
        private BigDecimal distance;
        private String address;

        public NearbyPlaceResult() {
        }

        public NearbyPlaceResult(String name, String placeType, BigDecimal latitude, BigDecimal longitude,
                                  BigDecimal distance, String address) {
            this.name = name;
            this.placeType = placeType;
            this.latitude = latitude;
            this.longitude = longitude;
            this.distance = distance;
            this.address = address;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPlaceType() {
            return placeType;
        }

        public void setPlaceType(String placeType) {
            this.placeType = placeType;
        }

        public BigDecimal getLatitude() {
            return latitude;
        }

        public void setLatitude(BigDecimal latitude) {
            this.latitude = latitude;
        }

        public BigDecimal getLongitude() {
            return longitude;
        }

        public void setLongitude(BigDecimal longitude) {
            this.longitude = longitude;
        }

        public BigDecimal getDistance() {
            return distance;
        }

        public void setDistance(BigDecimal distance) {
            this.distance = distance;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        @Override
        public String toString() {
            return "NearbyPlaceResult{" +
                    "name='" + name + '\'' +
                    ", placeType='" + placeType + '\'' +
                    ", latitude=" + latitude +
                    ", longitude=" + longitude +
                    ", distance=" + distance +
                    ", address='" + address + '\'' +
                    '}';
        }
    }
}