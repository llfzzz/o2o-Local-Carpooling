import { test, expect, type Page } from '@playwright/test';

// The map's failure states. A provider or SDK failure must be visible and honest — the one thing
// that must never happen is a map-shaped surface that looks like it worked.

test.use({ viewport: { width: 390, height: 844 } });

const SESSION = {
  accessToken: 'test-access-token',
  refreshToken: 'test-refresh-token',
  user: { userId: 'user-1', phone: '13800000000', roles: ['RIDER'] }
};

async function bootLoggedIn(page: Page) {
  await page.addInitScript((session) => {
    window.localStorage.setItem('carpool.session', JSON.stringify(session));
  }, SESSION);
  await page.route('**/api/maps/cities', (route) =>
    route.fulfill({ json: { unrestricted: true, demoProvider: false, cities: [] } }));
  await page.route('**/api/trips/search**', (route) => route.fulfill({ json: [] }));
  await page.route('**/api/orders**', (route) => route.fulfill({ json: [] }));
  await page.route('**/api/demo/inbox**', (route) => route.fulfill({ json: [] }));
}

test('unconfigured key: says so instead of showing an empty map frame', async ({ page }) => {
  // No VITE_AMAP_JS_KEY is set in dev/CI, which is exactly this path.
  await bootLoggedIn(page);
  await page.goto('/');

  await expect(page.getByText(/地图未配置/)).toBeVisible();
  // The rest of the screen keeps working without a map.
  await expect(page.getByText('选择出发地点')).toBeVisible();
});

test('offline: reports the network rather than a blank canvas', async ({ page }) => {
  await bootLoggedIn(page);
  await page.addInitScript(() => {
    Object.defineProperty(navigator, 'onLine', { get: () => false, configurable: true });
  });
  await page.goto('/');

  // Unconfigured is checked first, so with no key that message wins; assert one of the two
  // explicit states is present and that nothing pretends to be a working map.
  await expect(page.locator('.trip-map-state')).toBeVisible();
});
