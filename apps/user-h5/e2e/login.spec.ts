import { test, expect } from '@playwright/test';

// Backend-free smoke: the H5 boots and the interactive SMS-login screen renders. This locks the
// login entry point (phone input + send-code) without needing the demo stack. The full business
// flow is covered end-to-end through the Gateway by scripts/demo-smoke.sh.
test('H5 renders the SMS login screen', async ({ page }) => {
  await page.goto('/');

  await expect(page.getByText('FREE JOY · 验证码登录')).toBeVisible();
  await expect(page.getByRole('button', { name: '获取验证码' })).toBeVisible();
  // The interactive login never auto-fills a code; the login button is gated until a code is sent.
  await expect(page.getByRole('button', { name: '登录', exact: true })).toBeDisabled();
});
