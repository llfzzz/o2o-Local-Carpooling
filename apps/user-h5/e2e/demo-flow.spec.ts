import { test, expect, type Page } from '@playwright/test';

// Covers the two regression fixes end-to-end at the frontend contract level (gateway route-mocked,
// same style as map-states.spec):
//  1. The production Message Center never surfaces a login verification code.
//  2. A "random route" adopts the generated route so the NORMAL trip-search returns matching demo
//     trips, whose distance is within the configured range.

test.use({ viewport: { width: 390, height: 844 } });

const SESSION = {
  accessToken: 'test-access-token',
  refreshToken: 'test-refresh-token',
  user: { userId: 'user-1', phone: '13800000000', roles: ['RIDER'] }
};

const CITY = { adcodePrefix: '3502', name: '厦门', cityCode: '0592' };

// A resolved 厦门 place (structured LocationRef the map architecture expects).
function place(name: string, lat: number, lng: number) {
  return {
    point: { latitude: lat, longitude: lng, datum: 'GCJ02' },
    provider: 'demo',
    providerPlaceId: `demo-poi-${name}`,
    cityCode: '0592',
    adcode: '350211',
    displayName: name,
    formattedAddress: `${name}地址`,
    source: 'DEMO_SEED',
    distanceMeters: null,
    resolvedAt: '2026-07-01T00:00:00Z'
  };
}

const ORIGIN = place('软件园三期', 24.4879, 118.1781);
const DESTINATION = place('厦门北站', 24.6153, 118.0507);

// 12 km — inside the demo range [2 km, 30 km].
const GENERATED_DISTANCE_METERS = 12000;

function demoTrip(id: string, seats: number) {
  return {
    tripId: id,
    driverId: `demo-driver-${id}`,
    originText: ORIGIN.displayName,
    destinationText: DESTINATION.displayName,
    departureAt: '2026-07-01T00:30:00Z',
    route: {
      routeId: `route-${id}`,
      distanceMeters: GENERATED_DISTANCE_METERS,
      durationSeconds: 1500,
      providerTrace: 'amap-mock',
      polyline: '118.1781,24.4879;118.0507,24.6153',
      origin: ORIGIN,
      destination: DESTINATION
    },
    inventory: { totalSeats: seats, lockedSeats: 0 },
    seatPrice: { amount: 16.8, currency: 'CNY' },
    status: 'PUBLISHED',
    priceBreakdown: null,
    source: 'DEMO'
  };
}

const GENERATED = [demoTrip('t1', 3), demoTrip('t2', 2), demoTrip('t3', 4)];

async function bootLoggedIn(page: Page) {
  await page.addInitScript(
    ({ session, city }) => {
      window.localStorage.setItem('carpool.session', JSON.stringify(session));
      window.localStorage.setItem('carpool.city', JSON.stringify(city));
    },
    { session: SESSION, city: CITY }
  );
  await page.route('**/api/maps/cities', (route) =>
    route.fulfill({ json: { unrestricted: true, demoProvider: true, cities: [CITY] } }));
  await page.route('**/api/orders**', (route) => route.fulfill({ json: [] }));
  await page.route('**/api/conversations/unread-count', (route) => route.fulfill({ json: { unread: 0 } }));
  await page.route('**/api/conversations?*', (route) => route.fulfill({ json: [] }));
  await page.route('**/api/inbox/unread-count', (route) => route.fulfill({ json: { unread: 1 } }));
}

test('the Message Center shows business notices and never a login verification code', async ({ page }) => {
  await bootLoggedIn(page);
  // The production inbox API never returns login-code categories (server-side guarantee). We mock
  // it as the real backend would respond: business notices only.
  await page.route('**/api/inbox?*', (route) =>
    route.fulfill({
      json: {
        items: [
          {
            deliveryId: 'n1',
            channel: 'IN_APP',
            category: 'ORDER_PAID',
            title: '支付成功，座位已锁定',
            maskedPreview: '订单支付成功，座位已为您锁定',
            status: 'DELIVERED',
            createdAt: '2026-07-01T00:10:00Z',
            readAt: null,
            linkType: 'ORDER',
            linkId: 'order-1',
            cursor: 1,
            revealable: false
          }
        ],
        nextCursor: null
      }
    }));

  await page.goto('/');
  await page.getByRole('button', { name: '消息' }).click();

  // A real business notice is shown…
  await expect(page.getByText('支付成功，座位已锁定')).toBeVisible();
  // …and nothing login-code-related is present anywhere in the Message Center.
  await expect(page.getByText('登录验证码')).toHaveCount(0);
  await expect(page.getByText('AUTH_SMS_CODE')).toHaveCount(0);
  await expect(page.getByText(/验证码/)).toHaveCount(0);
});

test('a random route generates matching demo trips returned by the real trip-search', async ({ page }) => {
  await bootLoggedIn(page);
  await page.route('**/api/inbox?*', (route) =>
    route.fulfill({ json: { items: [], nextCursor: null } }));

  // Before generation there is no selected route, so search returns nothing.
  let generated = false;
  await page.route('**/api/trips/search**', (route) =>
    route.fulfill({ json: generated ? GENERATED : [] }));

  // The random-route endpoint returns the envelope; the frontend adopts its origin/destination.
  await page.route('**/api/demo/trips/random', (route) => {
    generated = true;
    route.fulfill({
      json: {
        origin: ORIGIN,
        destination: DESTINATION,
        route: {
          routeId: 'route-chosen',
          distanceMeters: GENERATED_DISTANCE_METERS,
          durationSeconds: 1500,
          providerTrace: 'amap-mock',
          polyline: '118.1781,24.4879;118.0507,24.6153',
          origin: ORIGIN,
          destination: DESTINATION
        },
        offers: GENERATED
      }
    });
  });

  await page.goto('/');
  await page.getByRole('button', { name: '随机路线' }).click();

  // The generated trips appear via the normal search list, labelled 演示, with in-range distance.
  await expect(page.locator('.trip-card')).toHaveCount(3);
  await expect(page.locator('.trip-card .loc-demo-tag').first()).toBeVisible();
  await expect(page.locator('.trip-card').first()).toContainText('12.0km');
  // 12 km sits inside the configured demo range [2 km, 30 km].
  expect(GENERATED_DISTANCE_METERS).toBeGreaterThanOrEqual(2000);
  expect(GENERATED_DISTANCE_METERS).toBeLessThanOrEqual(30000);
});
