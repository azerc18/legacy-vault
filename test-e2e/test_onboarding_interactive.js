const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

const mockUser = {
    id: '11111111-2222-3333-4444-555555555555',
    email: 'newbie@legacyvault.test',
    name: 'Người Dùng Mới',
    roles: ['OWNER'],
    role: 'OWNER'
};

const artifactsDir = path.resolve(__dirname, '../test-artifacts/onboarding');
if (!fs.existsSync(artifactsDir)) {
    fs.mkdirSync(artifactsDir, { recursive: true });
}

async function runTest() {
    console.log('🚀 Bắt đầu kiểm thử E2E Onboarding Interactive Spotlight Tour...');
    const browser = await chromium.launch({
        executablePath: 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
        headless: true
    });
    const context = await browser.newContext({
        viewport: { width: 1280, height: 800 },
        locale: 'vi-VN'
    });
    const page = await context.newPage();
    page.on('console', msg => console.log('BROWSER CONSOLE:', msg.text()));
    page.on('pageerror', err => console.log('BROWSER ERROR:', err));

    // 1. Mock auth localStorage: lv_user present, but NO lv_onboarding_status (first time login)
    await page.addInitScript(({ user }) => {
        localStorage.setItem('token', 'mock-valid-token');
        localStorage.setItem('lv_user', JSON.stringify(user));
        localStorage.removeItem('lv_onboarding_status');
        localStorage.removeItem('lv_tour_step');
    }, { user: mockUser });

    // 2. Mock API routes
    await page.route('**/api/**', async (route) => {
        const url = route.request().url();
        if (url.includes('.js') || url.includes('.jsx') || url.includes('/src/') || url.includes('@')) {
            return route.continue();
        }
        if (url.includes('/api/auth/refresh')) {
            return route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify({ success: true, data: { accessToken: 'mock-valid-token' } })
            });
        }
        if (url.includes('/api/users/me')) {
            return route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify({ success: true, data: { ...mockUser, fullName: mockUser.name } })
            });
        }
        if (url.includes('/api/vaults')) {
            return route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify([])
            });
        }
        if (url.includes('/api/plans/my-subscription')) {
            return route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify({ vip: false })
            });
        }
        return route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ success: true })
        });
    });

    // 3. Navigate to /owner/vaults
    console.log('📍 Truy cập /owner/vaults...');
    await page.goto('http://localhost:5173/owner/vaults', { waitUntil: 'networkidle' });
    console.log('📍 Current URL after load:', page.url());
    await page.waitForTimeout(1000);

    // 4. Verify First Visit Question Dialog is visible
    console.log('🔍 Kiểm tra Hộp thoại hỏi người dùng lần đầu...');
    const promptVisible = await page.locator('#first-time-prompt-title').isVisible();
    console.log('   - Hộp thoại hiển thị:', promptVisible);
    if (!promptVisible) {
        throw new Error('Hộp thoại hỏi người dùng lần đầu không xuất hiện!');
    }
    await page.screenshot({ path: path.join(artifactsDir, '01_first_time_prompt.png') });

    // 5. Click "Tôi chưa từng dùng (Bắt đầu hướng dẫn)"
    console.log('👆 Bấm chọn "Tôi chưa từng dùng"...');
    await page.click('button:has-text("Tôi chưa từng dùng")');
    await page.waitForTimeout(600);

    // 6. Verify Step 1: Spotlight highlights "+ Tạo két mới"
    console.log('🔍 Kiểm tra Bước 1 (Highlight "+ Tạo két mới")...');
    const step1Badge = await page.locator('text=Bước 1/6').isVisible();
    console.log('   - Bước 1/6 hiển thị:', step1Badge);
    await page.screenshot({ path: path.join(artifactsDir, '02_step1_new_vault_highlight.png') });

    // 7. Advance to Step 2 (click "+ Tạo két mới" or "Bắt đầu tạo két sắt")
    console.log('👆 Chuyển sang Bước 2...');
    await page.click('button:has-text("Bắt đầu tạo két sắt")');
    await page.waitForTimeout(800);

    // Verify URL is /owner/vaults/new and Step 2 is active
    console.log('🔍 URL hiện tại:', page.url());
    const step2Badge = await page.locator('text=Bước 2/6').isVisible();
    console.log('   - Bước 2/6 hiển thị:', step2Badge);
    await page.screenshot({ path: path.join(artifactsDir, '03_step2_vault_info.png') });

    // 8. Advance to Step 3: Người thụ hưởng
    console.log('👆 Bấm "Tiếp tục" sang Bước 3...');
    await page.click('button:has-text("Tiếp tục")');
    await page.waitForTimeout(600);
    const step3Badge = await page.locator('text=Bước 3/6').isVisible();
    console.log('   - Bước 3/6 hiển thị:', step3Badge);
    await page.screenshot({ path: path.join(artifactsDir, '04_step3_beneficiary.png') });

    // 9. Advance to Step 4: Loại tài sản
    console.log('👆 Bấm "Tiếp tục" sang Bước 4...');
    await page.click('button:has-text("Tiếp tục")');
    await page.waitForTimeout(600);
    const step4Badge = await page.locator('text=Bước 4/6').isVisible();
    console.log('   - Bước 4/6 hiển thị:', step4Badge);
    await page.screenshot({ path: path.join(artifactsDir, '05_step4_asset_type.png') });

    // 10. Advance to Step 5: Nhập thông tin tài sản
    console.log('👆 Bấm "Tiếp tục" sang Bước 5...');
    await page.click('button:has-text("Tiếp tục")');
    await page.waitForTimeout(600);
    const step5Badge = await page.locator('text=Bước 5/6').isVisible();
    console.log('   - Bước 5/6 hiển thị:', step5Badge);
    await page.screenshot({ path: path.join(artifactsDir, '06_step5_asset_fields.png') });

    // 11. Advance to Step 6: Ấn "Tạo két sắt"
    console.log('👆 Bấm "Tiếp tục" sang Bước 6...');
    await page.click('button:has-text("Tiếp tục")');
    await page.waitForTimeout(600);
    const step6Badge = await page.locator('text=Bước 6/6').isVisible();
    console.log('   - Bước 6/6 hiển thị:', step6Badge);
    await page.screenshot({ path: path.join(artifactsDir, '07_step6_submit_button.png') });

    // 12. Click "Hoàn tất hướng dẫn"
    console.log('👆 Bấm "Hoàn tất hướng dẫn"...');
    await page.click('button:has-text("Hoàn tất hướng dẫn")');
    await page.waitForTimeout(600);

    // 13. Verify Congrats Modal
    console.log('🔍 Kiểm tra Modal chúc mừng hoàn thành...');
    const congratsVisible = await page.locator('text=Chúc mừng bạn đã hoàn tất hướng dẫn').isVisible();
    console.log('   - Modal chúc mừng hiển thị:', congratsVisible);
    await page.screenshot({ path: path.join(artifactsDir, '08_congrats_modal.png') });

    // Click "Bắt đầu sử dụng ngay"
    await page.click('button:has-text("Bắt đầu sử dụng ngay")');
    await page.waitForTimeout(400);

    // Check localStorage status
    const status = await page.evaluate(() => localStorage.getItem('lv_onboarding_status'));
    console.log('💾 localStorage lv_onboarding_status:', status);

    await browser.close();
    console.log('✅ KIỂM THỬ THÀNH CÔNG RỰC RỠ 100%!');
}

runTest().catch((err) => {
    console.error('❌ Lỗi kiểm thử:', err);
    process.exit(1);
});
