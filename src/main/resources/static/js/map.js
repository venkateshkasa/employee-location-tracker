const MapManager = {
    employeeMap: null,
    adminMap: null,
    employeeMarker: null,
    adminMarkers: {},
    routePolyline: null,
    selectedEmployeeMarker: null,
    selectedEmployeeId: null,

    initEmployeeMap(lat, lng) {
        if (this.employeeMap) {
            this.employeeMap.remove();
        }

        const defaultLat = lat || 28.6139;
        const defaultLng = lng || 77.2090;

        this.employeeMap = L.map('map').setView([defaultLat, defaultLng], 14);

        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
            maxZoom: 19
        }).addTo(this.employeeMap);

        this.employeeMarker = L.marker([defaultLat, defaultLng]).addTo(this.employeeMap);
        this.routePolyline = L.polyline([], { color: '#2563eb', weight: 4, opacity: 0.7 }).addTo(this.employeeMap);
    },

    initAdminMap(lat, lng) {
        if (this.adminMap) {
            this.adminMap.remove();
        }

        const defaultLat = lat || 20.5937;
        const defaultLng = lng || 78.9629;

        this.adminMap = L.map('adminMap').setView([defaultLat, defaultLng], 5);

        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
            maxZoom: 19
        }).addTo(this.adminMap);

        this.adminMarkers = {};
        this.selectedEmployeeMarker = null;
        this.selectedEmployeeId = null;
    },

    updateEmployeeMarker(lat, lng, popupContent) {
        if (!this.employeeMap) return;

        const position = [lat, lng];
        if (this.employeeMarker) {
            this.employeeMarker.setLatLng(position);
            if (popupContent) {
                this.employeeMarker.bindPopup(popupContent).openPopup();
            }
        } else {
            this.employeeMarker = L.marker(position).addTo(this.employeeMap);
            if (popupContent) {
                this.employeeMarker.bindPopup(popupContent);
            }
        }
        this.employeeMap.setView(position, 15);
    },

    drawEmployeeRoute(locations) {
        if (!this.routePolyline || !locations || locations.length === 0) return;

        const points = locations.map(loc => [loc.latitude, loc.longitude]);
        this.routePolyline.setLatLngs(points);
    },

    updateAdminMarkers(employees) {
        if (!this.adminMap) return;

        Object.values(this.adminMarkers).forEach(marker => this.adminMap.removeLayer(marker));
        this.adminMarkers = {};

        // When the admin has focused on one employee (via the View button), keep
        // showing only that employee on every refresh instead of snapping back to
        // every marker - use "Show All" to return to the full view.
        const visibleEmployees = this.selectedEmployeeId
            ? employees.filter(emp => String(emp.userId) === String(this.selectedEmployeeId))
            : employees;

        visibleEmployees.forEach(emp => {
            if (emp.latitude != null && emp.longitude != null) {
                if (this.selectedEmployeeId && String(emp.userId) === String(this.selectedEmployeeId) && this.selectedEmployeeMarker) {
                    // Keep the highlighted marker itself live rather than layering a
                    // second plain marker on top of it at the same coordinates.
                    this.selectedEmployeeMarker.setLatLng([emp.latitude, emp.longitude]);
                    return;
                }

                const popup = `
                    <strong>${emp.employeeName || emp.name}</strong><br>
                    Status: ${emp.status || emp.trackingStatus}<br>
                    Lat: ${emp.latitude}<br>
                    Lng: ${emp.longitude}<br>
                    Last Updated: ${emp.lastUpdated || emp.locationTime || 'N/A'}
                `;
                const marker = L.marker([emp.latitude, emp.longitude])
                    .bindPopup(popup)
                    .addTo(this.adminMap);
                this.adminMarkers[emp.userId] = marker;
            }
        });
    },

    focusOnEmployee(employee) {
        if (!this.adminMap) return false;
        if (employee.latitude == null || employee.longitude == null) return false;

        this.selectedEmployeeId = employee.userId;

        // Drop any plain marker already drawn for this employee so it isn't
        // duplicated underneath the highlighted marker below.
        if (this.adminMarkers[employee.userId]) {
            this.adminMap.removeLayer(this.adminMarkers[employee.userId]);
            delete this.adminMarkers[employee.userId];
        }

        const lat = employee.latitude;
        const lng = employee.longitude;
        const now = new Date().toLocaleString();

        const popup = `
            <strong>${employee.name}</strong><br>
            Time: ${now}<br>
            Lat: ${lat}<br>
            Lng: ${lng}<br>
            Status: ${employee.trackingStatus}
        `;

        if (this.selectedEmployeeMarker) {
            this.adminMap.removeLayer(this.selectedEmployeeMarker);
        }

        this.selectedEmployeeMarker = L.marker([lat, lng], {
            icon: L.icon({
                iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-red.png',
                shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-shadow.png',
                iconSize: [25, 41],
                iconAnchor: [12, 41],
                popupAnchor: [1, -34],
                shadowSize: [41, 41]
            })
        }).bindPopup(popup).addTo(this.adminMap);

        this.adminMap.setView([lat, lng], 15);
        this.selectedEmployeeMarker.openPopup();

        document.getElementById('mapTitle').textContent = `Viewing: ${employee.name}`;
        return true;
    },

    showAllEmployees() {
        this.selectedEmployeeId = null;
        if (this.selectedEmployeeMarker && this.adminMap) {
            this.adminMap.removeLayer(this.selectedEmployeeMarker);
        }
        this.selectedEmployeeMarker = null;

        const titleEl = document.getElementById('mapTitle');
        if (titleEl) {
            titleEl.textContent = 'Employee Live Locations';
        }
    },

    createPopupContent(name, time, lat, lng, status) {
        return `
            <strong>${name}</strong><br>
            Time: ${time}<br>
            Latitude: ${lat}<br>
            Longitude: ${lng}<br>
            Status: ${status}
        `;
    }
};