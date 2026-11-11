/* MedCare: service worker mínimo. Solo guarda la pantalla "sin conexión" y los íconos.
   Nunca guarda páginas con datos médicos (citas, recetas, historias). */
const V = "medcare-v8";
const BASICOS = ["/offline.html", "/img/icon-192.png"];
self.addEventListener("install", (e) => { e.waitUntil(caches.open(V).then((c) => c.addAll(BASICOS)).then(() => self.skipWaiting())); });
self.addEventListener("activate", (e) => {
  e.waitUntil(caches.keys().then((ks) => Promise.all(ks.filter((k) => k !== V).map((k) => caches.delete(k)))).then(() => self.clients.claim()));
});
self.addEventListener("fetch", (e) => {
  if (e.request.method !== "GET" || e.request.mode !== "navigate") return;
  e.respondWith(fetch(e.request).catch(() => caches.match("/offline.html")));
});
