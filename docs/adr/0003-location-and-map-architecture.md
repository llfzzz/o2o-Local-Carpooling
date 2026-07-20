# ADR 0003 — Location, map and geographic matching architecture

- Status: Accepted
- Date: 2026-07-20
- Supersedes: nothing. Extends ADR 0002 (Provider SPI + demo profiles).

## Context

The trip-location experience was presentation-only. Riders typed Chinese strings into inputs; those
travelled to the server as `originText`/`destinationText`; the server either geocoded them via AMap
or fabricated a distance from string length (`(origin.length + destination.length) * 1200`); and
matching was `origin_text LIKE '%text%'`. There was no map (the "map" was four empty `<span>`s and a
rotated dashed CSS line), no `navigator.geolocation` anywhere, no coordinates in any frontend type,
and no driver position at all.

Critically, there was **no coordinate-system handling anywhere in the codebase**. AMap emits and
consumes GCJ-02; the browser Geolocation API emits WGS-84; inside China the two differ by roughly
500m. The existing code treated coordinates as opaque `"lng,lat"` strings matched by a regex with no
range check and no datum tag. It was self-consistent only because nothing had yet introduced a
WGS-84 source.

## Decisions

### 1. Every coordinate carries its datum; conversion happens in exactly one place

`GeoPoint(latitude, longitude, datum)` in `backend/common` has no constructor accepting an unlabeled
pair. Canonical storage and transport is GCJ-02 (matching the provider). `CoordinateTransform` is the
only WGS-84 ↔ GCJ-02 boundary, invoked from `MapQueryService` and `DriverLocationService`.

*Why:* a 500m error is not a rounding problem, it is "the car is on a different street". Making the
datum unrepresentable-as-absent removes the entire class of bug rather than documenting it.

### 2. A location is a `LocationRef`, never a string

Free text is an *input to resolution*, never a location. `LocationRef` carries coordinates + datum,
provider identity, provider place id, **adcode** (the multi-city key), display name, formatted
address, source, accuracy and capture time. City comparison is by adcode, never by display text.

### 3. Map capabilities live behind the existing Provider SPI, server-side

`MapProvider` grew from 2 methods to 5 (`quote`, `reverseGeocode`, `suggest`, `searchPoi`). The
browser never calls AMap for data — only for tile rendering. POI search, autocomplete, geocoding and
routing all proxy through `map-service`.

*Why:* one place for caching, quota control, rate limiting and observability; results are structured
and validated server-side before they can enter a trip; and a leaked browser key cannot be used to
farm place data. Cost: one debounced hop of latency per keystroke.

### 4. City support is configuration, not code

`MapCityRegistry` gates by adcode prefix, empty list meaning unrestricted. No city name appears in
any business conditional. The demo provider ships fixtures for four *unrelated* cities (厦门, 北京,
成都, 哈尔滨) specifically so a regression in one region cannot hide behind the demo route.

### 5. Matching is endpoint proximity + time window, with a bounding-box pre-filter

`GeoMatchingPolicy` (pure domain) defines origin radius, destination radius, departure window and
ranking. `TripRepository.searchByProximity` pre-filters with an indexed lat/lng box, then computes
exact great-circle distance in Java.

**Plain `DECIMAL(10,7)` columns, not MySQL SPATIAL** — repository tests run on H2, where `ST_*` is
unavailable, and a composite B-tree serves a bounding box perfectly well at this scale. Keeping the
tests able to exercise the real query mattered more than using the more specialised index type.

The pre-filter must **over**-select, never under-select. An early version derived the box from
111,320 m/degree while `Haversine` used a 6,371,008.8 m radius (111,195 m/degree), making the box ~6m
too small — silently dropping candidates at the radius edge. Both now share one earth model plus a
1% margin, pinned by a test.

### 6. Scheduled carpool with live pickup tracking — not ride-hailing

Driver position exists **only while a trip is active** and is visible **only to riders holding a
LOCKED seat** on it. Nothing is exposed before booking, so browsing cannot be used to harvest driver
positions. Non-participants get **404, not 403**, so the endpoint cannot confirm a trip is being
tracked. This preserves the `AGENTS.md` non-goal of complex dynamic dispatch.

### 7. Live positions are ephemeral

Redis only, under a TTL (default 45s), never MySQL. The TTL is simultaneously the offline signal
(an unrefreshed entry simply expires, so no reaper job is needed) and the retention limit.

*Why:* precise location history is a large, sensitive dataset with real retention, deletion and
disclosure obligations, and this product does not need it — the only question it must answer is
"where is my driver right now". There is no history to leak or forget to delete.

### 8. Driver capability is checked at publish, not on every position update

Publishing requires identity APPROVED **and** documents APPROVED, verified server-side via
driver-service's internal API. Because a trip cannot exist without that check, "does this principal
own an active trip" is sufficient authorization for the 10-second location hot path — no
cross-service call per update.

This also closed the long-documented gap where `POST /api/trips` took `driverId` from the request
body, letting any authenticated user publish as anybody.

## Alternatives considered

- **Route-corridor matching** (rider joins mid-route): better real-world matching, but needs
  persisted geometry indexing, point-to-polyline distance per candidate, and a detour-limit rule.
  Endpoint proximity ships now; route geometry is already persisted, so the algorithm can be swapped
  without a migration.
- **Leaflet + third-party tiles**: vendor-neutral, but tiles would be WGS-84 while routing is
  GCJ-02, reintroducing exactly the datum mixing this ADR exists to prevent.
- **WebSocket for tracking**: lowest latency, but new infrastructure on a host already ~38%
  oversubscribed on memory (`AGENTS.md` S34). SSE is one-directional, which is all this needs.
- **IP-based city fallback after a denied prompt**: rejected. Inferring location immediately after
  someone declines to share it is the wrong instinct, and carrier IP geolocation is frequently wrong.
  A remembered city choice is both more accurate and more honest.

## Consequences

- Two distinct AMap credentials: a server Web Service key (`AMAP_API_KEY`) and a domain-restricted
  browser JSAPI key (`VITE_AMAP_JS_KEY`). The JSAPI *security code* never reaches the browser —
  nginx proxies `/_AMapService/` and appends it server-side.
- `scripts/demo-smoke.sh` had to be reordered: the rider must now earn driver capability before
  publishing, because the capability gate is real.
- A configured-but-failing provider **fails closed**. It never degrades to demo output, and demo
  output is always badged, so a fabricated route cannot be mistaken for a real one.
- Spoofing defence is *plausibility*, not proof: a spoofer moving at believable speed is not
  detected. Recorded as residual risk in `docs/security.md`.
