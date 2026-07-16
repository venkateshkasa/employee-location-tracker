const CACHE_NAME = "employee-tracker-v2";

const urlsToCache = [
    "/",
    "/index.html",
    "/dashboard.html",
    "/css/style.css",
    "/js/api.js",
    "/js/auth.js",
    "/js/dashboard.js",
    "/js/map.js"
];

// Install Service Worker
self.addEventListener("install", event => {
    event.waitUntil(
        caches.open(CACHE_NAME)
            .then(cache => cache.addAll(urlsToCache))
    );

    self.skipWaiting();
});

// Activate Service Worker and delete old caches
self.addEventListener("activate", event => {
    event.waitUntil(
        caches.keys().then(cacheNames =>
            Promise.all(
                cacheNames.map(cache => {
                    if (cache !== CACHE_NAME) {
                        return caches.delete(cache);
                    }
                })
            )
        )
    );

    self.clients.claim();
});

// Fetch requests
self.addEventListener("fetch", event => {

    // Never cache API requests
    if (event.request.url.includes("/api/")) {
        event.respondWith(fetch(event.request));
        return;
    }

    // Always get latest HTML pages. cache: "no-store" bypasses the browser's own
    // HTTP cache (not just this Service Worker's cache), so a stale response
    // can never be served even though the strategy below is "network first".
    if (event.request.mode === "navigate") {
        event.respondWith(
            fetch(event.request, { cache: "no-store" })
                .then(response => {
                    if (response && response.ok) {
                        const clone = response.clone();
                        caches.open(CACHE_NAME).then(cache => {
                            cache.put(event.request, clone);
                        });
                    }

                    return response;
                })
                .catch(() => caches.match(event.request))
        );

        return;
    }

    // JS/CSS/Images -> Network First (bypassing HTTP cache), Cache Fallback
    event.respondWith(
        fetch(event.request, { cache: "no-store" })
            .then(response => {

                if (response && response.ok) {

                    const clone = response.clone();

                    caches.open(CACHE_NAME).then(cache => {
                        cache.put(event.request, clone);
                    });
                }

                return response;

            })
            .catch(() => caches.match(event.request))
    );
});