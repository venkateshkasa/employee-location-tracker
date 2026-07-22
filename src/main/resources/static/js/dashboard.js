let currentUser = null;
let locationInterval = null;
let trackingEnabled = false;
let heartbeatInterval = null;
// While the employee dashboard is open, a heartbeat is sent to the server
// every 30 seconds (regardless of whether Tracking is ON or OFF) so the
// Admin Dashboard can tell the difference between "browser closed / lost
// connection" and "still here" via LastSeenTime, instead of relying only on
// IsLoggedIn (which beforeunload/pagehide-based approaches can't update
// reliably - see setupDesktopAutoLogout above, which is a separate,
// best-effort mechanism kept as-is).
const HEARTBEAT_INTERVAL_MS = 30 * 1000;
let notificationInterval = null;
let adminNotificationInterval = null;
let adminDataInterval = null;
let distanceChartInstance = null;
const AUTO_UPDATE_MINUTES = 10;

// Admin dashboard "real-time" sync: since there's no push channel (no
// WebSocket/SSE) between employee and admin, we keep the admin's view in
// sync by polling the existing admin endpoints frequently enough that a
// Tracking ON/OFF change made by an employee (which is written to the DB
// immediately by /api/location/tracking) shows up on the admin dashboard
// within 1-2 seconds, with no page refresh needed.
const ADMIN_LIVE_POLL_MS = 1500;

// Nearby Colleges / geofence radius state (Show Nearby Colleges works only
// while tracking is ON; see updateNearbyControlsState/toggleNearbyPlaces).
const DEFAULT_RADIUS_METERS = 3000;
let radiusMeters = DEFAULT_RADIUS_METERS;
let lastEmployeeLocation = null; // { lat, lng } - used to draw/move the circle and reload colleges
let focusedEmployee = null; // admin: the EmployeeDto currently being viewed on the admin map

document.addEventListener('DOMContentLoaded', async () => {
    try {
        const response = await API.getCurrentUser();
        if (!response.success) {
            window.location.href = '/index.html';
            return;
        }
        currentUser = response.data;
        trackingEnabled = Boolean(currentUser.trackingEnabled);
        initializeDashboard();
        setupDesktopAutoLogout();
    } catch (e) {
        window.location.href = '/index.html';
    }

    document.getElementById('logoutBtn').addEventListener('click', handleLogout);
    initDarkMode();

document
    .getElementById("darkModeBtn")
    .addEventListener("click", toggleDarkMode);
});

// ==========================================================
// Desktop Auto Logout
//
// When the browser/tab/window is closed on Desktop, the session should be
// logged out automatically: activity saved, employee status set to
// Offline, and the session invalidated. /api/auth/logout already does all
// three (see AuthService#logout) and is CSRF-exempt, so on desktop we just
// need to reliably fire that request as the page is torn down - which is
// exactly what navigator.sendBeacon() is for (fetch() is not reliable
// during unload since the browser can abort it before it completes).
//
// On mobile, closing the browser/app or sending it to the background must
// NOT log the user out - the session should continue normally - so these
// listeners are only ever attached when isDesktopDevice() is true.
// ==========================================================

let autoLogoutBeaconSent = false;

/**
 * Heuristic used only to decide whether the "close tab/browser -> auto
 * logout" behavior applies. Real mobile browsers (phones/tablets,
 * including iPadOS which reports as a Mac but exposes touch points)
 * are excluded so the mobile session-continuity requirement holds.
 */
function isDesktopDevice() {
    const ua = navigator.userAgent || navigator.vendor || '';
    const mobileRegex = /Android|iPhone|iPod|iPad|BlackBerry|IEMobile|Opera Mini|Mobile|webOS/i;
    if (mobileRegex.test(ua)) {
        return false;
    }
    // iPadOS 13+ identifies as "Macintosh" but has multi-touch support.
    if (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1) {
        return false;
    }
    return true;
}

function sendDesktopLogoutBeacon() {
    if (autoLogoutBeaconSent) return;
    autoLogoutBeaconSent = true;

    try {
        if (navigator.sendBeacon) {
            navigator.sendBeacon('/api/auth/logout');
        } else {
            // Fallback for the rare desktop browser without Beacon support.
            fetch('/api/auth/logout', { method: 'POST', keepalive: true, credentials: 'include' });
        }
    } catch (e) {
        // Best-effort only - never let this throw during page teardown.
    }
}

function setupDesktopAutoLogout() {
    if (!isDesktopDevice()) return;

    // 'pagehide' fires reliably on tab close/window close/navigation and,
    // unlike 'unload', still allows sendBeacon(); event.persisted is true
    // when the page is going into the back/forward cache rather than
    // actually closing, so skip the beacon in that case.
    window.addEventListener('pagehide', (event) => {
        if (!event.persisted) {
            sendDesktopLogoutBeacon();
        }
    });

    // 'beforeunload' is kept as a fallback for older desktop browsers.
    window.addEventListener('beforeunload', sendDesktopLogoutBeacon);
}

async function initializeDashboard() {
    document.getElementById('navUserInfo').textContent = `${currentUser.name} (${currentUser.role})`;

    if (currentUser.role === 'EMPLOYEE') {
        document.getElementById('employeeSection').classList.remove('d-none');
        document.getElementById('welcomeText').textContent = `Welcome, ${currentUser.name}`;
        await initEmployeeDashboard();
    }

    if (currentUser.role === 'ADMIN') {
        document.getElementById('adminSection').classList.remove('d-none');
        await initAdminDashboard();
    }
}

async function initEmployeeDashboard() {

    MapManager.initEmployeeMap();

    // Refresh Location
    document.getElementById('refreshLocationBtn')
        .addEventListener('click', captureAndSaveLocation);

    // Tracking ON
    document.getElementById('trackingOnBtn')
        .addEventListener('click', () => {

            if (!trackingEnabled) {
                toggleTracking(true);
            }

        });

    // Tracking OFF - stops tracking immediately, no popup.
    document.getElementById('trackingOffBtn')
        .addEventListener('click', () => {

            if (trackingEnabled) {
                toggleTracking(false);
            }

        });

    initAddStopModal();

    // Start the Online/Offline heartbeat: keeps LastSeenTime fresh every 30s
    // while this dashboard is open, whether Tracking is ON or OFF.
    startHeartbeat();

    // ===========================
    // Radius Dropdown
    // ===========================

    const radiusSelect = document.createElement('select');

    radiusSelect.className = 'form-select form-select-sm w-auto';
    radiusSelect.id = 'radiusSelect';
    radiusSelect.title = 'Nearby Colleges Radius';

    radiusSelect.innerHTML = `
        <option value="1000">1 KM</option>
        <option value="3000" selected>3 KM</option>
        <option value="5000">5 KM</option>
        <option value="10000">10 KM</option>
    `;

    radiusSelect.addEventListener('change', onRadiusChange);

    // ===========================
    // Show Nearby Colleges Button
    // ===========================

    const toggleNearbyBtn = document.createElement('button');

    toggleNearbyBtn.className = 'btn btn-outline-secondary btn-sm';
    toggleNearbyBtn.id = 'toggleNearbyBtn';

    toggleNearbyBtn.innerHTML =
        '<i class="bi bi-building me-1"></i>Show Nearby Colleges';

    toggleNearbyBtn.addEventListener('click', toggleNearbyPlaces);

    const mapHeader =
        document.querySelector('#employeeSection .card-header .d-flex');

    if (mapHeader) {

        mapHeader.appendChild(radiusSelect);

        mapHeader.appendChild(toggleNearbyBtn);

    }

    updateTrackingButton();

    initMyReports();

    // ===========================
    // Pending State (before any data loads)
    // ===========================
    // Paint the "nothing known yet" state synchronously, before any async
    // fetch has a chance to resolve. This must happen first: it guarantees
    // the employee never sees a leftover render (Status/Lat/Lng/Today's
    // Distance) from whatever was on screen a moment ago - e.g. right after
    // logging in on a shared browser where a previous employee's dashboard
    // was just showing. See setLocationPendingUI() below.
    setLocationPendingUI();

    // ===========================
    // Load Dashboard
    // ===========================

    // A fresh GPS reading is about to be captured below (see "Start
    // Tracking") regardless of whether tracking is currently ON or OFF for
    // this employee. Always skip painting the map/marker from whatever
    // location happens to already be stored in the DB - it may belong to a
    // previous login from a completely different city/device - and let the
    // fresh reading populate it instead. Non-location widgets (distance,
    // stops, activities, route history) still load normally, and - thanks to
    // the pending state painted above plus the "no-store" fetch/response
    // caching fix (see api.js and WebConfig) - are now guaranteed to reflect
    // only the currently authenticated employee's real data, never a
    // previous employee's cached response.
    await loadEmployeeData({ skipLocationDisplay: true });

    // ===========================
    // Notification System
    // ===========================

    await loadNotifications();

    startNotificationPolling();

    // Mark All Read
    const markAllBtn =
        document.getElementById("markAllReadBtn");

    if (markAllBtn) {

        markAllBtn.addEventListener(
            "click",
            markAllNotificationsAsRead
        );

    }

    // Mark Single Notification
    const markReadBtn =
        document.getElementById("markAsReadBtn");

    if (markReadBtn) {

        markReadBtn.addEventListener(
            "click",
            async () => {

                if (!selectedNotification)
                    return;

                await API.markNotificationAsRead(
                    selectedNotification.notificationId
                );

                bootstrap.Modal
                    .getInstance(
                        document.getElementById("notificationModal")
                    )
                    .hide();

                await loadNotifications();

            }
        );

    }

    // Show On Map
    const showMapBtn =
        document.getElementById("showOnMapBtn");

    if (showMapBtn) {

        showMapBtn.addEventListener("click", () => {

    if (!selectedNotification) return;

    if (
        selectedNotification.latitude == null ||
        selectedNotification.longitude == null
    ) {
        alert("Location not available for this notification.");
        return;
    }

    const url =
        `https://www.google.com/maps/dir/?api=1&destination=` +
        `${selectedNotification.latitude},${selectedNotification.longitude}`;

    window.open(url, "_blank");

    bootstrap.Modal
        .getInstance(document.getElementById("notificationModal"))
        .hide();

});

    }

    // ===========================
    // Fresh Location On Login
    // ===========================
    // Never leave the previous device/city's stored coordinates on screen
    // after a login. A brand new GPS reading is requested immediately,
    // regardless of the employee's persisted tracking ON/OFF preference:
    //   - If tracking is ON, the fresh fix is saved to the EmployeeLocation
    //     table (via captureAndSaveLocation) and the periodic auto-update
    //     interval is started, exactly as before.
    //   - If tracking is OFF, the fresh fix is still fetched and shown on
    //     the employee's own map for their own reference, but - to keep the
    //     tracking toggle's existing meaning intact - it is NOT written to
    //     the EmployeeLocation table and is NOT visible to admins, since
    //     that still requires tracking to be turned on.
    // (setLocationPendingUI() already ran at the very start of
    // initEmployeeDashboard(), before any data was loaded - see above.)

    if (trackingEnabled) {

        await captureAndSaveLocation();

        startLocationInterval();

    } else {

        await captureFreshLocationForDisplayOnly();

    }

}

function initMyReports() {
    const todayStr = new Date().toISOString().split('T')[0];
    document.getElementById('myReportFromDate').value = todayStr;
    document.getElementById('myReportToDate').value = todayStr;

    document.getElementById('generateMyReportBtn').addEventListener('click', generateMyReport);
    document.getElementById('exportMyExcelBtn').addEventListener('click', exportMyReport);
    document.getElementById('printMyReportBtn').addEventListener('click', () => window.print());
}

async function generateMyReport() {
    // The logged-in employee's identity is resolved entirely on the server
    // (authService.getCurrentUserEntity()) - no userId is ever sent from here,
    // so an employee can never request another employee's report.
    const fromDate = document.getElementById('myReportFromDate').value;
    const toDate = document.getElementById('myReportToDate').value;
    const container = document.getElementById('myReportResult');

    if (fromDate && toDate && fromDate > toDate) {
        container.innerHTML = '<div class="alert alert-danger">From Date must not be after To Date</div>';
        return;
    }

    container.innerHTML = '<p class="text-muted text-center">Generating report...</p>';

    try {
        const response = await API.getMyReport(fromDate, toDate);
        if (response.success) {
            renderMyReport(response.data);
        } else {
            container.innerHTML = `<div class="alert alert-danger">${response.message || 'Failed to generate report'}</div>`;
        }
    } catch (error) {
        container.innerHTML = '<div class="alert alert-danger">Failed to generate report</div>';
    }
}

function renderMyReport(report) {
    document.getElementById('myReportResult').innerHTML = buildReportSectionsHtml(report);
}

/**
 * Builds a detailed "Stop N" card for every stop, showing exactly where the
 * employee stopped: address (via reverse geocoding), lat/lng, start/end time,
 * duration, and a Google Maps link. Shared by both the employee's "My
 * Reports" view and the admin's "Reports" view so the stop history logic
 * lives in one place.
 */
function buildStopHistoryHtml(stops) {
    if (!stops || stops.length === 0) {
        return '<p class="text-muted text-center">No stops in this range</p>';
    }

    return stops.map((stop, index) => {
        const mapsUrl = stop.googleMapsUrl || `https://www.google.com/maps?q=${stop.latitude},${stop.longitude}`;
        const address = stop.address || 'Address unavailable';
        const reasonRow = stop.manual ? `
                    <div><small class="text-muted">Stop Reason</small><br><strong>${stop.stopReasonLabel || '-'}</strong></div>
                    <div><small class="text-muted">Remarks</small><br><strong>${stop.remarks || '-'}</strong></div>` : '';

        return `
            <div class="stop-history-card">
                <div class="stop-history-card-header">
                    <span class="stop-history-number">Stop ${index + 1}</span>
                    ${stop.manual ? '<span class="badge bg-secondary">Manual Stop</span>' : ''}
                </div>
                <p class="stop-history-address">📍 ${address}</p>
                <div class="stop-history-grid">
                    <div><small class="text-muted">Latitude</small><br><strong>${Number(stop.latitude).toFixed(6)}</strong></div>
                    <div><small class="text-muted">Longitude</small><br><strong>${Number(stop.longitude).toFixed(6)}</strong></div>
                    <div><small class="text-muted">Start Time</small><br><strong>${stop.startTime || '-'}</strong></div>
                    <div><small class="text-muted">End Time</small><br><strong>${stop.endTime || 'Ongoing'}</strong></div>
                    <div><small class="text-muted">Duration</small><br><strong>${stop.duration || 'Calculating...'}</strong></div>${reasonRow}
                </div>
                <a href="${mapsUrl}" target="_blank" rel="noopener" class="stop-history-map-link">
                    <i class="bi bi-geo-alt-fill me-1"></i>Open in Google Maps
                </a>
            </div>
        `;
    }).join('');
}

/**
 * Builds the full report body (Summary -> Route -> Stop History -> Activity
 * Timeline) for a ReportDto. Reused by both the employee's own report and
 * the admin's report for any employee, so the layout and stop-history
 * rendering logic is never duplicated between the two views.
 */
function buildReportSectionsHtml(report) {
    const locationRows = (report.locations || []).map(loc => `
        <tr>
            <td>${loc.locationTime}</td>
            <td>${Number(loc.latitude).toFixed(6)}</td>
            <td>${Number(loc.longitude).toFixed(6)}</td>
            <td>${loc.address || 'Unknown Address'}</td>
        </tr>
    `).join('');

    const activityRows = (report.activities || []).map(activity => `
        <tr>
            <td>${activity.activityTime}</td>
            <td>${formatActivityType(activity.activityType)}</td>
            <td>${activity.description || ''}</td>
        </tr>
    `).join('');

    return `
        <div class="report-summary mb-4">
            <h6 class="fw-bold mb-3">Report: ${report.employeeName} (${report.fromDate} to ${report.toDate})</h6>
            <div class="row">
                <div class="col-md-4"><small class="text-muted">Total Distance</small><br><strong>${formatDistance(report.totalDistanceKm)} km</strong></div>
                <div class="col-md-4"><small class="text-muted">Total Stops</small><br><strong>${report.totalStops}</strong></div>
                <div class="col-md-4"><small class="text-muted">Total Location Updates</small><br><strong>${report.totalLocationUpdates}</strong></div>
            </div>
        </div>

        <h6 class="fw-bold">Location History (Route)</h6>
        <div class="table-responsive mb-4">
            <table class="table table-sm table-hover">
                <thead class="table-light"><tr><th>Time</th><th>Latitude</th><th>Longitude</th><th>Address</th></tr></thead>
                <tbody>${locationRows || '<tr><td colspan="4" class="text-center text-muted">No location updates in this range</td></tr>'}</tbody>
            </table>
        </div>

        <h6 class="fw-bold">Stop History</h6>
        <div class="stop-history-list mb-4">
            ${buildStopHistoryHtml(report.stops)}
        </div>

        <h6 class="fw-bold">Activity History</h6>
        <div class="table-responsive">
            <table class="table table-sm table-hover">
                <thead class="table-light"><tr><th>Time</th><th>Type</th><th>Description</th></tr></thead>
                <tbody>${activityRows || '<tr><td colspan="3" class="text-center text-muted">No activities in this range</td></tr>'}</tbody>
            </table>
        </div>
    `;
}

async function exportMyReport() {
    const fromDate = document.getElementById('myReportFromDate').value;
    const toDate = document.getElementById('myReportToDate').value;

    if (fromDate && toDate && fromDate > toDate) {
        alert('From Date must not be after To Date');
        return;
    }

    try {
        const response = await API.exportMyReport(fromDate, toDate);
        const blob = await response.blob();
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `my-report-${fromDate}-to-${toDate}.xlsx`;
        a.click();
        window.URL.revokeObjectURL(url);
    } catch (error) {
        alert('Failed to export report');
    }
}

function startLocationInterval() {
    stopLocationInterval();
    locationInterval = setInterval(captureAndSaveLocation, AUTO_UPDATE_MINUTES * 60 * 1000);
}

function stopLocationInterval() {
    if (locationInterval) {
        clearInterval(locationInterval);
        locationInterval = null;
    }
}

/** Sends one heartbeat immediately, then every 30s while the dashboard is open. */
function startHeartbeat() {
    stopHeartbeat();
    sendHeartbeatBeat();
    heartbeatInterval = setInterval(sendHeartbeatBeat, HEARTBEAT_INTERVAL_MS);
}

function stopHeartbeat() {
    if (heartbeatInterval) {
        clearInterval(heartbeatInterval);
        heartbeatInterval = null;
    }
}

async function sendHeartbeatBeat() {
    try {
        await API.sendHeartbeat();
    } catch (e) {
        // Best-effort: a failed/offline heartbeat is exactly the case the
        // server-side timeout is designed to catch, so just let the next
        // scheduled attempt retry - no need to surface this to the user.
    }
}

async function loadEmployeeData(options = {}) {
    const { skipLocationDisplay = false } = options;
    try {
        const [locationRes, distanceRes, stopsRes, activitiesRes, historyRes] = await Promise.all([
            API.getCurrentLocation().catch(() => null),
            API.getTodayDistance(),
            API.getTodayStops(),
            API.getTodayActivities(),
            API.getLocationHistory(new Date().toISOString().split('T')[0]).catch(() => null)
        ]);

        if (!skipLocationDisplay && locationRes && locationRes.success) {
            updateEmployeeUI(locationRes.data);
        }

        if (distanceRes && distanceRes.success) {
            document.getElementById('todayDistance').textContent = `${formatDistance(distanceRes.data.distanceKm)} km`;
        }

        if (stopsRes && stopsRes.success) {
            renderStopHistory(stopsRes.data);
        }

        if (activitiesRes && activitiesRes.success) {
            renderActivityTimeline(activitiesRes.data);
        }

        if (historyRes && historyRes.success && historyRes.data.length > 0) {
            const routePoints = historyRes.data
                .map(loc => ({
                    latitude: parseFloat(loc.latitude),
                    longitude: parseFloat(loc.longitude)
                }))
                .filter(loc => Number.isFinite(loc.latitude) && Number.isFinite(loc.longitude));
            MapManager.drawEmployeeRoute(routePoints);
        }
    } catch (error) {
        console.error('Error loading employee data:', error);
    }
}

function updateEmployeeUI(location) {
    if (!location) return;

    trackingEnabled = Boolean(location.trackingEnabled);
    updateTrackingButton();

    const status = location.status || 'OFFLINE';
    const lat = parseFloat(location.latitude);
    const lng = parseFloat(location.longitude);
    const hasCoordinates = Number.isFinite(lat) && Number.isFinite(lng);

    document.getElementById('currentStatus').innerHTML =
        `<span class="status-badge status-${status.toLowerCase()}">${status}</span>`;
    document.getElementById('currentLat').textContent = hasCoordinates ? lat.toFixed(6) : '-';
    document.getElementById('currentLng').textContent = hasCoordinates ? lng.toFixed(6) : '-';
    document.getElementById('lastUpdated').textContent = `Last Updated: ${location.locationTime || '-'}`;

    if (location.todayDistanceKm !== undefined) {
        document.getElementById('todayDistance').textContent = `${formatDistance(location.todayDistanceKm)} km`;
    }

   if (trackingEnabled && hasCoordinates) {

    const popup = MapManager.createPopupContent(
        location.employeeName || currentUser.name,
        location.locationTime || '-',
        lat.toFixed(6),
        lng.toFixed(6),
        status
    );

    MapManager.updateEmployeeMarker(lat, lng, popup);

    lastEmployeeLocation = { lat, lng };

    // Move the geofence circle with the employee's live location and
    // refresh nearby colleges automatically when they move (no-op if
    // Show Nearby Colleges isn't currently active).
    refreshNearbyCollegesAndCircle(lat, lng);

}
}

function updateTrackingButton() {

    const onBtn = document.getElementById('trackingOnBtn');
    const offBtn = document.getElementById('trackingOffBtn');
    const refreshBtn = document.getElementById('refreshLocationBtn');

    if (!onBtn || !offBtn || !refreshBtn) return;

    if (trackingEnabled) {

        onBtn.disabled = true;
        offBtn.disabled = false;
        refreshBtn.disabled = false;

        document.getElementById("currentStatus").innerHTML =
            '<span class="status-badge status-online">ONLINE</span>';

    } else {

        onBtn.disabled = false;
        offBtn.disabled = true;
        refreshBtn.disabled = true;

        document.getElementById("currentStatus").innerHTML =
            '<span class="status-badge status-offline">OFFLINE</span>';

    }

    updateNearbyControlsState();

}

/**
 * Show Nearby Colleges (and the Radius dropdown) only work while tracking
 * is ON. Whenever tracking turns OFF: disable both controls, remove the
 * geofence circle, and remove any nearby college markers.
 */
function updateNearbyControlsState() {
    const toggleBtn = document.getElementById('toggleNearbyBtn');
    const radiusSelect = document.getElementById('radiusSelect');
    if (!toggleBtn || !radiusSelect) return;

    toggleBtn.disabled = !trackingEnabled;
    radiusSelect.disabled = !trackingEnabled;

    if (!trackingEnabled) {
        MapManager.showNearbyPlaces = false;
        MapManager.clearNearbyPlaces();
        toggleBtn.innerHTML = '<i class="bi bi-building me-1"></i>Show Nearby Colleges';
    }
}

/**
 * Draws/moves the geofence circle at the given coordinates using the
 * currently selected radius, and reloads nearby colleges inside it.
 */
async function refreshNearbyCollegesAndCircle(lat, lng) {
    if (!trackingEnabled || !MapManager.showNearbyPlaces) return;

    MapManager.drawGeofenceCircle(false, lat, lng, radiusMeters);

    try {
        const nearbyRes = await API.getNearbyPlaces({ latitude: lat, longitude: lng, radius: radiusMeters });
        if (nearbyRes && nearbyRes.success) {
            MapManager.updateNearbyPlaces(nearbyRes.data);
        }
    } catch (error) {
        console.error('Error loading nearby colleges:', error);
    }
}

/**
 * Radius dropdown change: remove the old circle, draw the new one, and
 * reload nearby colleges inside the newly selected radius.
 */
async function onRadiusChange(event) {
    radiusMeters = Number(event.target.value) || DEFAULT_RADIUS_METERS;

    if (!MapManager.showNearbyPlaces || !lastEmployeeLocation) return;

    await refreshNearbyCollegesAndCircle(lastEmployeeLocation.lat, lastEmployeeLocation.lng);
}

async function toggleTracking(enable) {

    const nextState = enable;

    try {

        const response = await API.setTracking(nextState);

        if (response.success) {

            trackingEnabled = nextState;

            updateTrackingButton();

            if (trackingEnabled) {

                startLocationInterval();
                await captureAndSaveLocation();

            } else {

                stopLocationInterval();

                document.getElementById("currentLat").innerHTML="-";
                document.getElementById("currentLng").innerHTML="-";
                  if (MapManager.clearEmployeeMarker) {
        MapManager.clearEmployeeMarker();
    }


            }

        }

    } catch(error){

        console.error(error);

    }

}

// ==========================================================
// Add Stop Popup (opened via the "+ Add Stop" button in the Stop History
// card header - completely independent of Tracking ON/OFF).
//
// The employee picks a Stop Reason, enters a Start Time and an End Time,
// and optionally Remarks (mandatory only when Reason = "Other"). Saving
// validates the form, captures the employee's current GPS location
// automatically, and posts everything to /api/tracking-stop. Tracking
// ON/OFF state is never touched by this popup.
// ==========================================================

let addStopModalInstance = null;

function initAddStopModal() {
    const modalEl = document.getElementById('addStopModal');
    if (!modalEl) return;

    addStopModalInstance = bootstrap.Modal.getOrCreateInstance(modalEl);

    const addStopBtn = document.getElementById('addStopBtn');
    if (addStopBtn) {
        addStopBtn.addEventListener('click', openAddStopModal);
    }

    const reasonSelect = document.getElementById('addStopReasonSelect');
    const remarksRequiredMark = document.getElementById('addStopRemarksRequiredMark');
    const remarksHint = document.getElementById('addStopRemarksHint');

    if (reasonSelect) {
        reasonSelect.addEventListener('change', () => {
            const isOther = reasonSelect.value === 'OTHER';
            if (remarksRequiredMark) remarksRequiredMark.classList.toggle('d-none', !isOther);
            if (remarksHint) remarksHint.textContent = isOther ? 'Required when reason is Other' : 'Optional';
            hideAddStopAlert();
        });
    }

    const cancelBtn = document.getElementById('addStopCancelBtn');
    const cancelXBtn = document.getElementById('addStopCancelXBtn');
    [cancelBtn, cancelXBtn].forEach(btn => {
        if (btn) {
            btn.addEventListener('click', () => {
                addStopModalInstance.hide();
            });
        }
    });

    const saveBtn = document.getElementById('addStopSaveBtn');
    if (saveBtn) {
        saveBtn.addEventListener('click', confirmAddStop);
    }
}

function showAddStopAlert(message) {
    const alertBox = document.getElementById('addStopAlert');
    if (!alertBox) return;
    alertBox.textContent = message;
    alertBox.classList.remove('d-none');
}

function hideAddStopAlert() {
    const alertBox = document.getElementById('addStopAlert');
    if (!alertBox) return;
    alertBox.classList.add('d-none');
    alertBox.textContent = '';
}

function openAddStopModal() {
    if (!addStopModalInstance) return;

    // Reset the form to a clean state every time it opens.
    const reasonSelect = document.getElementById('addStopReasonSelect');
    const startInput = document.getElementById('addStopStartTime');
    const endInput = document.getElementById('addStopEndTime');
    const remarksInput = document.getElementById('addStopRemarksInput');
    const remarksRequiredMark = document.getElementById('addStopRemarksRequiredMark');
    const remarksHint = document.getElementById('addStopRemarksHint');
    if (reasonSelect) reasonSelect.value = '';
    if (startInput) startInput.value = '';
    if (endInput) endInput.value = '';
    if (remarksInput) remarksInput.value = '';
    if (remarksRequiredMark) remarksRequiredMark.classList.add('d-none');
    if (remarksHint) remarksHint.textContent = 'Optional';
    hideAddStopAlert();

    addStopModalInstance.show();
}

/**
 * Returns the employee's current GPS coordinates automatically - reusing
 * whatever the dashboard already has on hand (lastEmployeeLocation, kept up
 * to date by updateEmployeeUI()/captureAndSaveLocation()) so the employee
 * is never prompted to enter a location manually. Falls back to a single
 * fresh GPS reading only if no location has been captured yet this
 * session.
 */
async function getCurrentLocationForStop() {
    if (lastEmployeeLocation && Number.isFinite(lastEmployeeLocation.lat) && Number.isFinite(lastEmployeeLocation.lng)) {
        return { latitude: lastEmployeeLocation.lat, longitude: lastEmployeeLocation.lng };
    }

    if (!navigator.geolocation) {
        return null;
    }

    try {
        const position = await getFreshGpsPosition();
        return { latitude: position.coords.latitude, longitude: position.coords.longitude };
    } catch (error) {
        console.error('Unable to capture current location for stop:', error);
        return null;
    }
}

async function confirmAddStop() {
    const reasonSelect = document.getElementById('addStopReasonSelect');
    const startInput = document.getElementById('addStopStartTime');
    const endInput = document.getElementById('addStopEndTime');
    const remarksInput = document.getElementById('addStopRemarksInput');
    const saveBtn = document.getElementById('addStopSaveBtn');

    const stopReason = reasonSelect ? reasonSelect.value : '';
    const startTime = startInput ? startInput.value : '';
    const endTime = endInput ? endInput.value : '';
    const remarks = remarksInput ? remarksInput.value.trim() : '';

    hideAddStopAlert();

    if (!stopReason) {
        showAddStopAlert('Please select a Stop Reason.');
        return;
    }

    if (!startTime) {
        showAddStopAlert('Please enter a Start Time.');
        return;
    }

    if (!endTime) {
        showAddStopAlert('Please enter an End Time.');
        return;
    }

    if (endTime <= startTime) {
        showAddStopAlert('End Time must be greater than Start Time.');
        return;
    }

    if (stopReason === 'OTHER' && !remarks) {
        showAddStopAlert('Remarks is required when Stop Reason is Other.');
        return;
    }

    const originalHtml = saveBtn ? saveBtn.innerHTML : '';
    if (saveBtn) {
        saveBtn.disabled = true;
        saveBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Saving...';
    }

    try {
        const location = await getCurrentLocationForStop();
        if (!location) {
            showAddStopAlert('Unable to capture your current location. Please enable location access and try again.');
            return;
        }

        const response = await API.addStop({
            stopReason,
            remarks: remarks || null,
            startTime,
            endTime,
            latitude: location.latitude,
            longitude: location.longitude
        });

        if (response && response.success) {
            addStopModalInstance.hide();

            // Refresh Stop History / Activity Timeline so the new manual
            // stop shows up immediately - tracking state is untouched.
            await loadEmployeeData({ skipLocationDisplay: true });
        } else {
            showAddStopAlert((response && response.message) || 'Unable to save the stop. Please try again.');
        }
    } catch (error) {
        console.error('Error adding stop:', error);
        showAddStopAlert('Unable to save the stop. Please try again.');
    } finally {
        if (saveBtn) {
            saveBtn.disabled = false;
            saveBtn.innerHTML = originalHtml;
        }
    }
}

// How long to wait before automatically retrying when GPS is temporarily
// unavailable (error.code 2) or a fix times out (error.code 3). Permission
// denial (error.code 1) is never retried automatically - the user has to
// grant access first.
const GPS_RETRY_DELAY_MS = 5000;

/**
 * Wraps navigator.geolocation.getCurrentPosition in a real Promise so
 * callers can actually await a fresh GPS fix instead of the callback firing
 * asynchronously after the caller has already moved on.
 * maximumAge: 0 forces the browser to take a brand new reading rather than
 * reusing any cached position - this is what guarantees the app never shows
 * a location left over from a previous login/device/city.
 */
function getFreshGpsPosition() {
    return new Promise((resolve, reject) => {
        navigator.geolocation.getCurrentPosition(
            resolve,
            reject,
            { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 }
        );
    });
}

/**
 * Shown while a fresh GPS reading is being obtained (e.g. right after
 * login). Deliberately does NOT display any previously stored coordinates,
 * marker, or distance - all of those are reset to their "nothing known
 * yet" state here, synchronously, before any data has been fetched for
 * this session. This runs first, ahead of loadEmployeeData()/
 * captureAndSaveLocation(), so there is no window in which a leftover
 * value (e.g. from a previous employee's session on the same browser)
 * could still be on screen. Today's Distance is then correctly replaced
 * once loadEmployeeData()/captureAndSaveLocation() resolve with the real,
 * freshly-fetched value for the now-authenticated employee.
 */
function setLocationPendingUI() {
    const statusEl = document.getElementById('currentStatus');
    const latEl = document.getElementById('currentLat');
    const lngEl = document.getElementById('currentLng');
    const updatedEl = document.getElementById('lastUpdated');
    const distanceEl = document.getElementById('todayDistance');
    if (!statusEl || !latEl || !lngEl || !updatedEl) return;

    statusEl.innerHTML = '<span class="status-badge status-online">LOCATING...</span>';
    latEl.textContent = '-';
    lngEl.textContent = '-';
    updatedEl.textContent = 'Fetching current location...';
    if (distanceEl) {
        distanceEl.textContent = '0.00 km';
    }

    if (MapManager.clearEmployeeMarker) {
        MapManager.clearEmployeeMarker();
    }
}

/**
 * Shown when GPS cannot be used right now (permission denied, or the
 * device reported an error). Explicitly clears the displayed
 * lat/lng/marker so the UI never keeps showing an old, no-longer-current
 * location as though it were live.
 */
function setLocationUnavailableUI(message) {
    const statusEl = document.getElementById('currentStatus');
    const latEl = document.getElementById('currentLat');
    const lngEl = document.getElementById('currentLng');
    const updatedEl = document.getElementById('lastUpdated');
    if (!statusEl || !latEl || !lngEl || !updatedEl) return;

    statusEl.innerHTML = '<span class="status-badge status-offline">LOCATION UNAVAILABLE</span>';
    latEl.textContent = '-';
    lngEl.textContent = '-';
    updatedEl.textContent = message;

    if (MapManager.clearEmployeeMarker) {
        MapManager.clearEmployeeMarker();
    }
}

async function captureAndSaveLocation() {
    if (!trackingEnabled) {
        return;
    }

    if (!navigator.geolocation) {
        setLocationUnavailableUI('Geolocation is not supported by this browser.');
        alert('Geolocation is not supported by your browser.');
        return;
    }

    const btn = document.getElementById('refreshLocationBtn');
    if (btn) {
        btn.disabled = true;
        btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Updating...';
    }

    try {
        // Force a brand new GPS reading (maximumAge: 0 inside
        // getFreshGpsPosition) - never reuse a cached/previous position.
        const position = await getFreshGpsPosition();

        if (!trackingEnabled) return;

        // Only forward the selected radius while "Show Nearby Colleges" is
        // actually on - otherwise let the server fall back to its default
        // radius, same as before this feature existed.
        const response = await API.saveLocation(
            position.coords.latitude,
            position.coords.longitude,
            position.coords.accuracy,
            MapManager.showNearbyPlaces ? radiusMeters : undefined
        );

        if (response.success) {
            // Immediately reflect the fresh location on the employee's own
            // map; the admin dashboard picks it up on its next poll
            // (ADMIN_LIVE_POLL_MS), which already refreshes automatically.
            updateEmployeeUI(response.data);
            await loadEmployeeData();
        }
    } catch (error) {
        console.error('Geolocation error:', error);

        if (error && error.code === 1) {
            // PERMISSION_DENIED - do not fall back to the old stored
            // location; make it clear no current location is available.
            setLocationUnavailableUI('Location access denied. Enable location permissions to update your position.');
            alert('GPS permission denied. Please enable location access to track your position.');
        } else {
            // POSITION_UNAVAILABLE (2) or TIMEOUT (3) - GPS could not
            // produce a fix right now. Keep the UI honest about that and
            // retry automatically in the background until a fresh reading
            // succeeds.
            setLocationUnavailableUI('Waiting for GPS signal...');
            if (trackingEnabled) {
                setTimeout(captureAndSaveLocation, GPS_RETRY_DELAY_MS);
            }
        }
    } finally {
        if (btn) {
            btn.disabled = !trackingEnabled;
            btn.innerHTML = '<i class="bi bi-arrow-clockwise me-1"></i>Refresh Location';
        }
    }
}

/**
 * Used on login when the employee's tracking toggle is currently OFF.
 * Fetches one brand new GPS reading (same maximumAge: 0 guarantee as
 * captureAndSaveLocation) purely to show the employee their real current
 * position instead of whatever was last stored in the DB - but, unlike
 * captureAndSaveLocation, it never calls the save API, so the
 * EmployeeLocation table and the admin dashboard are untouched while
 * tracking remains off. This preserves the existing meaning of the
 * tracking toggle: turning it on is still what starts persisted/admin-
 * visible tracking.
 */
async function captureFreshLocationForDisplayOnly() {
    if (trackingEnabled) {
        // Tracking was turned on while this was pending - let the regular
        // captureAndSaveLocation/interval path take over instead.
        return;
    }

    if (!navigator.geolocation) {
        setLocationUnavailableUI('Geolocation is not supported by this browser.');
        return;
    }

    try {
        const position = await getFreshGpsPosition();

        if (trackingEnabled) {
            // Tracking got turned on mid-flight; captureAndSaveLocation
            // already owns display + saving now.
            return;
        }

        const lat = position.coords.latitude;
        const lng = position.coords.longitude;

        document.getElementById('currentStatus').innerHTML =
            '<span class="status-badge status-offline">TRACKING OFF</span>';
        document.getElementById('currentLat').textContent = lat.toFixed(6);
        document.getElementById('currentLng').textContent = lng.toFixed(6);
        document.getElementById('lastUpdated').textContent = 'Current device location (not being tracked)';

        const popup = MapManager.createPopupContent(
            currentUser.name,
            'Now',
            lat.toFixed(6),
            lng.toFixed(6),
            'OFFLINE'
        );
        MapManager.updateEmployeeMarker(lat, lng, popup, 'OFFLINE');
        lastEmployeeLocation = { lat, lng };
    } catch (error) {
        console.error('Geolocation error:', error);

        if (error && error.code === 1) {
            setLocationUnavailableUI('Location access denied. Enable location permissions to see your current position.');
        } else {
            // GPS not ready yet - keep retrying quietly until it is, same
            // as the tracking-enabled path.
            setLocationUnavailableUI('Waiting for GPS signal...');
            if (!trackingEnabled) {
                setTimeout(captureFreshLocationForDisplayOnly, GPS_RETRY_DELAY_MS);
            }
        }
    }
}

function renderActivityTimeline(activities) {
    const container = document.getElementById('activityTimeline');
    if (!activities || activities.length === 0) {
        container.innerHTML = '<p class="text-muted text-center">No activities today</p>';
        return;
    }

    container.innerHTML = activities.map(activity => {
        const typeClass = activity.activityType.toLowerCase().replaceAll('_', '');
        return `
            <div class="timeline-item ${typeClass}">
                <p class="timeline-time">${activity.activityTime}</p>
                <p class="timeline-desc"><strong>${formatActivityType(activity.activityType)}</strong></p>
                <p class="timeline-desc text-muted">${activity.description || ''}</p>
            </div>
        `;
    }).join('');
}

function renderStopHistory(stops) {
    const container = document.getElementById('stopHistory');
    if (!stops || stops.length === 0) {
        container.innerHTML = '<p class="text-muted text-center">No stops recorded today</p>';
        return;
    }

    container.innerHTML = stops.map(stop => {
        // Use the address already saved on the stop (same value shown in
        // Admin Reports) - never re-run reverse geocoding here. Only fall
        // back to lat/lng if no usable address was saved.
        const hasAddress = stop.address && stop.address.trim() !== '' && stop.address !== 'Address unavailable';
        const locationLine = hasAddress
            ? `📍 ${escapeHtml(stop.address)}`
            : `Location: ${Number(stop.latitude).toFixed(4)}, ${Number(stop.longitude).toFixed(4)}`;

        return `
        <div class="stop-item">
            <strong>${stop.startTime}</strong> - ${stop.endTime || 'Ongoing'}
            ${stop.manual ? '<span class="badge bg-secondary-subtle text-secondary-emphasis ms-1">Manual</span>' : ''}<br>
            ${stop.stopReasonLabel ? `<small class="text-muted">Reason: ${escapeHtml(stop.stopReasonLabel)}</small><br>` : ''}
            <small class="text-muted">Duration: ${stop.duration || 'Calculating...'}</small><br>
            <small class="text-muted">${locationLine}</small>
        </div>
    `;
    }).join('');
}

function formatActivityType(type) {
    const types = {
        'LOGIN': 'Login',
        'LOGOUT': 'Logout',
        'TRACKING_ENABLED': 'Tracking Enabled',
        'TRACKING_DISABLED': 'Tracking Disabled',
        'LOCATION_UPDATE': 'Location Updated',
        'STOP_STARTED': 'Stop Started',
        'STOP_ENDED': 'Stop Ended',
        'STOP': 'Stop Detected',
        'MANUAL_STOP_STARTED': 'Tracking Stopped (Reason Given)',
        'MANUAL_STOP_ENDED': 'Tracking Resumed',
        'MANUAL_STOP_ADDED': 'Stop Added'
    };
    return types[type] || type;
}

async function initAdminDashboard() {
    MapManager.initAdminMap();
    const todayStr = new Date().toISOString().split('T')[0];
    document.getElementById('reportFromDate').value = todayStr;
    document.getElementById('reportToDate').value = todayStr;

    document.getElementById('generateReportBtn').addEventListener('click', generateReport);
    document.getElementById('exportExcelBtn').addEventListener('click', exportReport);
    document.getElementById('printReportBtn').addEventListener('click', () => window.print());

    // Add nearby places toggle button for admin
    const adminToggleNearbyBtn = document.createElement('button');
    adminToggleNearbyBtn.className = 'btn btn-outline-secondary btn-sm';
    adminToggleNearbyBtn.id = 'adminToggleNearbyBtn';
    adminToggleNearbyBtn.innerHTML = '<i class="bi bi-building me-1"></i>Show Nearby Colleges';
    adminToggleNearbyBtn.addEventListener('click', toggleAdminNearbyPlaces);
    
    const adminMapHeader = document.querySelector('#adminSection .card-header');
    if (adminMapHeader) {
        adminMapHeader.appendChild(adminToggleNearbyBtn);
    }

    // Employee List: Search box + Status filter
    const employeeSearchInput = document.getElementById('employeeSearchInput');
    const statusFilter = document.getElementById('statusFilter');

    if (employeeSearchInput) {
        employeeSearchInput.addEventListener('input', applyEmployeeFilters);
    }

    if (statusFilter) {
        statusFilter.addEventListener('change', applyEmployeeFilters);
    }

    const departmentFilter = document.getElementById('departmentFilter');
    if (departmentFilter) {
        departmentFilter.addEventListener('change', applyEmployeeFilters);
    }

    const employeeSortSelect = document.getElementById('employeeSortSelect');
    if (employeeSortSelect) {
        employeeSortSelect.addEventListener('change', applyEmployeeFilters);
    }

    initEmployeeManagement();
    initStopHistoryReport();

    await loadAdminData();
    adminDataInterval = setInterval(loadAdminData, ADMIN_LIVE_POLL_MS);

    // Admin Notifications card (e.g. employees entering nearby colleges).
    // Polls independently of loadAdminData so new alerts show up quickly.
    await loadAdminNotifications();
    startAdminNotificationPolling();
}

/**
 * Sets up the "Stop History Report" card: default date range and the
 * Search button click handler. The Employee dropdown itself is populated
 * from allEmployees once loadAdminData() resolves (see
 * populateStopHistoryEmployeeSelect(), called alongside populateReportSelect()).
 */
function initStopHistoryReport() {
    const todayStr = new Date().toISOString().split('T')[0];
    const fromInput = document.getElementById('stopHistoryFromDate');
    const toInput = document.getElementById('stopHistoryToDate');
    if (fromInput) fromInput.value = todayStr;
    if (toInput) toInput.value = todayStr;

    const searchBtn = document.getElementById('searchStopHistoryBtn');
    if (searchBtn) {
        searchBtn.addEventListener('click', searchStopHistory);
    }
}

/** Keeps the Stop History "Employee" filter in sync with the current employee list. */
function populateStopHistoryEmployeeSelect(employees) {
    const select = document.getElementById('stopHistoryEmployeeSelect');
    if (!select) return;

    const current = select.value;
    select.innerHTML = '<option value="">All Employees</option>' +
        (employees || []).map(emp =>
            `<option value="${emp.userId}">${escapeHtml(emp.name)} (${escapeHtml(emp.employeeId)})</option>`
        ).join('');
    select.value = current;
}

async function searchStopHistory() {
    const userId = document.getElementById('stopHistoryEmployeeSelect')?.value || '';
    const fromDate = document.getElementById('stopHistoryFromDate')?.value || '';
    const toDate = document.getElementById('stopHistoryToDate')?.value || '';
    const stopReason = document.getElementById('stopHistoryReasonFilter')?.value || '';
    const tbody = document.getElementById('stopHistoryTableBody');

    if (!tbody) return;

    if (fromDate && toDate && fromDate > toDate) {
        tbody.innerHTML = '<tr><td colspan="11" class="text-center text-danger">From Date must not be after To Date</td></tr>';
        return;
    }

    tbody.innerHTML = '<tr><td colspan="11" class="text-center text-muted">Loading...</td></tr>';

    try {
        const response = await API.getStopHistoryReport({
            userId: userId || undefined,
            fromDate: fromDate || undefined,
            toDate: toDate || undefined,
            stopReason: stopReason || undefined
        });

        if (response && response.success) {
            renderStopHistoryReport(response.data);
        } else {
            tbody.innerHTML = `<tr><td colspan="11" class="text-center text-danger">${escapeHtml((response && response.message) || 'Failed to load stop history')}</td></tr>`;
        }
    } catch (error) {
        console.error('Error loading stop history report:', error);
        tbody.innerHTML = '<tr><td colspan="11" class="text-center text-danger">Failed to load stop history</td></tr>';
    }
}

function renderStopHistoryReport(stops) {
    const tbody = document.getElementById('stopHistoryTableBody');
    if (!tbody) return;

    if (!stops || stops.length === 0) {
        tbody.innerHTML = '<tr><td colspan="11" class="text-center text-muted">No stop history found for the selected filters</td></tr>';
        return;
    }

    tbody.innerHTML = stops.map(stop => `
        <tr>
            <td>${escapeHtml(stop.employeeName)}</td>
            <td><small class="text-muted">${escapeHtml(stop.employeeId)}</small></td>
            <td>${escapeHtml(stop.date)}</td>
            <td>${escapeHtml(stop.stopReasonLabel || stop.stopReason)}</td>
            <td>${escapeHtml(stop.remarks) || '-'}</td>
            <td>${escapeHtml(stop.startTime)}</td>
            <td>${stop.endTime ? escapeHtml(stop.endTime) : '<span class="status-badge status-online">Ongoing</span>'}</td>
            <td>${escapeHtml(stop.duration)}</td>
            <td>${escapeHtml(stop.address)}</td>
            <td>${Number(stop.latitude).toFixed(6)}</td>
            <td>${Number(stop.longitude).toFixed(6)}</td>
        </tr>
    `).join('');
}

/**
 * Fetches admin-facing notifications from /api/admin/notifications and
 * renders them into the "Admin Notifications" card on the admin dashboard.
 */
async function loadAdminNotifications() {
    try {
        const response = await API.getAdminNotifications();
        if (!response || !response.success) return;
        renderAdminNotifications(response.data);
    } catch (error) {
        console.error('Error loading admin notifications:', error);
    }
}

/**
 * Renders the list of AdminNotificationDto entries into the #adminNotifications
 * card, keeping the original "No notifications" empty state.
 */
function renderAdminNotifications(notifications) {
    const container = document.getElementById('adminNotifications');
    if (!container) return;

    if (!notifications || notifications.length === 0) {
        container.innerHTML = '<p class="text-muted text-center">No notifications</p>';
        return;
    }

    container.innerHTML = notifications.map(n => `
        <div class="admin-notification-item mb-2 pb-2 border-bottom">
            <div class="d-flex justify-content-between align-items-start">
                <strong>${n.employeeName || (n.userId ? ('Employee ' + n.userId) : 'System')}</strong>
                <small class="text-muted ms-2">${n.time || ''}</small>
            </div>
            <div class="small">${n.message || ''}</div>
        </div>
    `).join('');
}

function startAdminNotificationPolling() {
    stopAdminNotificationPolling();
    adminNotificationInterval = setInterval(loadAdminNotifications, 5000);
}

function stopAdminNotificationPolling() {
    if (adminNotificationInterval) {
        clearInterval(adminNotificationInterval);
        adminNotificationInterval = null;
    }
}

// Cache of the latest employees fetched from the server, so Search/Status
// filtering can be applied client-side without an extra API call.
let allEmployees = [];

/**
 * Filters the cached employee list by the Search box (name or employee ID)
 * and the Status dropdown (online/offline/inside office/outside office),
 * then re-renders the employee table with the results.
 */
function applyEmployeeFilters() {
    const searchTerm = (document.getElementById('employeeSearchInput')?.value || '').trim().toLowerCase();
    const statusValue = document.getElementById('statusFilter')?.value || '';
    const departmentValue = document.getElementById('departmentFilter')?.value || '';
    const sortValue = document.getElementById('employeeSortSelect')?.value || 'name';

    const filtered = allEmployees.filter(emp => {
        const matchesSearch = !searchTerm ||
            (emp.name && emp.name.toLowerCase().includes(searchTerm)) ||
            (emp.employeeId && emp.employeeId.toLowerCase().includes(searchTerm));

        if (!matchesSearch) return false;

        if (departmentValue && emp.department !== departmentValue) return false;

        if (!statusValue) return true;

        const trackingStatus = (emp.trackingStatus || '').toUpperCase();

        if (statusValue === 'online') return trackingStatus !== 'OFFLINE';
        if (statusValue === 'offline') return trackingStatus === 'OFFLINE';
        if (statusValue === 'inside') return Boolean(emp.insideOffice);
        if (statusValue === 'outside') return !emp.insideOffice;

        return true;
    });

    const sorted = filtered.slice().sort((a, b) => {
        if (sortValue === 'distance') return (b.todayDistanceKm || 0) - (a.todayDistanceKm || 0);
        if (sortValue === 'status') return (a.trackingStatus || '').localeCompare(b.trackingStatus || '');
        if (sortValue === 'lastSeen') return (b.lastUpdated || '').localeCompare(a.lastUpdated || '');
        return (a.name || '').localeCompare(b.name || '');
    });

    renderEmployeeTable(sorted);
}

/**
 * Keeps the Department filter dropdown and the Add/Edit Employee form's
 * Department datalist in sync with whatever departments currently exist
 * among employees, without needing a separate lookup endpoint.
 */
function populateDepartmentOptions(employees) {
    const departments = Array.from(new Set(
        (employees || []).map(e => e.department).filter(Boolean)
    )).sort();

    const filterSelect = document.getElementById('departmentFilter');
    if (filterSelect) {
        const current = filterSelect.value;
        filterSelect.innerHTML = '<option value="">All Departments</option>' +
            departments.map(d => `<option value="${escapeHtml(d)}">${escapeHtml(d)}</option>`).join('');
        filterSelect.value = departments.includes(current) ? current : '';
    }

    const datalist = document.getElementById('departmentOptions');
    if (datalist) {
        datalist.innerHTML = departments.map(d => `<option value="${escapeHtml(d)}">`).join('');
    }
}

/** Basic HTML-escaping so employee-entered text can't break table/modal markup. */
function escapeHtml(value) {
    if (value === null || value === undefined) return '';
    return String(value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

let adminDataLoadInFlight = false;

async function loadAdminData() {
    if (adminDataLoadInFlight) return;
    adminDataLoadInFlight = true;
    try {
        const [summaryRes, employeesRes, liveRes, nearbyRes] = await Promise.all([
            API.getAdminSummary(),
            API.getEmployees(),
            API.getLiveLocations(),
            API.getAdminNearbyPlaces().catch(() => null)
        ]);

        if (summaryRes && summaryRes.success) {
            document.getElementById('totalEmployees').textContent = summaryRes.data.totalEmployees;
            document.getElementById('onlineEmployees').textContent = summaryRes.data.onlineEmployees;
            document.getElementById('offlineEmployees').textContent = summaryRes.data.offlineEmployees;
        }

        if (employeesRes && employeesRes.success) {
            allEmployees = employeesRes.data;
            populateDepartmentOptions(allEmployees);
            applyEmployeeFilters();
            populateReportSelect(employeesRes.data);
            populateStopHistoryEmployeeSelect(employeesRes.data);
        }

        if (liveRes && liveRes.success) {
            MapManager.updateAdminMarkers(liveRes.data.map(loc => ({
                userId: loc.userId,
                name: loc.employeeName,
                latitude: parseFloat(loc.latitude),
                longitude: parseFloat(loc.longitude),
                status: loc.status,
                locationTime: loc.locationTime
            })));

            // Move the focused employee's geofence circle with their live
            // location and refresh their nearby colleges automatically.
            if (focusedEmployee) {
                const updated = liveRes.data.find(loc => String(loc.userId) === String(focusedEmployee.userId));
                if (updated) {
                    focusedEmployee.latitude = updated.latitude;
                    focusedEmployee.longitude = updated.longitude;
                    focusedEmployee.trackingStatus = updated.status;
                    await refreshAdminGeofenceAndColleges();
                }
            }
        }

        if (!focusedEmployee && nearbyRes && nearbyRes.success) {
            MapManager.updateAdminNearbyPlaces(nearbyRes.data);
        }
    } catch (error) {
        console.error('Error loading admin data:', error);
    } finally {
        adminDataLoadInFlight = false;
    }
}

function renderEmployeeTable(employees) {
    const tbody = document.getElementById('employeeTableBody');
    if (!employees || employees.length === 0) {
        tbody.innerHTML = '<tr><td colspan="11" class="text-center text-muted">No employees found</td></tr>';
        return;
    }

    tbody.innerHTML = employees.map(emp => {
        const trackingStatus = (emp.trackingStatus || 'OFFLINE').toUpperCase();
        const isOnline = trackingStatus !== 'OFFLINE';
        const accountStatus = (emp.accountStatus || 'ACTIVE').toUpperCase();
        const isActive = accountStatus === 'ACTIVE';
        const currentLocation = emp.address || emp.officeName || '-';

        return `
        <tr>
            <td><strong>${escapeHtml(emp.name)}</strong></td>
            <td><small class="text-muted">${escapeHtml(emp.employeeId)}</small></td>
            <td>${escapeHtml(emp.department) || '-'}</td>
            <td>${escapeHtml(emp.designation) || '-'}</td>
            <td><span class="status-badge ${isActive ? 'status-online' : 'status-offline'}">${isActive ? 'Active' : 'Inactive'}</span></td>
            <td><span class="status-badge ${isOnline ? 'status-online' : 'status-offline'}">${isOnline ? 'ON' : 'OFF'}</span></td>
            <td>${escapeHtml(emp.officeLocation) || '-'}</td>
            <td>${escapeHtml(emp.lastSeen || emp.lastUpdated) || '-'}</td>
            <td>${formatDistance(emp.todayDistanceKm)} km</td>
            <td>${escapeHtml(currentLocation)}</td>
            <td>
                <div class="d-flex flex-wrap gap-1">
                    <button class="btn btn-sm btn-outline-primary view-employee-btn" data-id="${emp.userId}" title="View">
                        <i class="bi bi-eye"></i>
                    </button>
                    <button class="btn btn-sm btn-outline-secondary edit-employee-btn" data-id="${emp.userId}" title="Edit">
                        <i class="bi bi-pencil"></i>
                    </button>
                    <button class="btn btn-sm btn-outline-warning reset-password-btn" data-id="${emp.userId}" title="Reset Password">
                        <i class="bi bi-key"></i>
                    </button>
                    <button class="btn btn-sm ${isActive ? 'btn-outline-danger' : 'btn-outline-success'} toggle-status-btn" data-id="${emp.userId}" data-status="${accountStatus}" title="${isActive ? 'Deactivate' : 'Activate'}">
                        <i class="bi ${isActive ? 'bi-toggle-on' : 'bi-toggle-off'}"></i>
                    </button>
                    <button class="btn btn-sm btn-outline-danger delete-employee-btn" data-id="${emp.userId}" data-name="${escapeHtml(emp.name)}" title="Delete">
                        <i class="bi bi-trash3"></i>
                    </button>
                </div>
            </td>
        </tr>`;
    }).join('');

    document.querySelectorAll('.view-employee-btn').forEach(btn => {
        btn.addEventListener('click', () => openViewEmployeeModal(btn.dataset.id));
    });

    document.querySelectorAll('.edit-employee-btn').forEach(btn => {
        btn.addEventListener('click', () => openEditEmployeeModal(btn.dataset.id));
    });

    document.querySelectorAll('.reset-password-btn').forEach(btn => {
        btn.addEventListener('click', () => openResetPasswordModal(btn.dataset.id));
    });

    document.querySelectorAll('.toggle-status-btn').forEach(btn => {
        btn.addEventListener('click', () => toggleEmployeeStatus(btn.dataset.id, btn.dataset.status));
    });

    document.querySelectorAll('.delete-employee-btn').forEach(btn => {
        btn.addEventListener('click', () => openDeleteConfirm(btn.dataset.id, btn.dataset.name));
    });
}

// ==========================================================
// Employee Management: Add / Edit / View / Reset Password /
// Activate-Deactivate / Delete
// ==========================================================

let pendingPhotoDataUrl = null;

function initEmployeeManagement() {
    const addBtn = document.getElementById('addEmployeeBtn');
    const formModalEl = document.getElementById('employeeFormModal');

    if (formModalEl) {
        formModalEl.addEventListener('show.bs.modal', (event) => {
            // Only reset into "Add" mode when the modal was opened via the
            // Add Employee button - openEditEmployeeModal() populates the
            // form itself and shows the modal programmatically, so it
            // should be left alone here.
            if (event.relatedTarget && event.relatedTarget.id === 'addEmployeeBtn') {
                resetEmployeeForm();
            }
        });
    }

    const form = document.getElementById('employeeForm');
    if (form) {
        form.addEventListener('submit', submitEmployeeForm);
    }

    const photoInput = document.getElementById('empPhotoFile');
    if (photoInput) {
        photoInput.addEventListener('change', handlePhotoFileChange);
    }

    const resetPasswordForm = document.getElementById('resetPasswordForm');
    if (resetPasswordForm) {
        resetPasswordForm.addEventListener('submit', submitResetPassword);
    }

    const deleteConfirmBtn = document.getElementById('deleteConfirmBtn');
    if (deleteConfirmBtn) {
        deleteConfirmBtn.addEventListener('click', confirmDeleteEmployee);
    }

    const showOnMapBtn = document.getElementById('viewEmpShowOnMapBtn');
    if (showOnMapBtn) {
        showOnMapBtn.addEventListener('click', showViewedEmployeeOnMap);
    }
}

function resetEmployeeForm() {
    const form = document.getElementById('employeeForm');
    if (form) form.reset();

    pendingPhotoDataUrl = null;
    document.getElementById('empUserId').value = '';
    document.getElementById('empEmployeeId').value = '';
    document.getElementById('empEmployeeId').placeholder = 'Auto-generated';
    document.getElementById('empPhotoPreview').classList.add('d-none');
    document.getElementById('empPhotoPreview').src = '';
    document.getElementById('empAccountStatus').value = 'ACTIVE';

    document.getElementById('empPasswordGroup').classList.remove('d-none');
    document.getElementById('empConfirmPasswordGroup').classList.remove('d-none');
    document.getElementById('empPassword').setAttribute('required', 'required');
    document.getElementById('empConfirmPassword').setAttribute('required', 'required');
    document.getElementById('empUsername').removeAttribute('disabled');

    document.getElementById('employeeFormModalLabel').innerHTML =
        '<i class="bi bi-person-plus-fill text-primary me-2"></i>Add Employee';
    document.getElementById('employeeFormSubmitBtn').innerHTML =
        '<i class="bi bi-check-circle me-1"></i>Create Employee';

    hideFormAlert();
}

function handlePhotoFileChange(event) {
    const file = event.target.files && event.target.files[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = () => {
        pendingPhotoDataUrl = reader.result;
        const preview = document.getElementById('empPhotoPreview');
        preview.src = pendingPhotoDataUrl;
        preview.classList.remove('d-none');
    };
    reader.readAsDataURL(file);
}

function showFormAlert(message) {
    const alertBox = document.getElementById('employeeFormAlert');
    alertBox.textContent = message;
    alertBox.classList.remove('d-none');
}

function hideFormAlert() {
    const alertBox = document.getElementById('employeeFormAlert');
    alertBox.classList.add('d-none');
    alertBox.textContent = '';
}

/**
 * Client-side mandatory-field + uniqueness pre-check for the Add/Edit
 * Employee form. The server re-validates everything regardless (source of
 * truth for uniqueness), this just gives immediate feedback.
 */
function validateEmployeeForm(isEdit) {
    const requiredFields = [
        ['empFullName', 'Full Name'],
        ['empEmail', 'Email'],
        ['empMobile', 'Mobile Number'],
        ['empDepartment', 'Department'],
        ['empDesignation', 'Designation'],
        ['empJoiningDate', 'Joining Date'],
        ['empOfficeLocation', 'Office Location'],
        ['empUsername', 'Username']
    ];

    for (const [id, label] of requiredFields) {
        const value = document.getElementById(id).value.trim();
        if (!value) {
            return `${label} is required`;
        }
    }

    if (!isEdit) {
        const password = document.getElementById('empPassword').value;
        const confirmPassword = document.getElementById('empConfirmPassword').value;
        if (!password) return 'Password is required';
        if (!confirmPassword) return 'Confirm Password is required';
        if (password !== confirmPassword) return 'Password and Confirm Password must match';
    }

    const userId = document.getElementById('empUserId').value;
    const email = document.getElementById('empEmail').value.trim().toLowerCase();
    const mobile = document.getElementById('empMobile').value.trim();
    const username = document.getElementById('empUsername').value.trim().toLowerCase();

    const conflict = allEmployees.find(emp => String(emp.userId) !== String(userId) && (
        (emp.email && emp.email.toLowerCase() === email) ||
        (emp.phone && emp.phone === mobile) ||
        (emp.username && emp.username.toLowerCase() === username)
    ));

    if (conflict) {
        if (conflict.email && conflict.email.toLowerCase() === email) return 'Email must be unique - this email is already in use';
        if (conflict.phone && conflict.phone === mobile) return 'Mobile Number must be unique - this number is already in use';
        return 'Username must be unique - this username is already in use';
    }

    return null;
}

function buildEmployeePayload() {
    return {
        name: document.getElementById('empFullName').value.trim(),
        email: document.getElementById('empEmail').value.trim(),
        mobile: document.getElementById('empMobile').value.trim(),
        gender: document.getElementById('empGender').value,
        dateOfBirth: document.getElementById('empDob').value,
        address: document.getElementById('empAddress').value.trim(),
        photoUrl: pendingPhotoDataUrl,
        department: document.getElementById('empDepartment').value.trim(),
        designation: document.getElementById('empDesignation').value.trim(),
        reportingManager: document.getElementById('empReportingManager').value.trim(),
        joiningDate: document.getElementById('empJoiningDate').value,
        employeeType: document.getElementById('empType').value,
        officeLocation: document.getElementById('empOfficeLocation').value.trim(),
        shift: document.getElementById('empShift').value,
        username: document.getElementById('empUsername').value.trim(),
        accountStatus: document.getElementById('empAccountStatus').value
    };
}

async function submitEmployeeForm(event) {
    event.preventDefault();
    hideFormAlert();

    const userId = document.getElementById('empUserId').value;
    const isEdit = Boolean(userId);

    const validationError = validateEmployeeForm(isEdit);
    if (validationError) {
        showFormAlert(validationError);
        return;
    }

    const payload = buildEmployeePayload();

    const submitBtn = document.getElementById('employeeFormSubmitBtn');
    const originalHtml = submitBtn.innerHTML;
    submitBtn.disabled = true;
    submitBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Saving...';

    try {
        let response;
        if (isEdit) {
            response = await API.updateEmployee(userId, payload);
        } else {
            payload.password = document.getElementById('empPassword').value;
            payload.confirmPassword = document.getElementById('empConfirmPassword').value;
            response = await API.createEmployee(payload);
        }

        if (response && response.success) {
            bootstrap.Modal.getOrCreateInstance(document.getElementById('employeeFormModal')).hide();
            showToast(isEdit ? 'Employee updated successfully' : 'Employee created successfully', 'success');
            resetEmployeeForm();
            await loadAdminData();
        } else {
            showFormAlert((response && response.message) || 'Unable to save employee');
        }
    } catch (error) {
        console.error('Error saving employee:', error);
        showFormAlert('Unable to save employee. Please try again.');
    } finally {
        submitBtn.disabled = false;
        submitBtn.innerHTML = originalHtml;
    }
}

function openEditEmployeeModal(userId) {
    const emp = allEmployees.find(e => String(e.userId) === String(userId));
    if (!emp) return;

    resetEmployeeForm();

    document.getElementById('empUserId').value = emp.userId;
    document.getElementById('empEmployeeId').value = emp.employeeId || '';
    document.getElementById('empEmployeeId').placeholder = '';
    document.getElementById('empFullName').value = emp.name || '';
    document.getElementById('empEmail').value = emp.email || '';
    document.getElementById('empMobile').value = emp.phone || '';
    document.getElementById('empGender').value = emp.gender || '';
    document.getElementById('empDob').value = emp.dateOfBirth || '';
    document.getElementById('empAddress').value = emp.residentialAddress || '';
    document.getElementById('empDepartment').value = emp.department || '';
    document.getElementById('empDesignation').value = emp.designation || '';
    document.getElementById('empReportingManager').value = emp.manager || '';
    document.getElementById('empJoiningDate').value = emp.joiningDate || '';
    document.getElementById('empType').value = emp.employeeType || 'Full Time';
    document.getElementById('empOfficeLocation').value = emp.officeLocation || '';
    document.getElementById('empShift').value = emp.shift || 'General Shift';
    document.getElementById('empUsername').value = emp.username || '';
    document.getElementById('empAccountStatus').value = emp.accountStatus || 'ACTIVE';

    if (emp.photoUrl) {
        pendingPhotoDataUrl = emp.photoUrl;
        const preview = document.getElementById('empPhotoPreview');
        preview.src = emp.photoUrl;
        preview.classList.remove('d-none');
    }

    // Password is changed via the dedicated Reset Password action, not here.
    document.getElementById('empPasswordGroup').classList.add('d-none');
    document.getElementById('empConfirmPasswordGroup').classList.add('d-none');
    document.getElementById('empPassword').removeAttribute('required');
    document.getElementById('empConfirmPassword').removeAttribute('required');

    document.getElementById('employeeFormModalLabel').innerHTML =
        '<i class="bi bi-pencil-square text-primary me-2"></i>Edit Employee';
    document.getElementById('employeeFormSubmitBtn').innerHTML =
        '<i class="bi bi-check-circle me-1"></i>Update Employee';

    bootstrap.Modal.getOrCreateInstance(document.getElementById('employeeFormModal')).show();
}

function openResetPasswordModal(userId) {
    const emp = allEmployees.find(e => String(e.userId) === String(userId));

    document.getElementById('resetPasswordForm').reset();
    document.getElementById('resetPasswordAlert').classList.add('d-none');
    document.getElementById('resetPasswordUserId').value = userId;
    document.getElementById('resetPasswordEmpLabel').textContent = emp
        ? `Set a new password for ${emp.name} (${emp.employeeId}).`
        : 'Set a new password for this employee.';

    bootstrap.Modal.getOrCreateInstance(document.getElementById('resetPasswordModal')).show();
}

async function submitResetPassword(event) {
    event.preventDefault();

    const userId = document.getElementById('resetPasswordUserId').value;
    const newPassword = document.getElementById('resetNewPassword').value;
    const confirmPassword = document.getElementById('resetConfirmPassword').value;
    const alertBox = document.getElementById('resetPasswordAlert');
    alertBox.classList.add('d-none');

    if (!newPassword || !confirmPassword) {
        alertBox.textContent = 'Both password fields are required';
        alertBox.classList.remove('d-none');
        return;
    }

    if (newPassword !== confirmPassword) {
        alertBox.textContent = 'Password and Confirm Password must match';
        alertBox.classList.remove('d-none');
        return;
    }

    try {
        const response = await API.resetEmployeePassword(userId, { newPassword, confirmPassword });
        if (response && response.success) {
            bootstrap.Modal.getOrCreateInstance(document.getElementById('resetPasswordModal')).hide();
            showToast('Password reset successfully', 'success');
        } else {
            alertBox.textContent = (response && response.message) || 'Unable to reset password';
            alertBox.classList.remove('d-none');
        }
    } catch (error) {
        console.error('Error resetting password:', error);
        alertBox.textContent = 'Unable to reset password. Please try again.';
        alertBox.classList.remove('d-none');
    }
}

async function toggleEmployeeStatus(userId, currentStatus) {
    const activating = currentStatus === 'INACTIVE';
    const emp = allEmployees.find(e => String(e.userId) === String(userId));
    const label = emp ? emp.name : 'this employee';

    const confirmed = window.confirm(`${activating ? 'Activate' : 'Deactivate'} ${label}?`);
    if (!confirmed) return;

    try {
        const response = await API.updateEmployeeStatus(userId, activating ? 'ACTIVE' : 'INACTIVE');
        if (response && response.success) {
            showToast(`Employee ${activating ? 'activated' : 'deactivated'} successfully`, 'success');
            await loadAdminData();
        } else {
            showToast((response && response.message) || 'Unable to update employee status', 'danger');
        }
    } catch (error) {
        console.error('Error updating employee status:', error);
        showToast('Unable to update employee status', 'danger');
    }
}

function openDeleteConfirm(userId, name) {
    document.getElementById('deleteConfirmEmpName').textContent = name || 'this employee';
    document.getElementById('deleteConfirmBtn').dataset.id = userId;
    bootstrap.Modal.getOrCreateInstance(document.getElementById('deleteConfirmModal')).show();
}

async function confirmDeleteEmployee() {
    const btn = document.getElementById('deleteConfirmBtn');
    const userId = btn.dataset.id;
    if (!userId) return;

    const originalHtml = btn.innerHTML;
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Deleting...';

    try {
        const response = await API.deleteEmployee(userId);
        bootstrap.Modal.getOrCreateInstance(document.getElementById('deleteConfirmModal')).hide();
        if (response && response.success) {
            showToast('Employee deleted successfully', 'success');
            await loadAdminData();
        } else {
            showToast((response && response.message) || 'Unable to delete employee', 'danger');
        }
    } catch (error) {
        console.error('Error deleting employee:', error);
        bootstrap.Modal.getOrCreateInstance(document.getElementById('deleteConfirmModal')).hide();
        showToast('Unable to delete employee', 'danger');
    } finally {
        btn.disabled = false;
        btn.innerHTML = originalHtml;
    }
}

let viewedEmployee = null;

async function openViewEmployeeModal(userId) {
    try {
        const response = await API.getEmployee(userId);
        if (!response || !response.success) {
            showToast((response && response.message) || 'Unable to load employee details', 'danger');
            return;
        }

        const emp = response.data;
        viewedEmployee = emp;

        document.getElementById('viewEmpName').textContent = emp.name || '-';
        document.getElementById('viewEmpId').textContent = emp.employeeId || '-';
        document.getElementById('viewEmpDepartment').textContent = emp.department || '-';
        document.getElementById('viewEmpDesignation').textContent = emp.designation || '-';
        document.getElementById('viewEmpEmail').textContent = emp.email || '-';
        document.getElementById('viewEmpMobile').textContent = emp.phone || '-';
        document.getElementById('viewEmpOfficeLocation').textContent = emp.officeLocation || '-';

        const trackingStatus = (emp.trackingStatus || 'OFFLINE').toUpperCase();
        document.getElementById('viewEmpTrackingStatus').innerHTML =
            `<span class="status-badge ${trackingStatus !== 'OFFLINE' ? 'status-online' : 'status-offline'}">${trackingStatus}</span>`;

        document.getElementById('viewEmpCurrentLocation').textContent = emp.address || emp.officeName || '-';
        document.getElementById('viewEmpDistance').textContent = `${formatDistance(emp.todayDistanceKm)} km`;
        document.getElementById('viewEmpLastSeen').textContent = emp.lastSeen || emp.lastUpdated || '-';
        document.getElementById('viewEmpJoiningDate').textContent = emp.joiningDate || '-';
        document.getElementById('viewEmpManager').textContent = emp.manager || '-';

        const photo = document.getElementById('viewEmpPhoto');
        const fallback = document.getElementById('viewEmpPhotoFallback');
        if (emp.photoUrl) {
            photo.src = emp.photoUrl;
            photo.classList.remove('d-none');
            fallback.classList.add('d-none');
        } else {
            photo.classList.add('d-none');
            fallback.classList.remove('d-none');
        }

        const showOnMapBtn = document.getElementById('viewEmpShowOnMapBtn');
        const hasLocation = Number.isFinite(Number(emp.latitude)) && Number.isFinite(Number(emp.longitude));
        showOnMapBtn.disabled = !hasLocation;

        bootstrap.Modal.getOrCreateInstance(document.getElementById('viewEmployeeModal')).show();
    } catch (error) {
        console.error('Error loading employee details:', error);
        showToast('Unable to load employee details', 'danger');
    }
}

async function showViewedEmployeeOnMap() {
    if (!viewedEmployee) return;

    const shown = MapManager.focusOnEmployee(viewedEmployee);
    if (!shown) {
        showToast(`${viewedEmployee.name} has no saved location yet.`, 'danger');
        return;
    }

    focusedEmployee = viewedEmployee;
    bootstrap.Modal.getOrCreateInstance(document.getElementById('viewEmployeeModal')).hide();
    await refreshAdminGeofenceAndColleges();
}

/** Lightweight Bootstrap toast, styled to match the existing theme's alert colors. */
function showToast(message, type = 'success') {
    const container = document.getElementById('appToastContainer');
    if (!container) return;

    const bg = type === 'success' ? 'text-bg-success' : (type === 'danger' ? 'text-bg-danger' : 'text-bg-secondary');
    const icon = type === 'success' ? 'bi-check-circle-fill' : (type === 'danger' ? 'bi-exclamation-triangle-fill' : 'bi-info-circle-fill');

    const toastEl = document.createElement('div');
    toastEl.className = `toast align-items-center ${bg} border-0`;
    toastEl.setAttribute('role', 'alert');
    toastEl.innerHTML = `
        <div class="d-flex">
            <div class="toast-body"><i class="bi ${icon} me-2"></i>${escapeHtml(message)}</div>
            <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast"></button>
        </div>`;

    container.appendChild(toastEl);
    const toast = new bootstrap.Toast(toastEl, { delay: 4000 });
    toastEl.addEventListener('hidden.bs.toast', () => toastEl.remove());
    toast.show();
}

/**
 * Draws the currently focused employee's geofence circle and loads their
 * nearby colleges (default radius), so the admin dashboard shows the same
 * circle + colleges view as the employee dashboard for whichever employee
 * is selected. No-op unless Show Nearby Colleges is active, an employee is
 * focused, and that employee is actually online/tracking.
 */
async function refreshAdminGeofenceAndColleges() {
    if (!MapManager.showNearbyPlaces || !focusedEmployee) return;

    const lat = Number(focusedEmployee.latitude);
    const lng = Number(focusedEmployee.longitude);
    const isOnline = String(focusedEmployee.trackingStatus || '').toUpperCase() !== 'OFFLINE';

    if (!Number.isFinite(lat) || !Number.isFinite(lng) || !isOnline) {
        MapManager.removeGeofenceCircle(true);
        return;
    }

    MapManager.drawGeofenceCircle(true, lat, lng, DEFAULT_RADIUS_METERS);

    try {
        const nearbyRes = await API.getAdminNearbyPlaces({
            userId: focusedEmployee.userId,
            latitude: lat,
            longitude: lng,
            radius: DEFAULT_RADIUS_METERS
        });
        if (nearbyRes && nearbyRes.success) {
            MapManager.updateAdminNearbyPlaces(nearbyRes.data);
        }
    } catch (error) {
        console.error('Error loading nearby colleges for employee:', error);
    }
}

function populateReportSelect(employees) {
    const select = document.getElementById('reportEmployeeSelect');
    // loadAdminData() re-runs this every ADMIN_LIVE_POLL_MS to refresh the
    // live employee list. Rebuilding innerHTML resets a <select>'s selection
    // to its first option, so without saving/restoring the current value the
    // admin's chosen employee would get silently reverted to the first
    // employee in the list within ~1.5s of picking it - causing "Generate
    // Report" to always report on that first employee regardless of what was
    // selected. Same guard already used by populateStopHistoryEmployeeSelect.
    const current = select.value;
    select.innerHTML = employees.map(emp =>
        `<option value="${emp.userId}">${emp.name} (${emp.employeeId})</option>`
    ).join('');
    if (current && select.querySelector(`option[value="${current}"]`)) {
        select.value = current;
    }
}

async function generateReport() {
    const userId = document.getElementById('reportEmployeeSelect').value;
    const fromDate = document.getElementById('reportFromDate').value;
    const toDate = document.getElementById('reportToDate').value;
    const container = document.getElementById('reportResult');

    if (fromDate && toDate && fromDate > toDate) {
        container.innerHTML = '<div class="alert alert-danger">From Date must not be after To Date</div>';
        return;
    }

    try {
        const response = await API.getReport(userId, fromDate, toDate);
        if (response.success) {
            container.innerHTML = buildReportSectionsHtml(response.data);
            renderDistanceChart(response.data);
        } else {
            container.innerHTML = `<div class="alert alert-danger">${response.message || 'Failed to generate report'}</div>`;
        }
    } catch (error) {
        container.innerHTML = '<div class="alert alert-danger">Failed to generate report</div>';
    }
}

/**
 * Computes the great-circle distance (km) between two lat/lng points using
 * the Haversine formula. Used to build a cumulative "distance travelled"
 * series for the Distance Chart from the report's location history.
 */
function haversineDistanceKm(lat1, lon1, lat2, lon2) {
    const toRad = deg => (deg * Math.PI) / 180;
    const R = 6371; // Earth radius in km
    const dLat = toRad(lat2 - lat1);
    const dLon = toRad(lon2 - lon1);
    const a = Math.sin(dLat / 2) ** 2 +
        Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c;
}

/**
 * Renders the "Distance Charts" line chart on the admin dashboard using the
 * report's location history, showing cumulative distance travelled over
 * time. Handles the case where there is only a single location point (still
 * draws a single-point chart at 0 km instead of leaving the canvas empty).
 */
function renderDistanceChart(report) {
    const canvas = document.getElementById('distanceChart');
    if (!canvas || typeof Chart === 'undefined') return;

    const locations = (report.locations || []).slice();

    const labels = [];
    const data = [];

    if (locations.length === 0) {
        // No location history at all for this range - nothing to plot.
        labels.push(report.fromDate || '');
        data.push(0);
    } else if (locations.length === 1) {
        // Only one point exists: still display the chart with a single point.
        labels.push(locations[0].locationTime || '');
        data.push(0);
    } else {
        let cumulativeKm = 0;
        labels.push(locations[0].locationTime || '');
        data.push(0);

        for (let i = 1; i < locations.length; i++) {
            const prev = locations[i - 1];
            const curr = locations[i];
            cumulativeKm += haversineDistanceKm(
                Number(prev.latitude), Number(prev.longitude),
                Number(curr.latitude), Number(curr.longitude)
            );
            labels.push(curr.locationTime || '');
            data.push(Number(cumulativeKm.toFixed(2)));
        }
    }

    if (distanceChartInstance) {
        distanceChartInstance.destroy();
        distanceChartInstance = null;
    }

    distanceChartInstance = new Chart(canvas, {
        type: 'line',
        data: {
            labels: labels,
            datasets: [{
                label: `${report.employeeName || 'Employee'} - Distance Travelled (km)`,
                data: data,
                borderColor: '#0d6efd',
                backgroundColor: 'rgba(13, 110, 253, 0.15)',
                fill: true,
                tension: 0.3,
                pointRadius: locations.length <= 1 ? 5 : 3
            }]
        },
        options: {
            responsive: true,
            scales: {
                x: { title: { display: true, text: 'Time' } },
                y: { title: { display: true, text: 'Distance (km)' }, beginAtZero: true }
            },
            plugins: {
                legend: { display: true }
            }
        }
    });
}

async function exportReport() {
    const userId = document.getElementById('reportEmployeeSelect').value;
    const fromDate = document.getElementById('reportFromDate').value;
    const toDate = document.getElementById('reportToDate').value;

    if (fromDate && toDate && fromDate > toDate) {
        alert('From Date must not be after To Date');
        return;
    }

    try {
        const response = await API.exportReport(userId, fromDate, toDate);
        const blob = await response.blob();
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `employee-report-${userId}-${fromDate}-to-${toDate}.xlsx`;
        a.click();
        window.URL.revokeObjectURL(url);
    } catch (error) {
        alert('Failed to export report');
    }
}

async function handleLogout() {

    // Session is about to be invalidated by the explicit logout below, so
    // suppress the desktop auto-logout beacon that would otherwise also
    // fire when this navigation away from the dashboard triggers pagehide.
    autoLogoutBeaconSent = true;

    // Stop location updates
    stopLocationInterval();

    // Stop the Online/Offline heartbeat
    stopHeartbeat();

    // Stop notification polling
    stopNotificationPolling();
    stopAdminNotificationPolling();

    if (adminDataInterval) {
        clearInterval(adminDataInterval);
        adminDataInterval = null;
    }

    try {

        await API.logout();

    } catch (e) {
        // Continue to login page even if logout API fails
    }

    window.location.href = '/index.html';
}
function initDarkMode() {

    const savedTheme =
        localStorage.getItem("theme");

    if (savedTheme === "dark") {

        document.body.classList.add("dark-mode");

        document
            .querySelector("#darkModeBtn i")
            .className = "bi bi-sun-fill";

    }

}
function toggleDarkMode() {

    document.body.classList.toggle("dark-mode");

    const icon =
        document.querySelector("#darkModeBtn i");

    if (document.body.classList.contains("dark-mode")) {

        localStorage.setItem("theme", "dark");

        icon.className = "bi bi-sun-fill";

    } else {

        localStorage.setItem("theme", "light");

        icon.className = "bi bi-moon-stars";

    }

}

function formatDistance(distance) {
    const value = Number(distance);
    return Number.isFinite(value) ? value.toFixed(2) : '0.00';
}

async function toggleNearbyPlaces() {
    // Show Nearby Colleges only works while tracking is ON (button is also
    // disabled in this state, this is a safety guard).
    if (!trackingEnabled) return;

    const isShown = MapManager.toggleNearbyPlaces();
    const btn = document.getElementById('toggleNearbyBtn');
    if (btn) {
        btn.innerHTML = isShown ?
            '<i class="bi bi-building me-1"></i>Hide Nearby Colleges' :
            '<i class="bi bi-building me-1"></i>Show Nearby Colleges';
    }

    if (isShown && lastEmployeeLocation) {
        // Draw the geofence circle and reload nearby colleges inside it
        await refreshNearbyCollegesAndCircle(lastEmployeeLocation.lat, lastEmployeeLocation.lng);
    } else if (!isShown) {
        // Turning off: remove the circle and nearby college markers
        MapManager.clearNearbyPlaces();
    }
}

async function toggleAdminNearbyPlaces() {

    const isShown = MapManager.toggleNearbyPlaces();

    const btn = document.getElementById('adminToggleNearbyBtn');

    if (btn) {
        btn.innerHTML = isShown
            ? '<i class="bi bi-building me-1"></i>Hide Nearby Colleges'
            : '<i class="bi bi-building me-1"></i>Show Nearby Colleges';
    }

    if (!isShown) {

        MapManager.clearNearbyPlaces();
        return;

    }

    try {

        if (focusedEmployee) {

            await refreshAdminGeofenceAndColleges();
            return;

        }

        const nearbyRes = await API.getAdminNearbyPlaces();

        if (nearbyRes && nearbyRes.success) {

            MapManager.updateAdminNearbyPlaces(nearbyRes.data);

        }

    } catch (error) {

        console.error("Error loading nearby places:", error);

    }

}
function startNotificationPolling() {

    stopNotificationPolling();

    notificationInterval = setInterval(() => {

        loadNotifications();

    }, 5000);

}
function stopNotificationPolling() {

    if (notificationInterval) {

        clearInterval(notificationInterval);

        notificationInterval = null;

    }

}
async function loadNotifications() {

    try {

        const response = await API.getNotifications();

        if (!response.success) return;

        renderNotifications(response.data);

    } catch (error) {

        console.error("Notification Error:", error);

    }

}
let selectedNotification = null;

function renderNotifications(notifications) {

    const badge = document.getElementById("notificationBadge");
    const container = document.getElementById("notificationItems");

    if (!badge || !container) return;

    const unreadCount = notifications.filter(n => !n.read).length;

    badge.textContent = unreadCount;

    if (unreadCount > 0) {
        badge.classList.remove("d-none");
    } else {
        badge.classList.add("d-none");
    }

    if (notifications.length === 0) {

        container.innerHTML = `
            <p class="text-center text-muted my-4">
                No Notifications
            </p>
        `;

        return;
    }

    container.innerHTML = notifications.map(n => `

        <div class="dropdown-item notification-item ${n.read ? "" : "fw-bold"}"
             data-id="${n.notificationId}">

            <div>${n.title}</div>

            <small>${n.message}</small>

            <br>

            <small class="text-muted">
                ${new Date(n.createdAt).toLocaleString()}
            </small>

        </div>

    `).join("");

    document.querySelectorAll(".notification-item").forEach(item => {

        item.addEventListener("click", () => {

            const id = Number(item.dataset.id);

            selectedNotification =
                notifications.find(n => n.notificationId === id);

            openNotificationModal(selectedNotification);

        });

    });

}
function openNotificationModal(notification) {
    if (!notification) return;

    document.getElementById("modalNotificationTitle").innerText =
        notification.title;

    document.getElementById("modalNotificationMessage").innerText =
        notification.message;

    document.getElementById("modalNotificationTime").innerText =
        new Date(notification.createdAt).toLocaleString();

    document.getElementById("modalNotificationStatus").innerText =
        notification.read ? "Read" : "Unread";

    const modal = new bootstrap.Modal(
        document.getElementById("notificationModal")
    );

    modal.show();

}
async function markAllNotificationsAsRead() {

    try {

        const response = await API.markAllNotificationsAsRead();

        if (response.success) {

            await loadNotifications();

        }

    } catch (error) {

        console.error("Mark All Read Error:", error);

    }

}