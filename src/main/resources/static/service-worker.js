const CACHE_NAME = "employee-tracker-v1";

const urlsToCache = [
    "/",
    "/index.html",
    "/dashboard.html",
    "/css/style.css",
    "/js/api.js",
    "/js/auth.js",
    "/js/dashboard.js"
];

self.addEventListener("install", event => {
    event.waitUntil(
        caches.open(CACHE_NAME)
            .then(cache => cache.addAll(urlsToCache))
    );
});

self.addEventListener("fetch", event => {
    event.respondWith(
        caches.match(event.request)
            .then(response => {
                return response || fetch(event.request);
            })
    );
});