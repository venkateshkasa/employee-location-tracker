let currentUser = null;
let locationInterval = null;
const AUTO_UPDATE_MINUTES = 10;

document.addEventListener('DOMContentLoaded', async () => {
    try {
        const response = await API.getCurrentUser();
        if (!response.success) {
            window.location.href = '/index.html';
            return;
        }
        currentUser = response.data;
        initializeDashboard();
    } catch (e) {
        window.location.href = '/index.html';
    }

    document.getElementById('logoutBtn').addEventListener('click', handleLogout);
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

    // Admin can also see employee section if they want - but requirement says admin panel in same dashboard
    // Admin users won't track GPS themselves typically
}

async function initEmployeeDashboard() {
    MapManager.initEmployeeMap();
    document.getElementById('refreshLocationBtn').addEventListener('click', captureAndSaveLocation);

    await loadEmployeeData();
    await captureAndSaveLocation();

    locationInterval = setInterval(captureAndSaveLocation, AUTO_UPDATE_MINUTES * 60 * 1000);
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
            document.getElementById('todayDistance').textContent = `${distanceRes.data.distanceKm} km`;
        }

        if (stopsRes && stopsRes.success) {
            renderStopHistory(stopsRes.data);
        }

        if (activitiesRes && activitiesRes.success) {
            renderActivityTimeline(activitiesRes.data);
        }

        if (historyRes && historyRes.success && historyRes.data.length > 0) {
            const routePoints = historyRes.data.map(loc => ({
                latitude: parseFloat(loc.latitude),
                longitude: parseFloat(loc.longitude)
            }));
            MapManager.drawEmployeeRoute(routePoints);
        }
    } catch (error) {
        console.error('Error loading employee data:', error);
    }
}

function updateEmployeeUI(location) {
    const lat = parseFloat(location.latitude);
    const lng = parseFloat(location.longitude);

    document.getElementById('currentStatus').innerHTML =
        `<span class="status-badge status-${location.status.toLowerCase()}">${location.status}</span>`;
    document.getElementById('currentLat').textContent = lat.toFixed(6);
    document.getElementById('currentLng').textContent = lng.toFixed(6);
    document.getElementById('lastUpdated').textContent = `Last Updated: ${location.locationTime}`;

    if (location.todayDistanceKm !== undefined) {
        document.getElementById('todayDistance').textContent = `${location.todayDistanceKm} km`;
    }

    const popup = MapManager.createPopupContent(
        location.employeeName || currentUser.name,
        location.locationTime,
        lat.toFixed(6),
        lng.toFixed(6),
        location.status
    );
    MapManager.updateEmployeeMarker(lat, lng, popup);
}

async function captureAndSaveLocation() {
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
                    btn.disabled = false;
                    btn.innerHTML = '<i class="bi bi-arrow-clockwise me-1"></i>Refresh Location';
                }
            }
        },
        (error) => {
            console.error('Geolocation error:', error);
            if (btn) {
                btn.disabled = false;
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
        const typeClass = activity.activityType.toLowerCase().replace('_', '');
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
            <small class="text-muted">Location: ${stop.latitude.toFixed(4)}, ${stop.longitude.toFixed(4)}</small>
        </div>
    `).join('');
}

function formatActivityType(type) {
    const types = {
        'LOGIN': 'Login',
        'LOGOUT': 'Logout',
        'LOCATION_UPDATE': 'Location Update',
        'STOP': 'Stop Detected'
    };
    return types[type] || type;
}

async function initAdminDashboard() {
    MapManager.initAdminMap();
    document.getElementById('reportDate').value = new Date().toISOString().split('T')[0];

    document.getElementById('generateReportBtn').addEventListener('click', generateReport);
    document.getElementById('exportExcelBtn').addEventListener('click', exportReport);
    document.getElementById('printReportBtn').addEventListener('click', () => window.print());

    await loadAdminData();
    setInterval(loadAdminData, 60000);
}

async function loadAdminData() {
    try {
        const [summaryRes, employeesRes, liveRes] = await Promise.all([
            API.getAdminSummary(),
            API.getEmployees(),
            API.getLiveLocations()
        ]);

        if (summaryRes && summaryRes.success) {
            document.getElementById('totalEmployees').textContent = summaryRes.data.totalEmployees;
            document.getElementById('onlineEmployees').textContent = summaryRes.data.onlineEmployees;
            document.getElementById('offlineEmployees').textContent = summaryRes.data.offlineEmployees;
        }

        if (employeesRes && employeesRes.success) {
            renderEmployeeTable(employeesRes.data);
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
        }
    } catch (error) {
        console.error('Error loading admin data:', error);
    }
}

function renderEmployeeTable(employees) {
    const tbody = document.getElementById('employeeTableBody');
    if (!employees || employees.length === 0) {
        tbody.innerHTML = '<tr><td colspan="4" class="text-center text-muted">No employees found</td></tr>';
        return;
    }

    tbody.innerHTML = employees.map(emp => `
        <tr>
            <td>
                <strong>${emp.name}</strong><br>
                <small class="text-muted">${emp.employeeId}</small>
            </td>
            <td><span class="status-badge status-${emp.trackingStatus.toLowerCase()}">${emp.trackingStatus}</span></td>
            <td>${emp.todayDistanceKm} km</td>
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
            const response = await API.getEmployee(id);
            if (response.success) {
                MapManager.focusOnEmployee(response.data);
            }
        });
    });
}

function populateReportSelect(employees) {
    const select = document.getElementById('reportEmployeeSelect');
    select.innerHTML = employees.map(emp =>
        `<option value="${emp.userId}">${emp.name} (${emp.employeeId})</option>`
    ).join('');
}

async function generateReport() {
    const userId = document.getElementById('reportEmployeeSelect').value;
    const date = document.getElementById('reportDate').value;
    const container = document.getElementById('reportResult');

    try {
        const response = await API.getReport(userId, date);
        if (response.success) {
            const report = response.data;
            container.innerHTML = `
                <div class="report-summary">
                    <h6 class="fw-bold mb-3">Report: ${report.employeeName}</h6>
                    <div class="row">
                        <div class="col-6"><small class="text-muted">Date</small><br><strong>${report.reportDate}</strong></div>
                        <div class="col-6"><small class="text-muted">Distance</small><br><strong>${report.totalDistanceKm} km</strong></div>
                        <div class="col-6 mt-2"><small class="text-muted">Stops</small><br><strong>${report.totalStops}</strong></div>
                        <div class="col-6 mt-2"><small class="text-muted">Updates</small><br><strong>${report.totalLocationUpdates}</strong></div>
                    </div>
                </div>
            `;
        }
    } catch (error) {
        container.innerHTML = '<div class="alert alert-danger">Failed to generate report</div>';
    }
}

async function exportReport() {
    const userId = document.getElementById('reportEmployeeSelect').value;
    const date = document.getElementById('reportDate').value;

    try {
        const response = await API.exportReport(userId, date);
        const blob = await response.blob();
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `employee-report-${userId}-${date}.xlsx`;
        a.click();
        window.URL.revokeObjectURL(url);
    } catch (error) {
        alert('Failed to export report');
    }
}

async function handleLogout() {
    if (locationInterval) {
        clearInterval(locationInterval);
    }
    try {
        await API.logout();
    } catch (e) {
        // Continue to login page
    }
    window.location.href = '/index.html';
}
