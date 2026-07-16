let currentUser = null;
let locationInterval = null;
let trackingEnabled = false;
let notificationInterval = null;
let adminNotificationInterval = null;
let distanceChartInstance = null;
const AUTO_UPDATE_MINUTES = 10;

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
    } catch (e) {
        window.location.href = '/index.html';
    }

    document.getElementById('logoutBtn').addEventListener('click', handleLogout);
    initDarkMode();

document
    .getElementById("darkModeBtn")
    .addEventListener("click", toggleDarkMode);
});

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

    // Tracking OFF
    document.getElementById('trackingOffBtn')
        .addEventListener('click', () => {

            if (trackingEnabled) {
                toggleTracking(false);
            }

        });

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
    // Load Dashboard
    // ===========================

    await loadEmployeeData();

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
    // Start Tracking
    // ===========================

    if (trackingEnabled) {

        await captureAndSaveLocation();

        startLocationInterval();

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

        return `
            <div class="stop-history-card">
                <div class="stop-history-card-header">
                    <span class="stop-history-number">Stop ${index + 1}</span>
                </div>
                <p class="stop-history-address">📍 ${address}</p>
                <div class="stop-history-grid">
                    <div><small class="text-muted">Latitude</small><br><strong>${Number(stop.latitude).toFixed(6)}</strong></div>
                    <div><small class="text-muted">Longitude</small><br><strong>${Number(stop.longitude).toFixed(6)}</strong></div>
                    <div><small class="text-muted">Start Time</small><br><strong>${stop.startTime || '-'}</strong></div>
                    <div><small class="text-muted">End Time</small><br><strong>${stop.endTime || 'Ongoing'}</strong></div>
                    <div><small class="text-muted">Duration</small><br><strong>${stop.duration || 'Calculating...'}</strong></div>
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

async function loadEmployeeData() {
    try {
        const [locationRes, distanceRes, stopsRes, activitiesRes, historyRes] = await Promise.all([
            API.getCurrentLocation().catch(() => null),
            API.getTodayDistance(),
            API.getTodayStops(),
            API.getTodayActivities(),
            API.getLocationHistory(new Date().toISOString().split('T')[0]).catch(() => null)
        ]);

        if (locationRes && locationRes.success) {
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

async function captureAndSaveLocation() {
    if (!trackingEnabled) {
        return;
    }

    if (!navigator.geolocation) {
        alert('Geolocation is not supported by your browser.');
        return;
    }

    const btn = document.getElementById('refreshLocationBtn');
    if (btn) {
        btn.disabled = true;
        btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Updating...';
    }

    navigator.geolocation.getCurrentPosition(
        async (position) => {
            try {
                if (!trackingEnabled) return;

                const response = await API.saveLocation(
                    position.coords.latitude,
                    position.coords.longitude,
                    position.coords.accuracy
                );

                if (response.success) {
                    updateEmployeeUI(response.data);
                    await loadEmployeeData();
                }
            } catch (error) {
                console.error('Error saving location:', error);
            } finally {
                if (btn) {
                    btn.disabled = !trackingEnabled;
                    btn.innerHTML = '<i class="bi bi-arrow-clockwise me-1"></i>Refresh Location';
                }
            }
        },
        (error) => {
            console.error('Geolocation error:', error);
            if (btn) {
                btn.disabled = !trackingEnabled;
                btn.innerHTML = '<i class="bi bi-arrow-clockwise me-1"></i>Refresh Location';
            }
            if (error.code === 1) {
                alert('GPS permission denied. Please enable location access to track your position.');
            }
        },
        { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 }
    );
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

    container.innerHTML = stops.map(stop => `
        <div class="stop-item">
            <strong>${stop.startTime}</strong> - ${stop.endTime || 'Ongoing'}<br>
            <small class="text-muted">Duration: ${stop.duration || 'Calculating...'}</small><br>
            <small class="text-muted">Location: ${Number(stop.latitude).toFixed(4)}, ${Number(stop.longitude).toFixed(4)}</small>
        </div>
    `).join('');
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
        'STOP': 'Stop Detected'
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

    await loadAdminData();
    setInterval(loadAdminData, 60000);

    // Admin Notifications card (e.g. employees entering nearby colleges).
    // Polls independently of loadAdminData so new alerts show up quickly.
    await loadAdminNotifications();
    startAdminNotificationPolling();
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

    const filtered = allEmployees.filter(emp => {
        const matchesSearch = !searchTerm ||
            (emp.name && emp.name.toLowerCase().includes(searchTerm)) ||
            (emp.employeeId && emp.employeeId.toLowerCase().includes(searchTerm));

        if (!matchesSearch) return false;

        if (!statusValue) return true;

        const trackingStatus = (emp.trackingStatus || '').toUpperCase();

        if (statusValue === 'online') return trackingStatus !== 'OFFLINE';
        if (statusValue === 'offline') return trackingStatus === 'OFFLINE';
        if (statusValue === 'inside') return Boolean(emp.insideOffice);
        if (statusValue === 'outside') return !emp.insideOffice;

        return true;
    });

    renderEmployeeTable(filtered);
}

async function loadAdminData() {
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
            applyEmployeeFilters();
            populateReportSelect(employeesRes.data);
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
    }
}

function renderEmployeeTable(employees) {
    const tbody = document.getElementById('employeeTableBody');
    if (!employees || employees.length === 0) {
        tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted">No employees found</td></tr>';
        return;
    }

    tbody.innerHTML = employees.map(emp => `
        <tr>
            <td>
                <strong>${emp.name}</strong><br>
                <small class="text-muted">${emp.employeeId}</small>
            </td>
            <td><span class="status-badge status-${emp.trackingStatus.toLowerCase()}">${emp.trackingStatus}</span></td>
            <td>${emp.officeName ? emp.officeName : (emp.insideOffice ? 'Inside Office' : '-')}</td>
            <td>${emp.lastSeen || emp.lastUpdated || '-'}</td>
            <td>${formatDistance(emp.todayDistanceKm)} km</td>
            <td>
                <button class="btn btn-sm btn-outline-primary view-employee-btn" data-id="${emp.userId}">
                    <i class="bi bi-eye"></i> View
                </button>
            </td>
        </tr>
    `).join('');

    document.querySelectorAll('.view-employee-btn').forEach(btn => {
        btn.addEventListener('click', async () => {
            const id = btn.dataset.id;
            try {
                const response = await API.getEmployee(id);
                if (response.success) {
                    const shown = MapManager.focusOnEmployee(response.data);
                    if (!shown) {
                        alert(`${response.data.name} has no saved location yet.`);
                        return;
                    }

                    focusedEmployee = response.data;
                    await refreshAdminGeofenceAndColleges();
                } else {
                    alert(response.message || 'Unable to load employee location.');
                }
            } catch (error) {
                console.error('Error loading employee location:', error);
                alert('Unable to load employee location.');
            }
        });
    });
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
    select.innerHTML = employees.map(emp =>
        `<option value="${emp.userId}">${emp.name} (${emp.employeeId})</option>`
    ).join('');
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

    // Stop location updates
    stopLocationInterval();

    // Stop notification polling
    stopNotificationPolling();
    stopAdminNotificationPolling();

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