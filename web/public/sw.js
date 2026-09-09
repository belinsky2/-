/**
 * Оффлайн-режим.
 *
 * Стратегия «сначала сеть, при неудаче — кэш»: приложение обновляется само,
 * но продолжает открываться в подвале клуба, где связи нет. Материал лежит
 * в IndexedDB и через этот кэш вообще не проходит.
 */
const CACHE = 'punchline-v1'

self.addEventListener('install', (e) => {
  self.skipWaiting()
  e.waitUntil(caches.open(CACHE))
})

self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))),
    ).then(() => self.clients.claim()),
  )
})

self.addEventListener('fetch', (e) => {
  const req = e.request
  if (req.method !== 'GET' || new URL(req.url).origin !== self.location.origin) return

  e.respondWith(
    fetch(req)
      .then((res) => {
        const copy = res.clone()
        void caches.open(CACHE).then((c) => c.put(req, copy))
        return res
      })
      .catch(async () => {
        const hit = await caches.match(req)
        if (hit) return hit
        // Переход по маршруту без сети отдаём стартовой страницей.
        if (req.mode === 'navigate') {
          const shell = await caches.match('./index.html')
          if (shell) return shell
        }
        return new Response('Нет сети', { status: 503, statusText: 'offline' })
      }),
  )
})
