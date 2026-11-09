/**
 * BROWSER E2E DEMO & VIDEO RECORDING SCRIPT:
 * TOÀN BỘ CÁC TÍNH NĂNG ONBOARDING & FEATURE QUICK TOURS
 * 
 * - Quay video chất lượng cao WebM (1440x900) với con trỏ chuột mô phỏng người thật
 * - Chụp 28 ảnh chụp màn hình độ phân giải cao tại từng bước
 * - Bao quát đầy đủ: Tour 6 bước tạo két, 7 Feature Quick Tours, Replay On-Demand, và Kiểm tra Admin
 */

const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');
const {
    attachHumanCursor,
    humanMoveAndClick,
    humanType,
    humanPause
} = require('./human_interactions');

const BASE_URL = 'http://localhost:5173';
const ARTIFACT_DIR = 'C:/Users/shin/.gemini/antigravity/brain/84420023-c595-401f-a9d9-e40238fb8c21';
const VIDEO_DIR = path.resolve(__dirname, '..', 'test-artifacts', 'videos', 'onboarding-demo');

if (!fs.existsSync(VIDEO_DIR)) fs.mkdirSync(VIDEO_DIR, { recursive: true });

// Cấu hình Mock Users cho các vai trò
const ownerUser = {
    id: '11111111-2222-3333-4444-555555555555',
    email: 'chutaisan@legacyvault.test',
    name: 'Trần Long Vân (Chủ Két)',
    roles: ['OWNER'],
    role: 'OWNER'
};

const beneficiaryUser = {
    id: '22222222-2222-3333-4444-555555555555',
    email: 'thuhuong@legacyvault.test',
    name: 'Nguyễn Hoàng Nam (Người Thụ Hưởng)',
    roles: ['BENEFICIARY'],
    role: 'BENEFICIARY'
};

const executorUser = {
    id: '33333333-2222-3333-4444-555555555555',
    email: 'thucthi@legacyvault.test',
    name: 'Lê Văn Cường (Người Thực Thi)',
    roles: ['EXECUTOR'],
    role: 'EXECUTOR'
};

const verifierUser = {
    id: '44444444-2222-3333-4444-555555555555',
    email: 'thamdinh@legacyvault.test',
    name: 'LS. Phạm Minh Tuấn (Thẩm Định Viên)',
    roles: ['LEGAL_VERIFIER'],
    role: 'LEGAL_VERIFIER'
};

const adminUser = {
    id: '55555555-2222-3333-4444-555555555555',
    email: 'admin@legacyvault.test',
    name: 'Admin Hệ Thống',
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
        if (url.includes('/api/plans/my-subscription')) {
            return route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify({ vip: true, planName: 'VIP_UNLIMITED' })
            });
        }
        // Chi tiết két sắt mẫu v-demo-1
        if (url.includes('/api/vaults/v-demo-1/assets')) {
            return route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify([
                    { id: 'ast-1', name: 'Tài khoản Chứng khoán SSI', type: 'FINANCIAL', details: 'Mã TK: 001C889988', createdAt: '2026-02-01T08:00:00' },
                    { id: 'ast-2', name: 'Ví Crypto Lạnh Ledger Nano X', type: 'CRYPTO', details: 'ETH / BTC Cold Storage', createdAt: '2026-02-15T09:00:00' }
                ])
            });
        }
        if (url.includes('/api/vaults/v-demo-1/documents') || url.includes('/api/vaults/v-demo-1/legacy-messages')) {
            return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([]) });
        }
        if (url.includes('/api/vaults/v-demo-1')) {
            return route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify({
                    id: 'v-demo-1',
                    name: 'Két Di Sản Gia Đình & Quỹ Đầu Tư',
                    description: 'Két sắt số bảo mật chuẩn quân sự AES-256 bảo vệ tài sản số chuyển giao thế hệ sau',
                    status: 'ACTIVE',
                    createdAt: '2026-01-01T08:00:00',
                    updatedAt: '2026-11-01T10:00:00',
                    totalAssets: 2,
                    totalDocuments: 1,
                    modifiable: true,
                    beneficiaries: [
                        { id: 'ben-1', fullName: 'Nguyễn Hoàng Nam', email: 'nam.nguyen@legacyvault.test', relationship: 'Con trai cả', percentage: 60 },
                        { id: 'ben-2', fullName: 'Nguyễn Mai Anh', email: 'maianh@legacyvault.test', relationship: 'Con gái út', percentage: 40 }
                    ]
                })
            });
        }
        if (url.includes('/api/vaults')) {
            return route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify([])
            });
        }
        // Nhật ký hoạt động
        if (url.includes('/api/activity') || url.includes('/api/logs')) {
            return route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify({
                    content: [
                        { id: 'log-1', createdAt: '2026-11-09T08:30:00', action: 'VAULT_ACCESS', actorName: 'Trần Long Vân', description: 'Đăng nhập và điểm danh định kỳ thành công' },
                        { id: 'log-2', createdAt: '2026-11-08T14:15:00', action: 'ASSET_UPDATE', actorName: 'Trần Long Vân', description: 'Cập nhật khóa bí mật ví lạnh Crypto' }
                    ],
                    items: [],
                    totalElements: 2,
                    totalPages: 1
                })
            });
        }
        // Công tắc tử thần DMS
        if (url.includes('/api/dms')) {
            return route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify({
                    status: 'ACTIVE',
                    checkInIntervalDays: 30,
                    gracePeriodDays: 7,
                    lastCheckIn: '2026-11-09T08:30:00',
                    nextCheckInDeadline: '2026-12-09T08:30:00',
                    confirmers: [
                        { id: 'c-1', name: 'BS. Trần Văn Hưng', email: 'hung.tran@bvchoray.vn', status: 'PENDING' }
                    ]
                })
            });
        }
        // Người thực thi di chúc
        if (url.includes('/api/executors')) {
            return route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify({
                    primaryExecutor: {
                        name: 'Lê Văn Cường',
                        email: 'cuong.le@thucthi.test',
                        phone: '0912345678',
                        status: 'ASSIGNED'
                    },
                    backupExecutor: {
                        name: 'LS. Nguyễn Đức Trọng',
                        email: 'trong.lawyer@vanphongluat.vn',
                        phone: '0987654321',
                        status: 'PENDING'
                    }
                })
            });
        }
        // Beneficiary danh sách két
        if (url.includes('/api/beneficiary/vaults')) {
            return route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify([
                    {
                        vaultId: 'v-demo-1',
                        vaultName: 'Két Di Sản Gia Đình & Quỹ Đầu Tư',
                        ownerName: 'Trần Long Vân',
                        status: 'CLAIMABLE',
                        claimedAssets: 0,
                        totalAssets: 2
                    }
                ])
            });
        }
        // Executor danh sách hồ sơ
        if (url.includes('/api/executor/estates') || url.includes('/api/executor')) {
            return route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify([
                    {
                        id: 'case-01',
                        ownerName: 'Trần Long Vân',
                        dmsTriggeredAt: '2026-11-07T09:00:00',
                        status: 'AWAITING_VERIFICATION',
                        vaultCount: 2,
                        proofSubmitted: true
                    }
                ])
            });
        }
        // Verifier yêu cầu thẩm định
        if (url.includes('/api/verifier')) {
            return route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify([
                    {
                        requestId: 'req-01',
                        ownerName: 'Trần Long Vân',
                        documentType: 'TRICH_LUC_KHAI_TU',
                        aiConfidence: 98,
                        status: 'PENDING_REVIEW',
                        submittedAt: '2026-11-08T10:00:00'
                    }
                ])
            });
        }
        return route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ success: true, data: [] })
        });
    });
}

// Cập nhật banner HUD trên đầu màn hình video để người xem biết đang ở kịch bản nào
async function setHudBanner(page, title, subtitle) {
    await page.evaluate(({ t, s }) => {
        let banner = document.getElementById('demo-hud-banner');
        if (!banner) {
            banner = document.createElement('div');
            banner.id = 'demo-hud-banner';
            banner.style.position = 'fixed';
            banner.style.top = '12px';
            banner.style.left = '50%';
            banner.style.transform = 'translateX(-50%)';
            banner.style.zIndex = '2147483640';
            banner.style.backgroundColor = 'rgba(15, 23, 42, 0.95)';
            banner.style.border = '1.5px solid rgba(251, 191, 36, 0.6)';
            banner.style.borderRadius = '9999px';
            banner.style.padding = '8px 24px';
            banner.style.boxShadow = '0 10px 25px -5px rgba(0, 0, 0, 0.8), 0 0 20px rgba(251, 191, 36, 0.3)';
            banner.style.display = 'flex';
            banner.style.flexDirection = 'column';
            banner.style.alignItems = 'center';
            banner.style.pointerEvents = 'none';
            banner.style.fontFamily = 'system-ui, sans-serif';
            banner.style.transition = 'all 0.3s ease-in-out';
            banner.innerHTML = `
                <div id="demo-hud-title" style="font-size: 13px; font-weight: 800; color: #fbbf24; text-transform: uppercase; letter-spacing: 0.08em; display: flex; align-items: center; gap: 8px;">
                    <span style="display:inline-block; width:8px; height:8px; border-radius:50%; background-color:#10b981; box-shadow:0 0 8px #10b981;"></span>
                    <span>${t}</span>
                </div>
                <div id="demo-hud-sub" style="font-size: 11px; color: #cbd5e1; margin-top: 2px;">${s}</div>
            `;
            document.body.appendChild(banner);
        } else {
            document.getElementById('demo-hud-title').innerHTML = `
                <span style="display:inline-block; width:8px; height:8px; border-radius:50%; background-color:#10b981; box-shadow:0 0 8px #10b981;"></span>
                <span>${t}</span>
            `;
            document.getElementById('demo-hud-sub').textContent = s;
        }
    }, { t: title, s: subtitle });
}

async function setSessionUser(page, user, onboardingStatus = 'experienced') {
    currentUser = user;
    await page.evaluate(({ u, status }) => {
        localStorage.setItem('token', 'mock-valid-token');
        localStorage.setItem('lv_user', JSON.stringify(u));
        localStorage.setItem('i18nextLng', 'vi');
        localStorage.setItem('lv_lang', 'vi');
        if (status) {
            localStorage.setItem('lv_onboarding_status', status);
        } else {
            localStorage.removeItem('lv_onboarding_status');
        }
    }, { u: user, status: onboardingStatus });
}

(async () => {
    console.log('🎥 BẮT ĐẦU QUÁ TRÌNH QUAY VIDEO E2E BROWSER TOÀN BỘ CÁC TÍNH NĂNG ONBOARDING...');
    
    const browser = await chromium.launch({
        executablePath: 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
        headless: true
    });

    const context = await browser.newContext({
        locale: 'vi-VN',
        viewport: { width: 1440, height: 900 },
        recordVideo: {
            dir: VIDEO_DIR,
            size: { width: 1440, height: 900 }
        }
    });

    const page = await context.newPage();
    await attachHumanCursor(page);
    await setupApiMocks(page);

    try {
        // =========================================================================
        // PHẦN 1: HỘP THOẠI CÂU HỎI TÂN THỦ & TOUR 6 BƯỚC KHỞI TẠO KÉT MẪU
        // =========================================================================
        console.log('\n🎬 [1/9] QUAY: HỘP THOẠI HỎI LẦN ĐẦU & 6 BƯỚC TẠO KÉT SẮT');
        await page.goto(`${BASE_URL}/login`, { waitUntil: 'domcontentloaded' });
        // Xóa hoàn toàn trạng thái để kích hoạt câu hỏi ban đầu
        await setSessionUser(page, ownerUser, null);
        await page.evaluate(() => localStorage.removeItem('lv_tour_step'));

        await page.goto(`${BASE_URL}/owner/vaults`, { waitUntil: 'networkidle' });
        await page.waitForTimeout(800);
        await setHudBanner(page, 'KỊCH BẢN 1: HỎI TÂN THỦ LẦN ĐẦU ĐĂNG NHẬP', 'Hộp thoại dẫn dắt người dùng mới bắt đầu hành trình bảo vệ di sản số');

        // Chờ hộp thoại
        const promptDialog = page.locator('#first-time-prompt-title');
        await promptDialog.waitFor({ state: 'visible', timeout: 5000 });
        await humanPause(page, 1000);
        await page.screenshot({ path: path.join(ARTIFACT_DIR, '01_prompt_newbie_question.png') });
        console.log('   📸 Đã chụp: 01_prompt_newbie_question.png');

        // Bấm "Tôi chưa từng dùng (Bắt đầu hướng dẫn)"
        await setHudBanner(page, 'TÂN THỦ BẮT ĐẦU TOUR KHỞI TẠO KÉT SẮT', 'Kích hoạt Spotlight Tour 6 bước chi tiết');
        await humanMoveAndClick(page, 'button:has-text("Tôi chưa từng dùng")', { pauseBefore: 600, pauseAfter: 1200 });

        // BƯỚC 1/6: Highlight nút "+ Tạo két mới"
        await setHudBanner(page, 'BƯỚC 1/6: CHỌN NÚT TẠO KÉT MỚI', 'Làm tối giao diện, chỉ hiển thị nổi bật nút Tạo két sắt');
        const tooltip = page.locator('#spotlight-tour-tooltip');
        await tooltip.waitFor({ state: 'visible', timeout: 5000 });
        await humanPause(page, 1000);
        await page.screenshot({ path: path.join(ARTIFACT_DIR, '02_vault_step1_new_btn.png') });
        console.log('   📸 Đã chụp: 02_vault_step1_new_btn.png');
        await humanMoveAndClick(page, '#spotlight-tour-tooltip button:has-text("Bắt đầu tạo két sắt")', { pauseBefore: 400, pauseAfter: 1400 });

        // BƯỚC 2/6: Điền Thông tin két sắt (/owner/vaults/new)
        await setHudBanner(page, 'BƯỚC 2/6: THIẾT LẬP THÔNG TIN KÉT SẮT', 'Đặt tên gọi gợi nhớ và mô tả mục đích bàn giao di sản');
        await tooltip.waitFor({ state: 'visible', timeout: 5000 });
        await humanPause(page, 1000);
        await page.screenshot({ path: path.join(ARTIFACT_DIR, '03_vault_step2_info.png') });
        console.log('   📸 Đã chụp: 03_vault_step2_info.png');
        await humanMoveAndClick(page, '#spotlight-tour-tooltip button:has-text("Tiếp tục")', { pauseBefore: 400, pauseAfter: 1200 });

        // BƯỚC 3/6: Người thụ hưởng
        await setHudBanner(page, 'BƯỚC 3/6: CHỈ ĐỊNH NGƯỜI THỤ HƯỞNG', 'Gán quyền thừa kế minh bạch theo tỷ lệ phần trăm');
        await humanPause(page, 1000);
        await page.screenshot({ path: path.join(ARTIFACT_DIR, '04_vault_step3_beneficiary.png') });
        console.log('   📸 Đã chụp: 04_vault_step3_beneficiary.png');
        await humanMoveAndClick(page, '#spotlight-tour-tooltip button:has-text("Tiếp tục")', { pauseBefore: 400, pauseAfter: 1200 });

        // BƯỚC 4/6: Chọn loại tài sản
        await setHudBanner(page, 'BƯỚC 4/6: PHÂN LOẠI TÀI SẢN SỐ', 'Hỗ trợ tài khoản ngân hàng, ví Crypto, mạng xã hội, giấy tờ pháp lý');
        await humanPause(page, 1000);
        await page.screenshot({ path: path.join(ARTIFACT_DIR, '05_vault_step4_asset_type.png') });
        console.log('   📸 Đã chụp: 05_vault_step4_asset_type.png');
        await humanMoveAndClick(page, '#spotlight-tour-tooltip button:has-text("Tiếp tục")', { pauseBefore: 400, pauseAfter: 1200 });

        // BƯỚC 5/6: Nhập thông tin tài sản
        await setHudBanner(page, 'BƯỚC 5/6: MÃ HÓA CHI TIẾT TÀI SẢN', 'Mọi mật khẩu, private key đều được bảo vệ bởi AES-256');
        await humanPause(page, 1000);
        await page.screenshot({ path: path.join(ARTIFACT_DIR, '06_vault_step5_asset_details.png') });
        console.log('   📸 Đã chụp: 06_vault_step5_asset_details.png');
        await humanMoveAndClick(page, '#spotlight-tour-tooltip button:has-text("Tiếp tục")', { pauseBefore: 400, pauseAfter: 1200 });

        // BƯỚC 6/6: Hoàn tất tạo két
        await setHudBanner(page, 'BƯỚC 6/6: HOÀN TẤT KHỞI TẠO KÉT SẮT', 'Bấm nút hoàn tất để niêm phong két sắt');
        await humanPause(page, 1000);
        await page.screenshot({ path: path.join(ARTIFACT_DIR, '07_vault_step6_submit.png') });
        console.log('   📸 Đã chụp: 07_vault_step6_submit.png');
        await humanMoveAndClick(page, '#spotlight-tour-tooltip button:has-text("Hoàn tất hướng dẫn")', { pauseBefore: 400, pauseAfter: 1400 });

        // MODAL CHÚC MỪNG
        await setHudBanner(page, 'HOÀN TẤT NHẬP MÔN LEGACYVAULT', 'Huy hiệu thành tựu và thông điệp chúc mừng hoàn thành tour');
        const congrats = page.locator('#congrats-modal-title');
        await congrats.waitFor({ state: 'visible', timeout: 5000 });
        await humanPause(page, 1200);
        await page.screenshot({ path: path.join(ARTIFACT_DIR, '08_vault_congrats_modal.png') });
        console.log('   📸 Đã chụp: 08_vault_congrats_modal.png');
        await humanMoveAndClick(page, 'button:has-text("Bắt đầu sử dụng ngay")', { pauseBefore: 400, pauseAfter: 1200 });

        // =========================================================================
        // PHẦN 2: FEATURE TOUR - NHẬT KÝ HOẠT ĐỘNG (/owner/activity)
        // =========================================================================
        console.log('\n🎬 [2/9] QUAY: FEATURE TOUR NHẬT KÝ HOẠT ĐỘNG & REPLAY');
        await page.goto(`${BASE_URL}/owner/activity`, { waitUntil: 'networkidle' });
        await page.waitForTimeout(600);
        await setHudBanner(page, 'TÍNH NĂNG 1: NHẬT KÝ HOẠT ĐỘNG & TRUY CẬP TÀI SẢN', 'Bước 1/3: Giới thiệu thanh Tabs chuyển đổi giữa nhật ký DMS và lượt truy cập');
        
        await tooltip.waitFor({ state: 'visible', timeout: 5000 });
        await humanPause(page, 1200);
        await page.screenshot({ path: path.join(ARTIFACT_DIR, '09_activity_step1_tabs.png') });
        console.log('   📸 Đã chụp: 09_activity_step1_tabs.png');

        // Bước 2: Bộ lọc thời gian
        await setHudBanner(page, 'TÍNH NĂNG 1: NHẬT KÝ HOẠT ĐỘNG', 'Bước 2/3: Bộ lọc thời gian tra cứu 7 ngày, 30 ngày hoặc toàn bộ');
        await humanMoveAndClick(page, '#spotlight-tour-tooltip button:has-text("Tiếp tục")', { pauseBefore: 400, pauseAfter: 1200 });
        await page.screenshot({ path: path.join(ARTIFACT_DIR, '10_activity_step2_filter.png') });
        console.log('   📸 Đã chụp: 10_activity_step2_filter.png');

        // Bước 3: Danh sách hoạt động
        await setHudBanner(page, 'TÍNH NĂNG 1: NHẬT KÝ HOẠT ĐỘNG', 'Bước 3/3: Bảng danh sách chi tiết các lượt truy cập được lưu vết bất biến');
        await humanMoveAndClick(page, '#spotlight-tour-tooltip button:has-text("Tiếp tục")', { pauseBefore: 400, pauseAfter: 1200 });
        await page.screenshot({ path: path.join(ARTIFACT_DIR, '11_activity_step3_table.png') });
        console.log('   📸 Đã chụp: 11_activity_step3_table.png');

        // Bấm "Đã hiểu" kết thúc tour
        await humanMoveAndClick(page, '#spotlight-tour-tooltip button:has-text("Đã hiểu")', { pauseBefore: 400, pauseAfter: 1200 });

        // F5 Reload chứng minh không hiện lại
        await setHudBanner(page, 'F5 TẢI LẠI TRANG: KHÔNG TỰ HIỆN LẠI LÀM PHIỀN', 'Hệ thống đã ghi nhớ người dùng đã hoàn thành');
        await page.reload({ waitUntil: 'networkidle' });
        await humanPause(page, 1000);

        // Mở menu Header Hướng dẫn -> Bấm Replay
        await setHudBanner(page, 'TÍNH NĂNG XEM LẠI THEO NHU CẦU (ON-DEMAND REPLAY)', 'Mở dropdown Hướng dẫn và chọn xem lại hướng dẫn trang hiện tại');
        await humanMoveAndClick(page, 'button:has-text("Hướng dẫn")', { pauseBefore: 400, pauseAfter: 800 });
        await page.screenshot({ path: path.join(ARTIFACT_DIR, '12_activity_replay_menu.png') });
        console.log('   📸 Đã chụp: 12_activity_replay_menu.png');
        await humanMoveAndClick(page, 'button:has-text("Xem lại hướng dẫn tính năng này")', { pauseBefore: 400, pauseAfter: 1000 });
        await humanMoveAndClick(page, '#spotlight-tour-tooltip button[title="Bỏ qua hướng dẫn"]', { pauseBefore: 400, pauseAfter: 800 });

        // =========================================================================
        // PHẦN 3: FEATURE TOUR - DEAD MAN\'S SWITCH (/owner/dms)
        // =========================================================================
        console.log('\n🎬 [3/9] QUAY: FEATURE TOUR DEAD MAN\'S SWITCH (/owner/dms)');
        await page.goto(`${BASE_URL}/owner/dms`, { waitUntil: 'networkidle' });
        await page.waitForTimeout(600);
        await setHudBanner(page, 'TÍNH NĂNG 2: CÔNG TẮC TỬ THẦN (DEAD MAN\'S SWITCH)', 'Bước 1/3: Trạng thái kích hoạt và nhịp tim điểm danh định kỳ');
        
        await tooltip.waitFor({ state: 'visible', timeout: 5000 });
        await humanPause(page, 1200);
        await page.screenshot({ path: path.join(ARTIFACT_DIR, '13_dms_step1_status.png') });
        console.log('   📸 Đã chụp: 13_dms_step1_status.png');

        await setHudBanner(page, 'TÍNH NĂNG 2: DEAD MAN\'S SWITCH', 'Bước 2/3: Cấu hình chu kỳ điểm danh và thời gian ân hạn');
        await humanMoveAndClick(page, '#spotlight-tour-tooltip button:has-text("Tiếp tục")', { pauseBefore: 400, pauseAfter: 1200 });
        await page.screenshot({ path: path.join(ARTIFACT_DIR, '14_dms_step2_interval.png') });
        console.log('   📸 Đã chụp: 14_dms_step2_interval.png');

        await setHudBanner(page, 'TÍNH NĂNG 2: DEAD MAN\'S SWITCH', 'Bước 3/3: Danh sách người xác nhận tử vong (bác sĩ, người thân)');
        await humanMoveAndClick(page, '#spotlight-tour-tooltip button:has-text("Tiếp tục")', { pauseBefore: 400, pauseAfter: 1200 });
        await page.screenshot({ path: path.join(ARTIFACT_DIR, '15_dms_step3_confirmers.png') });
        console.log('   📸 Đã chụp: 15_dms_step3_confirmers.png');
        await humanMoveAndClick(page, '#spotlight-tour-tooltip button:has-text("Đã hiểu")', { pauseBefore: 400, pauseAfter: 1000 });

        // =========================================================================
        // PHẦN 4: FEATURE TOUR - NGƯỜI THỰC THI DI CHÚC (/owner/executors)
        // =========================================================================
        console.log('\n🎬 [4/9] QUAY: FEATURE TOUR NGƯỜI THỰC THI (/owner/executors)');
        await page.goto(`${BASE_URL}/owner/executors`, { waitUntil: 'networkidle' });
        await page.waitForTimeout(600);
        await setHudBanner(page, 'TÍNH NĂNG 3: NGƯỜI THỰC THI DI CHÚC (EXECUTORS)', 'Bước 1/3: Tổng quan phân công nhiệm vụ và căn cứ pháp lý');
        
        await tooltip.waitFor({ state: 'visible', timeout: 5000 });
        await humanPause(page, 1200);
        await page.screenshot({ path: path.join(ARTIFACT_DIR, '16_executors_step1_overview.png') });
        console.log('   📸 Đã chụp: 16_executors_step1_overview.png');

        await setHudBanner(page, 'TÍNH NĂNG 3: NGƯỜI THỰC THI DI CHÚC', 'Bước 2/3: Chỉ định Người thực thi chính phụ trách nộp giấy chứng tử');
        await humanMoveAndClick(page, '#spotlight-tour-tooltip button:has-text("Tiếp tục")', { pauseBefore: 400, pauseAfter: 1200 });
        await page.screenshot({ path: path.join(ARTIFACT_DIR, '17_executors_step2_primary.png') });
        console.log('   📸 Đã chụp: 17_executors_step2_primary.png');

        await setHudBanner(page, 'TÍNH NĂNG 3: NGƯỜI THỰC THI DI CHÚC', 'Bước 3/3: Chỉ định Người thực thi dự phòng tránh gián đoạn');
        await humanMoveAndClick(page, '#spotlight-tour-tooltip button:has-text("Tiếp tục")', { pauseBefore: 400, pauseAfter: 1200 });
        await page.screenshot({ path: path.join(ARTIFACT_DIR, '18_executors_step3_backup.png') });
        console.log('   📸 Đã chụp: 18_executors_step3_backup.png');
        await humanMoveAndClick(page, '#spotlight-tour-tooltip button:has-text("Đã hiểu")', { pauseBefore: 400, pauseAfter: 1000 });

        // =========================================================================
        // PHẦN 5: FEATURE TOUR - CHI TIẾT KÉT SẮT (/owner/vaults/v-demo-1)
        // =========================================================================
        console.log('\n🎬 [5/9] QUAY: FEATURE TOUR CHI TIẾT KÉT SẮT & AI OCR');
        await page.goto(`${BASE_URL}/owner/vaults/v-demo-1`, { waitUntil: 'networkidle' });
        await page.waitForTimeout(600);
        await setHudBanner(page, 'TÍNH NĂNG 4: CHI TIẾT KÉT SẮT & QUẢN LÝ TÀI SẢN', 'Bước 1/3: Thông tin két, khóa mã hóa AES-256 và trạng thái bảo vệ');
        
        await tooltip.waitFor({ state: 'visible', timeout: 5000 });
        await humanPause(page, 1200);
        await page.screenshot({ path: path.join(ARTIFACT_DIR, '19_vault_detail_step1_meta.png') });
        console.log('   📸 Đã chụp: 19_vault_detail_step1_meta.png');

        await setHudBanner(page, 'TÍNH NĂNG 4: CHI TIẾT KÉT SẮT', 'Bước 2/3: Danh mục tài sản số và Công nghệ trích xuất Gemini AI OCR');
        await humanMoveAndClick(page, '#spotlight-tour-tooltip button:has-text("Tiếp tục")', { pauseBefore: 400, pauseAfter: 1200 });
        await page.screenshot({ path: path.join(ARTIFACT_DIR, '20_vault_detail_step2_assets_ai_ocr.png') });
        console.log('   📸 Đã chụp: 20_vault_detail_step2_assets_ai_ocr.png');

        await setHudBanner(page, 'TÍNH NĂNG 4: CHI TIẾT KÉT SẮT', 'Bước 3/3: Danh sách người thụ hưởng và tỷ lệ phân bổ tài sản');
        await humanMoveAndClick(page, '#spotlight-tour-tooltip button:has-text("Tiếp tục")', { pauseBefore: 400, pauseAfter: 1200 });
        await page.screenshot({ path: path.join(ARTIFACT_DIR, '21_vault_detail_step3_beneficiaries.png') });
        console.log('   📸 Đã chụp: 21_vault_detail_step3_beneficiaries.png');
        await humanMoveAndClick(page, '#spotlight-tour-tooltip button:has-text("Đã hiểu")', { pauseBefore: 400, pauseAfter: 1000 });

        // =========================================================================
        // PHẦN 6: FEATURE TOUR - BENEFICIARY KÉT THỪA KẾ (/beneficiary)
        // =========================================================================
        console.log('\n🎬 [6/9] QUAY: FEATURE TOUR BENEFICIARY KÉT THỪA KẾ');
        await page.goto(`${BASE_URL}/login`, { waitUntil: 'domcontentloaded' });
        await setSessionUser(page, beneficiaryUser, 'experienced');
        await page.goto(`${BASE_URL}/beneficiary`, { waitUntil: 'networkidle' });
        await page.waitForTimeout(600);
        await setHudBanner(page, 'TÍNH NĂNG 5: PHÂN HỆ NGƯỜI THỤ HƯỞNG (BENEFICIARY)', 'Bước 1/2: Hướng dẫn người nhận di sản về quy trình tiếp nhận và mở khóa két');
        
        await tooltip.waitFor({ state: 'visible', timeout: 5000 });
        await humanPause(page, 1200);
        await page.screenshot({ path: path.join(ARTIFACT_DIR, '22_beneficiary_step1_header.png') });
        console.log('   📸 Đã chụp: 22_beneficiary_step1_header.png');

        await setHudBanner(page, 'TÍNH NĂNG 5: PHÂN HỆ NGƯỜI THỤ HƯỞNG', 'Bước 2/2: Danh sách két sắt được người để lại di sản ủy thác bàn giao');
        await humanMoveAndClick(page, '#spotlight-tour-tooltip button:has-text("Tiếp tục")', { pauseBefore: 400, pauseAfter: 1200 });
        await page.screenshot({ path: path.join(ARTIFACT_DIR, '23_beneficiary_step2_vaults.png') });
        console.log('   📸 Đã chụp: 23_beneficiary_step2_vaults.png');
        await humanMoveAndClick(page, '#spotlight-tour-tooltip button:has-text("Đã hiểu")', { pauseBefore: 400, pauseAfter: 1000 });

        // =========================================================================
        // PHẦN 7: FEATURE TOUR - EXECUTOR BẢNG HỒ SƠ THỰC THI (/executor/home)
        // =========================================================================
        console.log('\n🎬 [7/9] QUAY: FEATURE TOUR EXECUTOR HỒ SƠ THỰC THI');
        await page.goto(`${BASE_URL}/login`, { waitUntil: 'domcontentloaded' });
        await setSessionUser(page, executorUser, 'experienced');
        await page.goto(`${BASE_URL}/executor/home`, { waitUntil: 'networkidle' });
        await page.waitForTimeout(600);
        await setHudBanner(page, 'TÍNH NĂNG 6: PHÂN HỆ NGƯỜI THỰC THI (EXECUTOR)', 'Bước 1/2: Thống kê số lượng hồ sơ di chúc được ủy quyền');
        
        await tooltip.waitFor({ state: 'visible', timeout: 5000 });
        await humanPause(page, 1200);
        await page.screenshot({ path: path.join(ARTIFACT_DIR, '24_executor_step1_stats.png') });
        console.log('   📸 Đã chụp: 24_executor_step1_stats.png');

        await setHudBanner(page, 'TÍNH NĂNG 6: PHÂN HỆ NGƯỜI THỰC THI', 'Bước 2/2: Danh sách các ca di sản cần nộp bằng chứng tử để kích hoạt bàn giao');
        await humanMoveAndClick(page, '#spotlight-tour-tooltip button:has-text("Tiếp tục")', { pauseBefore: 400, pauseAfter: 1200 });
        await page.screenshot({ path: path.join(ARTIFACT_DIR, '25_executor_step2_cases.png') });
        console.log('   📸 Đã chụp: 25_executor_step2_cases.png');
        await humanMoveAndClick(page, '#spotlight-tour-tooltip button:has-text("Đã hiểu")', { pauseBefore: 400, pauseAfter: 1000 });

        // =========================================================================
        // PHẦN 8: FEATURE TOUR - LEGAL VERIFIER BẢNG THẨM ĐỊNH (/verifier)
        // =========================================================================
        console.log('\n🎬 [8/9] QUAY: FEATURE TOUR LEGAL VERIFIER THẨM ĐỊNH PHÁP LÝ');
        await page.goto(`${BASE_URL}/login`, { waitUntil: 'domcontentloaded' });
        await setSessionUser(page, verifierUser, 'experienced');
        await page.goto(`${BASE_URL}/verifier`, { waitUntil: 'networkidle' });
        await page.waitForTimeout(600);
        await setHudBanner(page, 'TÍNH NĂNG 7: BẢNG THẨM ĐỊNH PHÁP LÝ (LEGAL VERIFIER)', 'Bước 1/2: Lối tắt tra cứu hồ sơ và lịch sử thẩm định');
        
        await tooltip.waitFor({ state: 'visible', timeout: 5000 });
        await humanPause(page, 1200);
        await page.screenshot({ path: path.join(ARTIFACT_DIR, '26_verifier_step1_history_link.png') });
        console.log('   📸 Đã chụp: 26_verifier_step1_history_link.png');

        await setHudBanner(page, 'TÍNH NĂNG 7: BẢNG THẨM ĐỊNH PHÁP LÝ', 'Bước 2/2: Hàng đợi thẩm định hồ sơ qua đời kèm kết quả phân tích Gemini AI OCR');
        await humanMoveAndClick(page, '#spotlight-tour-tooltip button:has-text("Tiếp tục")', { pauseBefore: 400, pauseAfter: 1200 });
        await page.screenshot({ path: path.join(ARTIFACT_DIR, '27_verifier_step2_queue.png') });
        console.log('   📸 Đã chụp: 27_verifier_step2_queue.png');
        await humanMoveAndClick(page, '#spotlight-tour-tooltip button:has-text("Đã hiểu")', { pauseBefore: 400, pauseAfter: 1000 });

        // =========================================================================
        // PHẦN 9: KIỂM TRA LOẠI TRỪ ADMIN (/admin/users)
        // =========================================================================
        console.log('\n🎬 [9/9] QUAY: PHÂN HỆ QUẢN TRỊ VIÊN ADMIN (LOẠI TRỪ KHÔNG TOUR)');
        await page.goto(`${BASE_URL}/login`, { waitUntil: 'domcontentloaded' });
        await setSessionUser(page, adminUser, 'experienced');
        await page.goto(`${BASE_URL}/admin/users`, { waitUntil: 'networkidle' });
        await page.waitForTimeout(600);
        await setHudBanner(page, 'PHÂN HỆ ADMIN: LOẠI TRỪ HOÀN TOÀN', 'Trang Quản trị viên luôn sạch sẽ, không có bất kỳ tour làm phiền nào');
        await humanPause(page, 1500);
        await page.screenshot({ path: path.join(ARTIFACT_DIR, '28_admin_clean_no_tour.png') });
        console.log('   📸 Đã chụp: 28_admin_clean_no_tour.png');

        await setHudBanner(page, 'KẾT THÚC KIỂM THỬ: 100% THÀNH CÔNG RỰC RỠ', 'Toàn bộ tính năng Onboarding & Feature Tours hoạt động hoàn hảo');
        await humanPause(page, 1800);

        console.log('\n🎉 ĐÃ HOÀN THÀNH TOÀN BỘ QUÁ TRÌNH QUAY VIDEO VÀ CHỤP ẢNH BẰNG CHỨNG!');
    } finally {
        const video = page.video();
        await context.close();
        await browser.close();
        if (video) {
            const videoPath = await video.path();
            const targetVideo = path.join(ARTIFACT_DIR, 'ONBOARDING_FEATURE_TOURS_DEMO.webm');
            fs.copyFileSync(videoPath, targetVideo);
            console.log('🎬 ĐÃ LƯU VIDEO BROWSER THÀNH CÔNG TẠI:', targetVideo);
        }
    }
})();
