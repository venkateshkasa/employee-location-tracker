package com.employeetracker.service;

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
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;

/**
 * Converts a stop's Latitude/Longitude into a human readable address using
 * reverse geocoding (OpenStreetMap Nominatim - no API key required).
 * <p>
 * Addresses are NEVER hardcoded: every value returned by this service comes
 * directly from the geocoding provider's response for the given coordinates.
 * If the lookup fails for any reason (no network, provider down, unexpected
 * response), this service fails soft and returns {@code null} so callers can
 * fall back to a generic message and retry later, rather than persisting a
 * bad/placeholder value.
 */
@Service
public class ReverseGeocodingService {

    private static final Logger log = LoggerFactory.getLogger(ReverseGeocodingService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${geocoding.reverse.url:https://nominatim.openstreetmap.org/reverse}")
    private String reverseGeocodeUrl;

    @Value("${geocoding.reverse.enabled:true}")
    private boolean enabled;

    public ReverseGeocodingService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(4000);
        factory.setReadTimeout(4000);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * Looks up a human readable address for the given coordinates.
     *
     * @return the formatted address, or {@code null} if it could not be resolved
     */
    public String reverseGeocode(BigDecimal latitude, BigDecimal longitude) {
        if (!enabled || latitude == null || longitude == null) {
            return null;
        }

        try {
            URI uri = UriComponentsBuilder.fromHttpUrl(reverseGeocodeUrl)
                    .queryParam("format", "jsonv2")
                    .queryParam("lat", latitude.toPlainString())
                    .queryParam("lon", longitude.toPlainString())
                    .queryParam("zoom", 18)
                    .queryParam("addressdetails", 1)
                    .build()
                    .toUri();

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "EmployeeLocationTracker/1.0 (reverse-geocoding for stop history)");
            headers.set("Accept", "application/json");

            ResponseEntity<String> response = restTemplate.exchange(
                    uri, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return null;
            }

            return formatAddress(objectMapper.readTree(response.getBody()));
        } catch (Exception ex) {
            log.warn("Reverse geocoding failed for [{}, {}]: {}", latitude, longitude, ex.getMessage());
            return null;
        }
    }

    private String formatAddress(JsonNode root) {
        JsonNode address = root.path("address");
        if (address.isMissingNode()) {
            return blankToNull(root.path("display_name").asText(null));
        }

        String landmark = firstNonBlank(address, "road", "neighbourhood", "suburb", "hamlet");
        String locality = firstNonBlank(address, "city", "town", "village", "county");
        String state = firstNonBlank(address, "state", "state_district");
        String country = firstNonBlank(address, "country");

        StringBuilder sb = new StringBuilder();
        if (landmark != null) {
            sb.append("Near ").append(landmark);
        }
        appendPart(sb, locality);
        appendPart(sb, state);
        appendPart(sb, country);

        if (sb.length() == 0) {
            return blankToNull(root.path("display_name").asText(null));
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
        sb.append(value);
    }

    private String firstNonBlank(JsonNode address, String... fields) {
        for (String field : fields) {
            String value = address.path(field).asText(null);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}