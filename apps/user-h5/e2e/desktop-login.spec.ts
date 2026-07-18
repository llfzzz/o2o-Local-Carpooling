import { test, expect } from '@playwright/test';

// Backend-free smoke for the desktop shell: at the default Desktop Chrome viewport (1280x720,
// above the 1024px matchMedia gate) the rider-console login card renders instead of the mobile
// H5 login, and the code is never auto-filled (登录 stays gated until a code is sent + entered).
test('desktop viewport renders the console login card', async ({ page }) => {
  await page.goto('/');

  await expect(page.locator('.dsk-login')).toBeVisible();
  await expect(page.getByText('FREE JOY · 验证码登录')).toBeVisible();
  await expect(page.getByRole('button', { name: '获取验证码' })).toBeVisible();
  await expect(page.getByRole('button', { name: '登录', exact: true })).toBeDisabled();
  // The mobile shell must not mount on wide viewports.
  await expect(page.locator('.bottom-nav')).toHaveCount(0);
  await expect(page.locator('.mobile-shell')).toHaveCount(0);
});

test('shell swaps live across the 1024px breakpoint', async ({ page }) => {
  await page.goto('/');

  await page.setViewportSize({ width: 1023, height: 800 });
  await expect(page.locator('.mobile-shell')).toBeVisible();
  await expect(page.locator('.dsk-login')).toHaveCount(0);

  await page.setViewportSize({ width: 1024, height: 800 });
  await expect(page.locator('.dsk-login')).toBeVisible();
  await expect(page.locator('.mobile-shell')).toHaveCount(0);
});
