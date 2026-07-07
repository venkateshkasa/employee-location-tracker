const API = {
    baseUrl: '',

    getCsrfToken() {
        const match = document.cookie.match(/XSRF-TOKEN=([^;]+)/);
        return match ? decodeURIComponent(match[1]) : null;
    },

    async request(url, options = {}) {
        const headers = {
            'Content-Type': 'application/json',
            ...options.headers
        };

        const csrfToken = this.getCsrfToken();
        if (csrfToken && options.method && options.method !== 'GET') {
            headers['X-XSRF-TOKEN'] = csrfToken;
        }

        const response = await fetch(this.baseUrl + url, {
            ...options,
            headers,
            credentials: 'include'
        });

        // /api/auth/me is used to check whether a session already exists, and
        // /api/auth/login is the login attempt itself - a 401 from either of these
        // simply means "not logged in" / "bad credentials", not an expired session.
        // Redirecting here would otherwise send the login page back to itself,
        // causing an infinite reload loop for anyone who isn't logged in yet.
        const isAuthCheckOrLogin = url.includes('/api/auth/me') || url.includes('/api/auth/login');

        if (response.status === 401 && !isAuthCheckOrLogin) {
            if (!window.location.pathname.endsWith('/index.html') && window.location.pathname !== '/') {
                window.location.href = '/index.html';
            }
            throw new Error('Unauthorized');
        }

        if (url.includes('/report/export')) {
            return response;
        }

        const contentType = response.headers.get('content-type');
        if (contentType && contentType.includes('application/json')) {
            return response.json();
        }

        return response;
    },

    async get(url) {
        return this.request(url, { method: 'GET' });
    },

    async post(url, data) {
        return this.request(url, {
            method: 'POST',
            body: JSON.stringify(data)
        });
    },

    async login(username, password) {
        return this.post('/api/auth/login', { username, password });
    },

    async logout() {
        return this.post('/api/auth/logout', {});
    },

    async getCurrentUser() {
        return this.get('/api/auth/me');
    },

    async saveLocation(latitude, longitude, accuracy) {
        return this.post('/api/location/save', { latitude, longitude, accuracy });
    },

    async getCurrentLocation() {
        return this.get('/api/location/current');
    },

    async getLocationHistory(date) {
        const query = date ? `?date=${date}` : '';
        return this.get('/api/location/history' + query);
    },

    async getTodayDistance() {
        return this.get('/api/location/distance');
    },

    async getTodayStops() {
        return this.get('/api/location/stops');
    },

    async getTodayActivities() {
        return this.get('/api/location/activities');
    },

    async getAdminSummary() {
        return this.get('/api/admin/summary');
    },

    async getEmployees() {
        return this.get('/api/admin/employees');
    },

    async getEmployee(id) {
        return this.get(`/api/admin/employee/${id}`);
    },

    async getLiveLocations() {
        return this.get('/api/admin/live-locations');
    },

    async getReport(userId, date) {
        const params = new URLSearchParams();
        if (userId) params.append('userId', userId);
        if (date) params.append('date', date);
        return this.get('/api/admin/report?' + params.toString());
    },

    async exportReport(userId, date) {
        const params = new URLSearchParams({ userId });
        if (date) params.append('date', date);
        return this.request('/api/admin/report/export?' + params.toString(), { method: 'GET' });
    }
};