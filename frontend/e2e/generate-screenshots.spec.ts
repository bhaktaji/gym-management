import { test, expect } from '@playwright/test';
import * as path from 'path';

const SCREENSHOT_DIR = '/app/applet/gym-management/docs/screenshots';
const BASE_URL = 'http://127.0.0.1:8085';

test('Generate production screenshots against packaged JAR on port 8085', async ({ page }) => {
  test.setTimeout(90000);

  // 1. Surya Fitness Club (Attendance OFF) Dashboard
  await page.goto(`${BASE_URL}/login`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('input[inputmode="tel"]');
  await page.fill('input[inputmode="tel"]', '+919810010003');
  await page.fill('input[type="password"]', 'DemoOwner@2026');
  await page.click('button[type="submit"]');
  await page.waitForURL('**/app');
  await page.waitForTimeout(1500);
  await page.screenshot({ path: path.join(SCREENSHOT_DIR, 'owner-dashboard-attendance-off.png'), fullPage: false });

  // 2. Members List (Surya)
  await page.goto(`${BASE_URL}/app/members`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('table tbody tr');
  await page.waitForTimeout(1000);
  await page.screenshot({ path: path.join(SCREENSHOT_DIR, 'members-list.png'), fullPage: false });

  // 3. Billing & Invoices
  await page.goto(`${BASE_URL}/app/billing`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('table tbody tr');
  await page.waitForTimeout(1000);
  await page.screenshot({ path: path.join(SCREENSHOT_DIR, 'invoice-preview.png'), fullPage: false });

  // 4. Logout and Login as Bajrang Powerhouse (Attendance ON)
  await page.click('button:has-text("Logout"), button:has-text("लॉगआउट")');
  await page.waitForURL('**/login');
  await page.waitForSelector('input[inputmode="tel"]');
  await page.fill('input[inputmode="tel"]', '+919810010001');
  await page.fill('input[type="password"]', 'DemoOwner@2026');
  await page.click('button[type="submit"]');
  await page.waitForURL('**/app');
  await page.waitForTimeout(1500);
  await page.screenshot({ path: path.join(SCREENSHOT_DIR, 'owner-dashboard-attendance-on.png'), fullPage: false });

  // 5. Attendance Page (Bajrang Powerhouse ON)
  await page.goto(`${BASE_URL}/app/checkins`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(1000);
  await page.screenshot({ path: path.join(SCREENSHOT_DIR, 'attendance-page.png'), fullPage: false });

  // 6. PT & Commission Statement
  await page.goto(`${BASE_URL}/app/pt`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(1000);
  await page.screenshot({ path: path.join(SCREENSHOT_DIR, 'pt-commission-statement.png'), fullPage: false });

  // 7. Hindi Toggle
  const hindiBtn = page.locator('button:has-text("हिंदी"), button:has-text("HI"), button:has-text("हि")').first();
  if (await hindiBtn.isVisible()) {
    await hindiBtn.click();
    await page.waitForTimeout(500);
  }
  await page.screenshot({ path: path.join(SCREENSHOT_DIR, 'hindi-toggle.png'), fullPage: false });

  // 8. Public QR Self Check-in at 390px mobile viewport (/g/bajrang-lko/in)
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(`${BASE_URL}/g/bajrang-lko/in`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(1500);
  await page.screenshot({ path: path.join(SCREENSHOT_DIR, 'qr-self-checkin-390px.png'), fullPage: false });

  // 9. Public UPI Pay Page
  const tokenStr = await page.evaluate(() => localStorage.getItem('gymkhata.tokens'));
  let invId = '';
  if (tokenStr) {
    try {
      const { accessToken } = JSON.parse(tokenStr);
      const res = await fetch(`${BASE_URL}/api/v1/billing/invoices`, {
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      const data = await res.json();
      if (Array.isArray(data) && data.length > 0) {
        invId = data[0].id;
      }
    } catch (e) {}
  }
  if (invId) {
    await page.goto(`${BASE_URL}/pay/${invId}`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(1500);
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, 'upi-pay-page.png'), fullPage: false });
  }
});
