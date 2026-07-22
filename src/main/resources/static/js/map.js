const MapManager = {
    employeeMap: null,
    adminMap: null,
    employeeMarker: null,
    adminMarkers: {},
    nearbyPlaceMarkers: {},
    adminNearbyPlaceMarkers: {},
    routePolyline: null,
    animatedRouteMarker: null,
    routeAnimationTimer: null,
    heatLayers: [],
    selectedEmployeeId: null,
    showNearbyPlaces: false,
    employeeGeofenceCircle: null,
    adminGeofenceCircle: null,

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

        this.employeeMarker = L.marker([defaultLat, defaultLng], { icon: this.iconForStatus('ONLINE') }).addTo(this.employeeMap);
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
        this.selectedEmployeeId = null;
    },

    updateEmployeeMarker(lat, lng, popupContent, status = 'ONLINE') {
        if (!this.employeeMap) return;

        const position = [Number(lat), Number(lng)];
        if (!Number.isFinite(position[0]) || !Number.isFinite(position[1])) return;

        if (this.employeeMarker) {
            this.employeeMarker.setLatLng(position).setIcon(this.iconForStatus(status));
        } else {
            this.employeeMarker = L.marker(position, { icon: this.iconForStatus(status) }).addTo(this.employeeMap);
        }

        if (popupContent) {
            this.employeeMarker.bindPopup(popupContent).openPopup();
        }
        this.employeeMap.setView(position, 15);
    },

    drawEmployeeRoute(locations) {
        if (!this.routePolyline || !locations) return;

        const points = locations
            .map(loc => [Number(loc.latitude), Number(loc.longitude)])
            .filter(point => Number.isFinite(point[0]) && Number.isFinite(point[1]));
        this.routePolyline.setLatLngs(points);
        this.animateRoute(points);
    },

    animateRoute(points) {
        if (!this.employeeMap || !points || points.length === 0) return;
        if (this.routeAnimationTimer) {
            clearInterval(this.routeAnimationTimer);
        }
        if (!this.animatedRouteMarker) {
            this.animatedRouteMarker = L.circleMarker(points[0], {
                radius: 7,
                color: '#111827',
                fillColor: '#facc15',
                fillOpacity: 1
            }).addTo(this.employeeMap);
        }
        let index = 0;
        this.routeAnimationTimer = setInterval(() => {
            this.animatedRouteMarker.setLatLng(points[index]);
            index = (index + 1) % points.length;
        }, 700);
    },

    updateAdminMarkers(employees) {
        if (!this.adminMap) return;

        const activeIds = new Set();
        employees.forEach(emp => {
            const lat = Number(emp.latitude);
            const lng = Number(emp.longitude);
            if (!Number.isFinite(lat) || !Number.isFinite(lng)) return;

            const employeeId = String(emp.userId);
            activeIds.add(employeeId);
            const popup = this.createPopupContent(
                emp.employeeName || emp.name,
                emp.lastUpdated || emp.locationTime || 'N/A',
                lat.toFixed(6),
                lng.toFixed(6),
                emp.status || emp.trackingStatus || 'OFFLINE'
            );

            const status = emp.insideOffice ? 'INSIDE_OFFICE' : (emp.status || emp.trackingStatus || 'OFFLINE');
            if (this.adminMarkers[employeeId]) {
                this.adminMarkers[employeeId].setLatLng([lat, lng]).setIcon(this.iconForStatus(status)).bindPopup(popup);
            } else {
                this.adminMarkers[employeeId] = L.marker([lat, lng], { icon: this.iconForStatus(status) }).bindPopup(popup).addTo(this.adminMap);
            }
        });

        Object.keys(this.adminMarkers).forEach(employeeId => {
            if (!activeIds.has(employeeId)) {
                this.adminMap.removeLayer(this.adminMarkers[employeeId]);
                delete this.adminMarkers[employeeId];
            }
        });
        this.drawHeatMap(employees);
    },

    focusOnEmployee(employee) {
        if (!this.adminMap) return false;

        const lat = Number(employee.latitude);
        const lng = Number(employee.longitude);
        if (!Number.isFinite(lat) || !Number.isFinite(lng)) return false;

        const employeeId = String(employee.userId);
        const popup = this.createPopupContent(
            employee.name,
            employee.lastUpdated || 'N/A',
            lat.toFixed(6),
            lng.toFixed(6),
            employee.trackingStatus || 'OFFLINE',
            employee.distanceFromOfficeKm
        );

        this.selectedEmployeeId = employeeId;
        this.updateSelectedMarker(employeeId, lat, lng, popup);
        this.adminMap.setView([lat, lng], 16);
        this.adminMarkers[employeeId].openPopup();

        document.getElementById('mapTitle').textContent = `Viewing: ${employee.name}`;
        return true;
    },

    updateSelectedMarker(employeeId, lat, lng, popup) {
        Object.entries(this.adminMarkers).forEach(([id, marker]) => {
            marker.setIcon(id === employeeId ? this.selectedIcon() : this.defaultIcon());
        });

        if (this.adminMarkers[employeeId]) {
            this.adminMarkers[employeeId].setLatLng([lat, lng]).bindPopup(popup).setIcon(this.selectedIcon());
        } else {
            this.adminMarkers[employeeId] = L.marker([lat, lng], { icon: this.selectedIcon() })
                .bindPopup(popup)
                .addTo(this.adminMap);
        }
    },

    defaultIcon() {
        return this.iconForStatus('ONLINE');
    },

    iconForStatus(status) {
        const normalized = String(status || '').toUpperCase();
        let color = 'green';
        if (normalized.includes('OFFLINE')) color = 'red';
        if (normalized.includes('STOP') || normalized.includes('IDLE')) color = 'yellow';
        if (normalized.includes('INSIDE')) color = 'blue';
        return L.icon({
            iconUrl: `https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-${color}.png`,
            shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-shadow.png',
            iconSize: [25, 41],
            iconAnchor: [12, 41],
            popupAnchor: [1, -34],
            shadowSize: [41, 41]
        });
    },

    selectedIcon() {
        return L.icon({
            iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-red.png',
            shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-shadow.png',
            iconSize: [25, 41],
            iconAnchor: [12, 41],
            popupAnchor: [1, -34],
            shadowSize: [41, 41]
        });
    },

   createPopupContent(name, time, lat, lng, status, distanceFromOfficeKm) {
    const distanceLine = (distanceFromOfficeKm === undefined || distanceFromOfficeKm === null)
        ? ''
        : `<br>Distance from Office: ${Number(distanceFromOfficeKm).toFixed(2)} km`;
    return `
        <strong>${name}</strong><br>
        Time: ${time}<br>
        Latitude: ${lat}<br>
        Longitude: ${lng}<br>
        Status: ${status}${distanceLine}
    `;
},

drawHeatMap(employees) {
    if (!this.adminMap) return;
    this.heatLayers.forEach(layer => this.adminMap.removeLayer(layer));
    this.heatLayers = [];
    employees.forEach(emp => {
        const lat = Number(emp.latitude);
        const lng = Number(emp.longitude);
        if (!Number.isFinite(lat) || !Number.isFinite(lng)) return;
        const radius = emp.trackingStatus === 'STOPPED' ? 42 : 26;
        const layer = L.circle([lat, lng], {
            radius,
            color: '#ef4444',
            fillColor: '#ef4444',
            fillOpacity: 0.18,
            weight: 0
        }).addTo(this.adminMap);
        this.heatLayers.push(layer);
    });
},

clearEmployeeMarker() {
    if (this.employeeMarker && this.employeeMap) {
        this.employeeMap.removeLayer(this.employeeMarker);
        this.employeeMarker = null;
    }
},

toggleNearbyPlaces() {
    this.showNearbyPlaces = !this.showNearbyPlaces;
    this.updateNearbyPlacesVisibility();
    return this.showNearbyPlaces;
},

updateNearbyPlacesVisibility() {
    if (!this.employeeMap) return;
    
    Object.values(this.nearbyPlaceMarkers).forEach(marker => {
        if (this.showNearbyPlaces) {
            marker.addTo(this.employeeMap);
        } else {
            this.employeeMap.removeLayer(marker);
        }
    });
},

updateNearbyPlaces(places) {
    if (!this.employeeMap || !this.showNearbyPlaces) return;

    // Remove old markers
    Object.values(this.nearbyPlaceMarkers).forEach(marker => {
        this.employeeMap.removeLayer(marker);
    });
    this.nearbyPlaceMarkers = {};

    // Add new markers
    places.forEach(place => {
        const lat = Number(place.latitude);
        const lng = Number(place.longitude);
        if (!Number.isFinite(lat) || !Number.isFinite(lng)) return;

        const icon = this.iconForPlaceType(place.placeType);
        const popup = this.createNearbyPlacePopup(place);
        
        const marker = L.marker([lat, lng], { icon })
            .bindPopup(popup)
            .addTo(this.employeeMap);
        
        this.nearbyPlaceMarkers[place.placeId] = marker;
    });
},

updateAdminNearbyPlaces(places) {
    if (!this.adminMap || !this.showNearbyPlaces) return;

    // Remove old markers
    Object.values(this.adminNearbyPlaceMarkers).forEach(marker => {
        this.adminMap.removeLayer(marker);
    });
    this.adminNearbyPlaceMarkers = {};

    // Add new markers
    places.forEach(place => {
        const lat = Number(place.latitude);
        const lng = Number(place.longitude);
        if (!Number.isFinite(lat) || !Number.isFinite(lng)) return;

        const icon = this.iconForPlaceType(place.placeType);
        const popup = this.createAdminNearbyPlacePopup(place);
        
        const marker = L.marker([lat, lng], { icon })
            .bindPopup(popup)
            .addTo(this.adminMap);
        
        this.adminNearbyPlaceMarkers[place.placeId] = marker;
    });
},

iconForPlaceType(placeType) {
    const type = String(placeType || '').toLowerCase();
    let color = 'blue';
    
    if (type.includes('university')) color = 'red';
    else if (type.includes('engineering')) color = 'green';
    else if (type.includes('degree')) color = 'yellow';
    else if (type.includes('medical')) color = 'purple';
    else if (type.includes('polytechnic')) color = 'orange';
    
    return L.icon({
        iconUrl: `https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-${color}.png`,
        shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-shadow.png',
        iconSize: [25, 41],
        iconAnchor: [12, 41],
        popupAnchor: [1, -34],
        shadowSize: [41, 41]
    });
},

createNearbyPlacePopup(place) {
    const distanceKm = (place.distance / 1000).toFixed(2);
    return `
        <strong>${place.placeName}</strong><br>
        Type: ${place.placeType}<br>
        Distance: ${distanceKm} KM<br>
        ${place.address ? `Address: ${place.address}<br>` : ''}
        Entered: ${new Date(place.enteredTime).toLocaleString()}
    `;
},

createAdminNearbyPlacePopup(place) {
    const distanceKm = (place.distance / 1000).toFixed(2);
    return `
        <strong>${place.placeName}</strong><br>
        Type: ${place.placeType}<br>
        Distance: ${distanceKm} KM<br>
        ${place.address ? `Address: ${place.address}<br>` : ''}
        Employee ID: ${place.userId}<br>
        Entered: ${new Date(place.enteredTime).toLocaleString()}
    `;
},

/**
 * Draws (or moves) the geofence circle used by the Radius dropdown feature.
 * Always removes any existing circle on that map first, so repeated calls
 * (radius change, employee movement, re-focusing an employee) never leave
 * behind a stale/duplicate circle.
 */
drawGeofenceCircle(isAdmin, lat, lng, radiusMeters) {
    const map = isAdmin ? this.adminMap : this.employeeMap;
    if (!map) return;

    this.removeGeofenceCircle(isAdmin);

    const circle = L.circle([lat, lng], {
        radius: radiusMeters,
        color: '#2563eb',
        fillColor: '#2563eb',
        fillOpacity: 0.08,
        weight: 2,
        dashArray: '6, 6'
    }).addTo(map);

    if (isAdmin) {
        this.adminGeofenceCircle = circle;
    } else {
        this.employeeGeofenceCircle = circle;
    }
},

removeGeofenceCircle(isAdmin) {
    if (isAdmin) {
        if (this.adminGeofenceCircle && this.adminMap) {
            this.adminMap.removeLayer(this.adminGeofenceCircle);
        }
        this.adminGeofenceCircle = null;
    } else {
        if (this.employeeGeofenceCircle && this.employeeMap) {
            this.employeeMap.removeLayer(this.employeeGeofenceCircle);
        }
        this.employeeGeofenceCircle = null;
    }
},

clearNearbyPlaces() {
    if (this.employeeMap) {
        Object.values(this.nearbyPlaceMarkers).forEach(marker => {
            this.employeeMap.removeLayer(marker);
        });
        this.nearbyPlaceMarkers = {};
    }
    
    if (this.adminMap) {
        Object.values(this.adminNearbyPlaceMarkers).forEach(marker => {
            this.adminMap.removeLayer(marker);
        });
        this.adminNearbyPlaceMarkers = {};
    }

    this.removeGeofenceCircle(false);
    this.removeGeofenceCircle(true);
}
};