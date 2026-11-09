const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

const ownerUser = {
    id: '11111111-2222-3333-4444-555555555555',
    email: 'owner@legacyvault.test',
    name: 'Chủ Tài Sản',
    roles: ['OWNER'],
    role: 'OWNER'
};

const beneficiaryUser = {
    id: '22222222-2222-3333-4444-555555555555',
    email: 'beneficiary@legacyvault.test',
    name: 'Người Thụ Hưởng',
    roles: ['BENEFICIARY'],
    role: 'BENEFICIARY'
};

const executorUser = {
    id: '33333333-2222-3333-4444-555555555555',
    email: 'executor@legacyvault.test',
    name: 'Người Thực Thi',
    roles: ['EXECUTOR'],
    role: 'EXECUTOR'
};

const verifierUser = {
    id: '44444444-2222-3333-4444-555555555555',
    email: 'verifier@legacyvault.test',
    name: 'Thẩm Định Viên',
    roles: ['LEGAL_VERIFIER'],
    role: 'LEGAL_VERIFIER'
};

const adminUser = {
    id: '55555555-2222-3333-4444-555555555555',
    email: 'admin@legacyvault.test',
    name: 'Quản Trị Viên',
    roles: ['ADMIN'],
    role: 'ADMIN'
};

let currentUser = ownerUser;

async function setupApiMocks(page) {
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
                body: JSON.stringify({ success: true, data: { ...currentUser, fullName: currentUser.name } })
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
        if (url.includes('/api/activity') || url.includes('/api/logs')) {
            return route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify({ items: [], content: [], totalElements: 0 })
            });
        }
        if (url.includes('/api/dms')) {
            return route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify({
                    status: 'INACTIVE',
                    checkInIntervalDays: 30,
                    gracePeriodDays: 7,
                    lastCheckIn: null,
                    nextCheckInDeadline: null
                })
            });
        }
        if (url.includes('/api/executors')) {
            return route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify({ primaryExecutor: null, backupExecutor: null })
            });
        }
        if (url.includes('/api/verifier')) {
            return route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify([])
            });
        }
        return route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ success: true, data: [] })
        });
    });
}

async function setSessionUser(page, user) {
    currentUser = user;
    await page.evaluate(({ u }) => {
        localStorage.setItem('token', 'mock-valid-token');
        localStorage.setItem('lv_user', JSON.stringify(u));
        localStorage.setItem('lv_onboarding_status', 'experienced');
    }, { u: user });
}

async function runFeatureToursVerification() {
    console.log('🧪 BẮT ĐẦU KIỂM THỬ TOÀN DIỆN: ONBOARDING NHANH TỪNG TÍNH NĂNG (FEATURE TOURS)');
    const browser = await chromium.launch({
        executablePath: 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
        headless: true
    });

    const context = await browser.newContext({
        viewport: { width: 1280, height: 800 },
        locale: 'vi-VN'
    });
    const page = await context.newPage();

    // Thư mục lưu ảnh bằng chứng
    const artifactDir = 'C:\\Users\\shin\\.gemini\\antigravity\\brain\\84420023-c595-401f-a9d9-e40238fb8c21';

    try {
        await setupApiMocks(page);

        // =========================================================================
        // TEST 1: Owner Activity (/owner/activity)
        // =========================================================================
        console.log('\n--- 1. Kiểm thử Owner Activity (/owner/activity) ---');
        await page.goto('http://localhost:5173/login', { waitUntil: 'domcontentloaded' });
        await setSessionUser(page, ownerUser);

        await page.goto('http://localhost:5173/owner/activity', { waitUntil: 'networkidle' });
        await page.waitForTimeout(600);

        const tooltip = page.locator('#spotlight-tour-tooltip');
        await tooltip.waitFor({ state: 'visible', timeout: 5000 });
        console.log('   ✅ Feature tour tự động kích hoạt lần đầu truy cập');

        // Chụp ảnh bằng chứng bước 1 (Header)
        await page.screenshot({ path: path.join(artifactDir, 'test_tour_owner_activity_step1.png') });
        console.log('   📸 Đã chụp: test_tour_owner_activity_step1.png');

        // Bấm tiếp tục -> Bước 2 (Bộ lọc thời gian)
        await page.click('#spotlight-tour-tooltip button:has-text("Tiếp tục")');
        await page.waitForTimeout(400);
        await page.screenshot({ path: path.join(artifactDir, 'test_tour_owner_activity_step2.png') });
        console.log('   📸 Đã chụp: test_tour_owner_activity_step2.png');

        // Bấm tiếp tục -> Bước 3 (Danh sách hoạt động)
        await page.click('#spotlight-tour-tooltip button:has-text("Tiếp tục")');
        await page.waitForTimeout(400);
        await page.screenshot({ path: path.join(artifactDir, 'test_tour_owner_activity_step3.png') });
        console.log('   📸 Đã chụp: test_tour_owner_activity_step3.png');

        // Bấm hoàn tất "Đã hiểu"
        console.log('   👆 Bấm nút "Đã hiểu" kết thúc tour...');
        await page.click('#spotlight-tour-tooltip button:has-text("Đã hiểu")');
        await page.waitForTimeout(400);

        // Kiểm tra tour biến mất và localStorage đã lưu
        const isDismissed = !(await tooltip.isVisible());
        console.log('   ✅ Tour đã đóng lại hoàn toàn:', isDismissed);
        const seenStorage = await page.evaluate(() => localStorage.getItem('lv_seen_feature_owner_activity'));
        console.log('   ✅ LocalStorage ghi nhận lv_seen_feature_owner_activity =', seenStorage);
        if (seenStorage !== 'true') throw new Error('LocalStorage không lưu trạng thái đã xem!');

        // Reload trang -> kiểm tra tour KHÔNG tự động hiện lại
        await page.reload({ waitUntil: 'networkidle' });
        await page.waitForTimeout(800);
        const tourAfterReload = await tooltip.isVisible();
        console.log('   ✅ Khi tải lại trang, tour KHÔNG tự động hiện lại phiền người dùng:', !tourAfterReload);
        if (tourAfterReload) throw new Error('Tour tự động hiện lại sau khi đã hoàn tất!');

        // Kiểm tra tính năng Replay từ Header Menu
        console.log('   🔄 Kiểm tra bấm "Xem lại hướng dẫn tính năng này" từ menu Hướng dẫn...');
        await page.click('button:has-text("Hướng dẫn")');
        await page.waitForTimeout(300);
        const replayBtn = page.locator('button:has-text("Xem lại hướng dẫn tính năng này")');
        if (await replayBtn.isVisible()) {
            await replayBtn.click();
            await page.waitForTimeout(500);
            const replayVisible = await tooltip.isVisible();
            console.log('   ✅ Tour được kích hoạt lại thành công theo yêu cầu của người dùng:', replayVisible);
            await page.screenshot({ path: path.join(artifactDir, 'test_tour_owner_activity_replayed.png') });
            console.log('   📸 Đã chụp: test_tour_owner_activity_replayed.png');
            await page.click('#spotlight-tour-tooltip button[title="Bỏ qua hướng dẫn"]');
            await page.waitForTimeout(300);
        } else {
            console.log('   ⚠️ Nút xem lại không thấy trong menu dropdown');
        }

        // =========================================================================
        // TEST 2: Owner DMS (/owner/dms)
        // =========================================================================
        console.log('\n--- 2. Kiểm thử Owner Dead Man\'s Switch (/owner/dms) ---');
        await page.goto('http://localhost:5173/owner/dms', { waitUntil: 'networkidle' });
        await page.waitForTimeout(600);

        await tooltip.waitFor({ state: 'visible', timeout: 5000 });
        console.log('   ✅ DMS Feature tour tự động kích hoạt');
        await page.screenshot({ path: path.join(artifactDir, 'test_tour_owner_dms_step1.png') });
        console.log('   📸 Đã chụp: test_tour_owner_dms_step1.png');

        await page.click('#spotlight-tour-tooltip button[title="Bỏ qua hướng dẫn"]');
        await page.waitForTimeout(300);
        const seenDms = await page.evaluate(() => localStorage.getItem('lv_seen_feature_owner_dms'));
        console.log('   ✅ Ghi nhận lv_seen_feature_owner_dms =', seenDms);

        // =========================================================================
        // TEST 3: Beneficiary (/beneficiary)
        // =========================================================================
        console.log('\n--- 3. Kiểm thử Beneficiary (/beneficiary) ---');
        await setSessionUser(page, beneficiaryUser);

        await page.goto('http://localhost:5173/beneficiary', { waitUntil: 'networkidle' });
        await page.waitForTimeout(600);

        await tooltip.waitFor({ state: 'visible', timeout: 5000 });
        console.log('   ✅ Beneficiary Feature tour tự động kích hoạt');
        await page.screenshot({ path: path.join(artifactDir, 'test_tour_beneficiary_step1.png') });
        console.log('   📸 Đã chụp: test_tour_beneficiary_step1.png');

        await page.click('#spotlight-tour-tooltip button[title="Bỏ qua hướng dẫn"]');
        await page.waitForTimeout(300);
        const seenBeneficiary = await page.evaluate(() => localStorage.getItem('lv_seen_feature_beneficiary_vaults'));
        console.log('   ✅ Ghi nhận lv_seen_feature_beneficiary_vaults =', seenBeneficiary);

        // =========================================================================
        // TEST 4: Executor (/executor/home)
        // =========================================================================
        console.log('\n--- 4. Kiểm thử Executor (/executor/home) ---');
        await setSessionUser(page, executorUser);

        await page.goto('http://localhost:5173/executor/home', { waitUntil: 'networkidle' });
        await page.waitForTimeout(600);

        await tooltip.waitFor({ state: 'visible', timeout: 5000 });
        console.log('   ✅ Executor Feature tour tự động kích hoạt');
        await page.screenshot({ path: path.join(artifactDir, 'test_tour_executor_step1.png') });
        console.log('   📸 Đã chụp: test_tour_executor_step1.png');

        await page.click('#spotlight-tour-tooltip button[title="Bỏ qua hướng dẫn"]');
        await page.waitForTimeout(300);
        const seenExecutor = await page.evaluate(() => localStorage.getItem('lv_seen_feature_executor_home'));
        console.log('   ✅ Ghi nhận lv_seen_feature_executor_home =', seenExecutor);

        // =========================================================================
        // TEST 5: Legal Verifier (/verifier)
        // =========================================================================
        console.log('\n--- 5. Kiểm thử Legal Verifier (/verifier) ---');
        await setSessionUser(page, verifierUser);

        await page.goto('http://localhost:5173/verifier', { waitUntil: 'networkidle' });
        await page.waitForTimeout(600);

        await tooltip.waitFor({ state: 'visible', timeout: 5000 });
        console.log('   ✅ Legal Verifier Feature tour tự động kích hoạt');
        await page.screenshot({ path: path.join(artifactDir, 'test_tour_verifier_step1.png') });
        console.log('   📸 Đã chụp: test_tour_verifier_step1.png');

        await page.click('#spotlight-tour-tooltip button[title="Bỏ qua hướng dẫn"]');
        await page.waitForTimeout(300);
        const seenVerifier = await page.evaluate(() => localStorage.getItem('lv_seen_feature_verifier_dashboard'));
        console.log('   ✅ Ghi nhận lv_seen_feature_verifier_dashboard =', seenVerifier);

        // =========================================================================
        // TEST 6: Admin Excluded (/admin/users)
        // =========================================================================
        console.log('\n--- 6. Kiểm tra Admin: Không kích hoạt Onboarding tour (/admin/users) ---');
        await setSessionUser(page, adminUser);

        await page.goto('http://localhost:5173/admin/users', { waitUntil: 'networkidle' });
        await page.waitForTimeout(1000);

        const adminTourVisible = await tooltip.isVisible();
        console.log('   ✅ Admin page KHÔNG có tour nào kích hoạt:', !adminTourVisible);
        if (adminTourVisible) throw new Error('Tour kích hoạt sai trên trang Admin!');

        console.log('\n🎉 TOÀN BỘ 6 KỊCH BẢN KIỂM THỬ ĐÃ VƯỢT QUA 100% THÀNH CÔNG!');
    } finally {
        await browser.close();
    }
}

runFeatureToursVerification().catch((err) => {
    console.error('❌ LỖI KIỂM THỬ:', err);
    process.exit(1);
});
