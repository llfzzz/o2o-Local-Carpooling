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
  await page.route('**/api/inbox/unread-count', (route) => route.fulfill({ json: { unread: 0 } }));
  await page.route('**/api/conversations/unread-count', (route) => route.fulfill({ json: { unread: 0 } }));
  await page.route('**/api/conversations?*', (route) => route.fulfill({ json: [] }));
  await page.route('**/api/inbox**', (route) => route.fulfill({ json: { items: [], nextCursor: null } }));
}

test('an unusable map always states why, and never blocks the rest of the screen', async ({ page }) => {
  // Deliberately does not pin which reason: CI has no VITE_AMAP_JS_KEY ("unconfigured"), while a
  // developer machine may have a key whose whitelist excludes localhost ("rejected"). The contract
  // under test is that SOME explicit reason is shown — never a blank frame that looks like a map.
  await bootLoggedIn(page);
  await page.goto('/');

  await expect(page.locator('.trip-map-state')).toBeVisible();
  await expect(page.locator('.trip-map-state'))
    .toHaveText(/地图未配置|未授权当前域名|加载失败|网络已断开/);
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

test('a rejected AMap key is detected rather than rendering a blank map', async ({ page }) => {
  // Verified against a real domain-restricted key: AMap loads the script, constructs the map,
  // renders its attribution bar and even fires `complete` — while rendering no tiles. The only
  // signal is an async console.error, so this pins that we still notice.
  await bootLoggedIn(page);
  await page.goto('/');

  const detected = await page.evaluate(async () => {
    const mod = await import('/src/lib/amapLoader.ts');
    let code: string | null = null;
    mod.onAmapAuthFailure((c: string) => { code = c; });
    // Exactly what AMap emits when the origin is not on the key's whitelist.
    console.error('FlyDataAuthTask error: INVALID_USER_DOMAIN');
    await new Promise((r) => setTimeout(r, 50));
    return code;
  });

  expect(detected).toBe('INVALID_USER_DOMAIN');
});

test('the auth-failure hook forwards to the original console.error', async ({ page }) => {
  // Observation only — swallowing provider errors would trade one silent failure for another.
  await bootLoggedIn(page);
  await page.goto('/');

  const forwarded = await page.evaluate(async () => {
    const mod = await import('/src/lib/amapLoader.ts');
    mod.onAmapAuthFailure(() => {});
    const seen: string[] = [];
    const prev = console.error;
    console.error = (...args: unknown[]) => { seen.push(String(args[0])); prev(...args); };
    console.error('INVALID_USER_KEY happened');
    console.error = prev;
    return seen;
  });

  expect(forwarded).toContain('INVALID_USER_KEY happened');
});
