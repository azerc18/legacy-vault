const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

const mockUser = {
    id: '11111111-2222-3333-4444-555555555555',
    email: 'veteran@legacyvault.test',
    name: 'Người Dùng Cũ',
    roles: ['OWNER'],
    role: 'OWNER'
};

async function testExperiencedFlow() {
    console.log('🚀 Kiểm thử kịch bản: Người dùng chọn "Tôi đã dùng rồi"...');
    const browser = await chromium.launch({
        executablePath: 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
        headless: true
    });
    const context = await browser.newContext({
        viewport: { width: 1280, height: 800 },
        locale: 'vi-VN'
    });
    const page = await context.newPage();

    await page.addInitScript(({ user }) => {
        localStorage.setItem('token', 'mock-valid-token');
        localStorage.setItem('lv_user', JSON.stringify(user));
    }, { user: mockUser });

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

    await page.goto('http://localhost:5173/owner/vaults', { waitUntil: 'networkidle' });
    await page.waitForTimeout(1000);

    // Verify dialog appears
    const promptVisible = await page.locator('#first-time-prompt-title').isVisible();
    console.log('   - Hộp thoại câu hỏi xuất hiện:', promptVisible);
    if (!promptVisible) throw new Error('Hộp thoại câu hỏi không xuất hiện!');

    // Click "Tôi đã dùng rồi"
    console.log('👆 Bấm chọn "Tôi đã dùng rồi"...');
    await page.click('button:has-text("Tôi đã dùng rồi")');
    await page.waitForTimeout(500);

    // Verify dialog is gone
    const promptAfter = await page.locator('#first-time-prompt-title').isVisible();
    console.log('   - Hộp thoại đã đóng:', !promptAfter);
    if (promptAfter) throw new Error('Hộp thoại vẫn còn hiển thị sau khi click!');

    // Verify tour is NOT running
    const tourStepVisible = await page.locator('text=Bước 1/6').isVisible();
    console.log('   - Spotlight Tour KHÔNG kích hoạt:', !tourStepVisible);
    if (tourStepVisible) throw new Error('Spotlight Tour lại kích hoạt ngoài ý muốn!');

    // Verify localStorage status
    const status = await page.evaluate(() => localStorage.getItem('lv_onboarding_status'));
    console.log('💾 localStorage lv_onboarding_status:', status);
    if (status !== 'experienced') throw new Error(`Trạng thái không đúng: ${status}`);

    // Verify reload doesn't show prompt again
    console.log('🔄 Tải lại trang (F5) để kiểm tra không hỏi lại lần 2...');
    await page.reload({ waitUntil: 'networkidle' });
    await page.waitForTimeout(1000);
    const promptOnReload = await page.locator('#first-time-prompt-title').isVisible();
    console.log('   - Không hiển thị lại khi tải lại trang:', !promptOnReload);
    if (promptOnReload) throw new Error('Hộp thoại lại xuất hiện khi reload!');

    await browser.close();
    console.log('✅ KỊCH BẢN "TÔI ĐÃ DÙNG RỒI" HOÀN TOÀN CHÍNH XÁC!');
}

testExperiencedFlow().catch((err) => {
    console.error('❌ Lỗi:', err);
    process.exit(1);
});
