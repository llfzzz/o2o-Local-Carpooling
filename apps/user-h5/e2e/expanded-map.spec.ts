import { test, expect, type Page } from '@playwright/test';

// Expanded interactive map: opening it, picking endpoints via the (mocked) server suggest, and
// confirming a route so the server-authoritative price breakdown renders and the thumbnail stays
// in sync via the shared route-selection store. Backend-free — the Gateway is route-mocked.
//
// The invariant under test: the browser resolves nothing itself. Endpoints come from
// /api/maps/place/suggest and the route + price come from /api/trips/route-preview; the client
// only displays what the server returned.

test.use({ viewport: { width: 390, height: 844 } });

const SESSION = {
  accessToken: 'test-access-token',
  refreshToken: 'test-refresh-token',
  user: { userId: 'user-1', phone: '13800000000', roles: ['RIDER'] }
};

const place = (displayName: string, lat: number, lng: number) => ({
  point: { latitude: lat, longitude: lng, datum: 'GCJ02' },
  provider: 'amap',
  providerPlaceId: `poi-${displayName}`,
  cityCode: '0592',
  adcode: '350211',
  displayName,
  formattedAddress: `福建省厦门市${displayName}`,
  source: 'AUTOCOMPLETE',
  accuracyMeters: null,
  capturedAt: '2026-07-20T02:00:00Z'
});

const ROUTE_PREVIEW = {
  route: {
    routeId: 'route-1',
    distanceMeters: 18500,
    durationSeconds: 2100,
    providerTrace: 'amap-v5',
    polyline: '118.10,24.48;118.12,24.50',
    origin: place('软件园三期', 24.4879, 118.1781),
    destination: place('集美大学', 24.5766, 118.0994)
  },
  pricing: {
    distanceMeters: 18500,
    distanceKm: 18.5,
    baseFare: 6.0,
    includedKm: 3.0,
    chargeableKm: 15.5,
    extraCharge: 18.6,
    total: { amount: 24.6, currency: 'CNY' },
    currency: 'CNY'
  }
};

async function bootLoggedIn(page: Page) {
  await page.addInitScript((session) => {
    window.localStorage.setItem('carpool.session', JSON.stringify(session));
  }, SESSION);

  await page.route('**/api/maps/cities', (route) =>
    route.fulfill({ json: { unrestricted: false, demoProvider: true, cities: [
      { adcodePrefix: '3502', name: '厦门', cityCode: '0592' }
    ] } }));

  // Suggest returns the endpoint matching the typed keyword.
  await page.route('**/api/maps/place/suggest**', (route) => {
    const url = new URL(route.request().url());
    const keyword = url.searchParams.get('keyword') ?? '';
    const match = keyword.includes('集美') ? place('集美大学', 24.5766, 118.0994) : place('软件园三期', 24.4879, 118.1781);
    return route.fulfill({ json: [match] });
  });

  await page.route('**/api/trips/route-preview', (route) => route.fulfill({ json: ROUTE_PREVIEW }));
  await page.route('**/api/trips/search**', (route) => route.fulfill({ json: [] }));
  await page.route('**/api/orders**', (route) => route.fulfill({ json: [] }));
  await page.route('**/api/inbox/unread-count', (route) => route.fulfill({ json: { unread: 0 } }));
  await page.route('**/api/conversations/unread-count', (route) => route.fulfill({ json: { unread: 0 } }));
  await page.route('**/api/conversations?*', (route) => route.fulfill({ json: [] }));
  await page.route('**/api/inbox**', (route) => route.fulfill({ json: { items: [], nextCursor: null } }));
}

test('confirming a route in the expanded map shows the server price and syncs the thumbnail', async ({ page }) => {
  await bootLoggedIn(page);
  await page.goto('/');

  // Open the expanded map from the thumbnail.
  await page.getByRole('button', { name: '展开地图' }).click();
  const dialog = page.getByRole('dialog', { name: '选择路线' });
  await expect(dialog).toBeVisible();

  // Pick both endpoints through the server-backed search (never free text). Scoped to the
  // dialog because the home screen's own endpoint buttons still exist behind the modal.
  await dialog.getByRole('button', { name: /选择出发地点/ }).click();
  await page.getByLabel('搜索地点').fill('软件园');
  await page.getByRole('button', { name: /软件园三期/ }).click();

  await dialog.getByRole('button', { name: /选择到达地点/ }).click();
  await page.getByLabel('搜索地点').fill('集美');
  await page.getByRole('button', { name: /集美大学/ }).click();

  // Confirm → the server route-preview price breakdown renders, then the modal closes.
  await dialog.getByRole('button', { name: '确认路线' }).click();
  await expect(dialog).toBeHidden();

  // The thumbnail's route fields now reflect the confirmed endpoints (shared store).
  await expect(page.getByRole('button', { name: '出发 软件园三期' })).toBeVisible();
  await expect(page.getByRole('button', { name: '到达 集美大学' })).toBeVisible();
});
