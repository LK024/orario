const CACHE_NAME = 'orario-v2';
const NETWORK_TIMEOUT_MS = 4000;
const urlsToCache = [
  './',
  './index.html',
  './android.html',
  './manifest.json'
];

self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME).then(cache => cache.addAll(urlsToCache))
  );
  self.skipWaiting();
});

self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys()
      .then(keys => Promise.all(
        keys.filter(k => k !== CACHE_NAME).map(k => caches.delete(k))
      ))
      .then(() => self.clients.claim())
  );
});

// Pagine HTML: prima la rete (aggiornamenti sempre visibili).
// Se la rete fallisce o non risponde entro il timeout, si usa la cache.
// La richiesta di rete continua comunque in background e aggiorna la cache.
function networkFirst(event) {
  const req = event.request;

  const network = fetch(req).then(res => {
    if (res.ok && !res.redirected) {
      const copy = res.clone();
      caches.open(CACHE_NAME).then(c => c.put(req, copy));
    }
    return res;
  });
  event.waitUntil(network.catch(() => {}));

  let timer;
  const timeout = new Promise((_, reject) => {
    timer = setTimeout(() => reject(new Error('timeout')), NETWORK_TIMEOUT_MS);
  });

  return Promise.race([network, timeout])
    .finally(() => clearTimeout(timer))
    .catch(() => caches.match(req).then(r => r || caches.match('./index.html')));
}

// Tutto il resto (manifest, font, icone): cache-first, salvando ciò che arriva dalla rete
function cacheFirst(event) {
  const req = event.request;
  return caches.match(req).then(cached => {
    if (cached) return cached;
    return fetch(req).then(res => {
      if (res.ok || res.type === 'opaque') {
        const copy = res.clone();
        caches.open(CACHE_NAME).then(c => c.put(req, copy));
      }
      return res;
    });
  });
}

self.addEventListener('fetch', event => {
  const req = event.request;
  if (req.method !== 'GET') return;

  const url = new URL(req.url);

  // Il CSV di Google Sheets non va mai cachato: ci pensa già localStorage
  if (url.hostname === 'docs.google.com') return;

  if (req.mode === 'navigate' || req.destination === 'document') {
    event.respondWith(networkFirst(event));
  } else {
    event.respondWith(cacheFirst(event));
  }
});
