import { test, expect, type Page } from '@playwright/test';

// Location permission UX, covering every state the browser can put us in: granted, denied,
// timed out, and unsupported. Backend-free like the other specs — the Gateway is route-mocked,
// so this exercises the frontend state machine without needing the demo stack.
//
// The rule under test: a denied or failed prompt is never a dead end. The rider can always still
// search manually, and a detected position is only ever a suggestion.

test.use({ viewport: { width: 390, height: 844 } });

const SESSION = {
  accessToken: 'test-access-token',
  refreshToken: 'test-refresh-token',
  user: { userId: 'user-1', phone: '13800000000', roles: ['RIDER'] }
};

const SOFTWARE_PARK = {
  point: { latitude: 24.4879, longitude: 118.1781, datum: 'GCJ02' },
  provider: 'amap',
  providerPlaceId: 'B001',
  cityCode: '0592',
  adcode: '350211',
  displayName: '软件园三期',
  formattedAddress: '福建省厦门市集美区软件园三期',
  source: 'AUTOCOMPLETE',
  accuracyMeters: null,
  capturedAt: '2026-07-20T02:00:00Z'
};

/** Signs in without a backend and stubs every map call the home screen makes. */
async function bootLoggedIn(page: Page, options?: { suggestions?: unknown[] }) {
  // src/lib/session.ts stores the Session object directly, not a zustand-persist envelope.
  await page.addInitScript((session) => {
    window.localStorage.setItem('carpool.session', JSON.stringify(session));
  }, SESSION);

  await page.route('**/api/maps/cities', (route) =>
    route.fulfill({ json: { unrestricted: false, demoProvider: true, cities: [
      { adcodePrefix: '3502', name: '厦门', cityCode: '0592' },
      { adcodePrefix: '1101', name: '北京', cityCode: '010' }
    ] } }));

  await page.route('**/api/maps/place/suggest**', (route) =>
    route.fulfill({ json: options?.suggestions ?? [SOFTWARE_PARK] }));

  await page.route('**/api/maps/reverse-geocode', (route) =>
    route.fulfill({ json: { ...SOFTWARE_PARK, source: 'MAP_PIN', displayName: '当前位置附近' } }));

  await page.route('**/api/trips/search**', (route) => route.fulfill({ json: [] }));
  await page.route('**/api/orders**', (route) => route.fulfill({ json: [] }));
  await page.route('**/api/inbox/unread-count', (route) => route.fulfill({ json: { unread: 0 } }));
  await page.route('**/api/conversations/unread-count', (route) => route.fulfill({ json: { unread: 0 } }));
  await page.route('**/api/conversations?*', (route) => route.fulfill({ json: [] }));
  await page.route('**/api/inbox**', (route) => route.fulfill({ json: { items: [], nextCursor: null } }));
}

test('granted: the current position resolves into a real place', async ({ page, context }) => {
  await context.grantPermissions(['geolocation']);
  await context.setGeolocation({ latitude: 24.4879, longitude: 118.1781 });
  await bootLoggedIn(page);

  await page.goto('/');
  await page.getByRole('button', { name: /出发/ }).click();
  await page.getByRole('button', { name: '使用我的当前位置' }).click();

  // The browser fix is only a suggestion until the server turns it into a structured place.
  await expect(page.getByRole('button', { name: /当前位置附近/ })).toBeVisible();
});

/**
 * Forces every fix attempt to fail with the given GeolocationPositionError code. Headless
 * Chromium leaves getCurrentPosition pending when no permission decision has been made, so
 * overriding it is the only deterministic way to exercise the failure paths.
 */
async function failGeolocationWith(page: Page, code: 1 | 2 | 3) {
  await page.addInitScript((errorCode) => {
    navigator.geolocation.getCurrentPosition = (_success, error) => {
      error?.({
        code: errorCode,
        message: 'forced',
        PERMISSION_DENIED: 1,
        POSITION_UNAVAILABLE: 2,
        TIMEOUT: 3
      } as GeolocationPositionError);
    };
  }, code);
}

test('denied: falls back to manual search instead of dead-ending', async ({ page }) => {
  await bootLoggedIn(page);
  await failGeolocationWith(page, 1);

  await page.goto('/');
  await page.getByRole('button', { name: /出发/ }).click();
  await page.getByRole('button', { name: '使用我的当前位置' }).click();

  await expect(page.getByText('浏览器已拒绝定位权限。可以直接搜索地点，或在浏览器设置里重新允许。')).toBeVisible();

  // The manual path still works, which is the whole point.
  await page.getByLabel('搜索地点').fill('软件园');
  await expect(page.getByRole('button', { name: /软件园三期/ })).toBeVisible();
});

test('timed out: offers a retry and still allows manual search', async ({ page, context }) => {
  await context.grantPermissions(['geolocation']);
  await bootLoggedIn(page);
  // Every attempt times out, including the hook's one automatic retry.
  await failGeolocationWith(page, 3);

  await page.goto('/');
  await page.getByRole('button', { name: /出发/ }).click();
  await page.getByRole('button', { name: '使用我的当前位置' }).click();

  await expect(page.getByRole('button', { name: /定位超时，重试/ })).toBeVisible();

  await page.getByLabel('搜索地点').fill('软件园');
  await expect(page.getByRole('button', { name: /软件园三期/ })).toBeVisible();
});

test('unsupported: says so plainly and keeps search usable', async ({ page }) => {
  await bootLoggedIn(page);
  await page.addInitScript(() => {
    Object.defineProperty(navigator, 'geolocation', { value: undefined, configurable: true });
  });

  await page.goto('/');
  await page.getByRole('button', { name: /出发/ }).click();

  await expect(page.getByText('当前环境不支持定位。请直接搜索地点。')).toBeVisible();
  await page.getByLabel('搜索地点').fill('软件园');
  await expect(page.getByRole('button', { name: /软件园三期/ })).toBeVisible();
});

test('poor accuracy is flagged with a correction flow in the expanded map', async ({ page, context }) => {
  await context.grantPermissions(['geolocation']);
  // A fix with a 500 m accuracy radius — well past the 100 m poor-accuracy threshold.
  await context.setGeolocation({ latitude: 24.4879, longitude: 118.1781, accuracy: 500 });
  await bootLoggedIn(page);

  await page.goto('/');
  // The correction flow lives in the expanded map, where the marker can be dragged/re-pinned.
  await page.getByRole('button', { name: '展开地图' }).click();
  await page.getByRole('button', { name: /定位起点/ }).click();

  // Explicit low-accuracy warning that points the rider at the manual correction.
  await expect(page.getByText(/定位精度较低/)).toBeVisible();
});

test('demo provider results are badged as demo data', async ({ page }) => {
  await bootLoggedIn(page, { suggestions: [{ ...SOFTWARE_PARK, provider: 'demo' }] });

  await page.goto('/');
  await expect(page.getByText(/演示地图数据/)).toBeVisible();

  await page.getByRole('button', { name: /出发/ }).click();
  await page.getByLabel('搜索地点').fill('软件园');
  await expect(page.getByText('演示', { exact: true })).toBeVisible();
});

test('no hardcoded city or route: the rider starts from an empty search', async ({ page }) => {
  await bootLoggedIn(page);

  await page.goto('/');

  // The old build seeded 软件园三期 / 集美大学 / 厦门 into the inputs. Nothing may be pre-filled now.
  await expect(page.getByText('选择出发地点')).toBeVisible();
  await expect(page.getByText('选择到达地点')).toBeVisible();
  await expect(page.getByText('先选择起点和终点')).toBeVisible();
});
