const CACHE = 'receipt-triptych-__CACHE_VERSION__';
const ASSETS = __ASSET_URLS__;
const KNOWN = new Set(ASSETS.map((url) => new URL(url, self.location).pathname));

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(CACHE).then((cache) => cache.addAll(ASSETS)).then(() => self.skipWaiting()));
});
self.addEventListener('activate', (event) => {
  event.waitUntil(Promise.all([
    caches.keys().then((names) => Promise.all(names.filter((name) => name.startsWith('receipt-triptych-') && name !== CACHE).map((name) => caches.delete(name)))),
    self.clients.claim(),
  ]));
});
self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);
  if (event.request.method !== 'GET' || url.origin !== self.location.origin) return;
  if (event.request.mode === 'navigate') {
    event.respondWith(caches.open(CACHE).then((cache) => cache.match('./index.html', { ignoreVary: true })));
  } else if (KNOWN.has(url.pathname)) {
    // Static program assets are identical for every request; dev-server Origin variance must not miss the offline cache.
    event.respondWith(caches.open(CACHE).then((cache) => cache.match(url.pathname, { ignoreVary: true })));
  }
});
