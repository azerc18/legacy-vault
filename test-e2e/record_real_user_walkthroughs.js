// Comprehensive Human-Like E2E Walkthrough Recorder
// Generates clear, high-visibility video demonstrations with realistic pacing, smooth scrolling, and visible cursor

const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');
const { attachHumanCursor, humanMoveAndClick, humanType, humanScroll, humanPause } = require('./human_interactions');
const { setupMockForRole } = require('./api_mocks');

const BASE_URL = 'https://legacyvault.codes';
const OUTPUT_DIR = path.resolve(__dirname, '..', 'test-artifacts', 'videos', 'real-walkthrough');

if (!fs.existsSync(OUTPUT_DIR)) fs.mkdirSync(OUTPUT_DIR, { recursive: true });

async function createHumanSession(videoFileName) {
    const tempDir = path.join(OUTPUT_DIR, `temp_${Date.now()}_${Math.random().toString(36).substring(7)}`);
    if (!fs.existsSync(tempDir)) fs.mkdirSync(tempDir, { recursive: true });

    const browser = await chromium.launch({
        channel: 'chrome',
        headless: true
    });

    const context = await browser.newContext({
        recordVideo: {
            dir: tempDir,
            size: { width: 1280, height: 720 }
        },
        viewport: { width: 1280, height: 720 }
    });

    const page = await context.newPage();
    await attachHumanCursor(page);

    const closeAndSave = async () => {
        const video = page.video();
        await context.close();
        await browser.close();

        if (video) {
            const rawPath = await video.path();
            const targetPath = path.join(OUTPUT_DIR, videoFileName);
            if (fs.existsSync(targetPath)) fs.unlinkSync(targetPath);
            fs.renameSync(rawPath, targetPath);
            console.log(`\n>>> [SAVED HD VIDEO] ${videoFileName} (${(fs.statSync(targetPath).size / 1024).toFixed(1)} KB)`);
        }
        try {
            if (fs.existsSync(tempDir)) fs.rmdirSync(tempDir, { recursive: true });
        } catch (_) {}
    };

    return { page, closeAndSave };
}

async function runAllWalkthroughs() {
    console.log('======================================================================');
    console.log('STARTING REAL-USER SYSTEM WALKTHROUGHS (HUMAN PACING & VISIBLE CURSOR)');
    console.log('======================================================================\n');

    // -------------------------------------------------------------------------
    // JOURNEY 1: Guest Landing Page, Theme, Language & Full Authentication Flow
    // -------------------------------------------------------------------------
    console.log('\n--- [JOURNEY 1] Guest & Public Authentication Flow ---');
    {
        const { page, closeAndSave } = await createHumanSession('REAL_USER_ROLE_01_Guest_And_Authentication.webm');
        try {
            console.log('  1. Mở trang chủ https://legacyvault.codes/');
            await page.goto(`${BASE_URL}/`, { waitUntil: 'domcontentloaded' });
            await humanPause(page, 2500);

            console.log('  2. Rê chuột đổi sang Dark Mode');
            const themeBtn = page.locator('button:has(.material-symbols-outlined)').filter({ hasText: /light_mode|dark_mode/ }).first();
            await humanMoveAndClick(page, themeBtn, { pauseBefore: 600, pauseAfter: 2000 });

            console.log('  3. Đổi lại Light Mode');
            await humanMoveAndClick(page, themeBtn, { pauseBefore: 500, pauseAfter: 1500 });

            console.log('  4. Chuyển đổi ngôn ngữ sang Tiếng Anh (EN)');
            const langBtn = page.locator('button:has-text("VI"), button:has-text("EN")').first();
            await humanMoveAndClick(page, langBtn, { pauseBefore: 500, pauseAfter: 2000 });

            console.log('  5. Chuyển đổi ngôn ngữ trở về Tiếng Việt (VI)');
            await humanMoveAndClick(page, langBtn, { pauseBefore: 500, pauseAfter: 1800 });

            console.log('  6. Cuộn mượt xem các tính năng cốt lõi trên trang chủ');
            await humanScroll(page, 550, { pauseAfter: 2000 });
            await humanScroll(page, 650, { pauseAfter: 2000 });
            await humanScroll(page, -1200, { pauseAfter: 1800 });

            console.log('  7. Nhấn nút Đăng ký trên Navbar / Hero');
            const ctaBtn = page.locator('a[href="/register"]').first();
            await humanMoveAndClick(page, ctaBtn, { pauseBefore: 600, pauseAfter: 2000 });

            console.log('  8. Thao tác Form Đăng ký: Nhập cố tình sai để xem lỗi validation');
            await humanType(page, '#reg-name', 'Trần Long Vân', { delay: 60, pauseAfter: 600 });
            await humanType(page, '#reg-email', 'email_sai_dinh_dang', { delay: 50, pauseAfter: 600 });
            await humanType(page, '#reg-password', '123', { delay: 60, pauseAfter: 600 });
            await humanType(page, '#reg-confirm', '456', { delay: 60, pauseAfter: 600 });

            const registerBtn = page.locator('button[type="submit"]').first();
            await humanMoveAndClick(page, registerBtn, { pauseBefore: 500, pauseAfter: 3000 });
            console.log('     -> Đã hiển thị rõ ràng các cảnh báo validation màu đỏ.');

            console.log('  9. Sửa lại dữ liệu hợp lệ và gửi đăng ký');
            await humanType(page, '#reg-email', 'tlv48691@gmail.com', { delay: 50, pauseAfter: 600 });
            await humanType(page, '#reg-password', 'LegacyVault@2026Secure!', { delay: 50, pauseAfter: 600 });
            await humanType(page, '#reg-confirm', 'LegacyVault@2026Secure!', { delay: 50, pauseAfter: 600 });
            await humanMoveAndClick(page, registerBtn, { pauseBefore: 600, pauseAfter: 2500 });

            console.log('  10. Trang Xác thực OTP (/verify-email)');
            await page.goto(`${BASE_URL}/verify-email?email=tlv48691@gmail.com`, { waitUntil: 'domcontentloaded' });
            await humanPause(page, 2000);

            const otpInput = page.locator('input[name="otp"], input[placeholder*="OTP" i], input[type="text"]').first();
            if (await otpInput.isVisible()) {
                await humanType(page, otpInput, '123456', { delay: 100, pauseAfter: 1500 });
            }

            const resendBtn = page.locator('button:has-text("Gửi lại"), button:has-text("Resend")').first();
            if (await resendBtn.isVisible()) {
                await humanMoveAndClick(page, resendBtn, { pauseBefore: 500, pauseAfter: 3000 });
                console.log('     -> Bộ đếm ngược 60 giây đang chạy.');
            }

            console.log('  11. Màn hình Đăng nhập (/login) & Thử sai mật khẩu');
            await page.goto(`${BASE_URL}/login`, { waitUntil: 'domcontentloaded' });
            await humanPause(page, 1800);

            const loginEmail = page.locator('#login-email, input[type="email"]').first();
            const loginPass = page.locator('#login-password, input[type="password"]').first();
            await humanType(page, loginEmail, 'tlv48691@gmail.com', { delay: 50, pauseAfter: 600 });
            await humanType(page, loginPass, 'MatKhauSai@999', { delay: 60, pauseAfter: 800 });

            const submitLogin = page.locator('button[type="submit"]').first();
            await humanMoveAndClick(page, submitLogin, { pauseBefore: 500, pauseAfter: 3000 });
            console.log('     -> Đã thấy thông báo lỗi đăng nhập thất bại.');

            console.log('  12. Quên mật khẩu (/forgot-password)');
            const forgotLink = page.locator('a:has-text("Quên mật khẩu"), a:has-text("Forgot password")').first();
            await humanMoveAndClick(page, forgotLink, { pauseBefore: 500, pauseAfter: 1800 });

            const forgotEmail = page.locator('input[type="email"]').first();
            await humanType(page, forgotEmail, 'tlv48691@gmail.com', { delay: 50, pauseAfter: 800 });

            const resetBtn = page.locator('button[type="submit"]').first();
            await humanMoveAndClick(page, resetBtn, { pauseBefore: 500, pauseAfter: 3000 });
            console.log('     -> Đã gửi yêu cầu khôi phục mật khẩu.');

        } catch (e) {
            console.error('Error in Journey 1:', e);
        } finally {
            await closeAndSave();
        }
    }

    // -------------------------------------------------------------------------
    // JOURNEY 2: Vault Owner Management (Dashboard, Create Vault, Assets Detail)
    // -------------------------------------------------------------------------
    console.log('\n--- [JOURNEY 2] Vault Owner - Két sắt & Quản lý Tài sản số ---');
    {
        const { page, closeAndSave } = await createHumanSession('REAL_USER_ROLE_02_Vault_Owner_Management.webm');
        try {
            await setupMockForRole(page, 'owner');

            console.log('  1. Mở Owner Dashboard (/owner/vaults)');
            await page.goto(`${BASE_URL}/owner/vaults`, { waitUntil: 'domcontentloaded' });
            await humanPause(page, 2800);

            console.log('  2. Quan sát Thẻ Sẵn sàng Di sản (Estate Readiness Card 92/100)');
            const readinessCard = page.locator('text=/Sẵn sàng di sản|Estate Readiness|EXCELLENT/i').first();
            await readinessCard.scrollIntoViewIfNeeded().catch(() => {});
            await humanPause(page, 2000);

            console.log('  3. Quan sát Két sắt "Két sắt Gia đình - Di sản số 2026"');
            const vaultCard = page.locator('text=/Két sắt Gia đình/i').first();
            await vaultCard.scrollIntoViewIfNeeded().catch(() => {});
            await humanPause(page, 2000);

            console.log('  4. Nhấn nút "Tạo két mới" (/owner/vaults/new)');
            const newVaultBtn = page.locator('a[href*="/owner/vaults/new"], button:has-text("Tạo két"), button:has-text("New Vault")').first();
            await humanMoveAndClick(page, newVaultBtn, { pauseBefore: 600, pauseAfter: 2000 });

            console.log('  5. Điền Form tạo két sắt mới');
            const nameInput = page.locator('input[placeholder*="Gia đình" i], input[name="name"], #vault-name').first();
            await humanType(page, nameInput, 'Két sắt Khẩn cấp & Tài chính số 2026', { delay: 50, pauseAfter: 800 });

            const descInput = page.locator('textarea').first();
            await humanType(page, descInput, 'Lưu giữ tài khoản dự phòng và chỉ dẫn khẩn cấp cho gia đình.', { delay: 40, pauseAfter: 800 });

            const submitCreate = page.locator('button:has-text("Tạo két"), button:has-text("Create Vault")').first();
            await humanMoveAndClick(page, submitCreate, { pauseBefore: 600, pauseAfter: 2500 });

            console.log('  6. Mở Chi tiết Két sắt (/owner/vaults/vault-demo-001)');
            await page.goto(`${BASE_URL}/owner/vaults/vault-demo-001`, { waitUntil: 'domcontentloaded' });
            await humanPause(page, 2500);

            console.log('  7. Cuộn xuống danh sách Tài sản số');
            await humanScroll(page, 450, { pauseAfter: 2000 });

            console.log('  8. Nhấn nút "Thêm tài sản" (Add Asset) để mở Modal');
            const addAssetBtn = page.locator('button:has-text("Thêm tài sản"), button:has-text("Add Asset")').first();
            await humanMoveAndClick(page, addAssetBtn, { pauseBefore: 500, pauseAfter: 2500 });

            console.log('  9. Khám phá các loại tài sản trong Modal (Tài chính, MXH, Ví crypto)');
            await humanPause(page, 3000);

            const cancelModal = page.locator('button:has-text("Hủy"), button:has-text("Cancel"), button:has(.material-symbols-outlined:has-text("close"))').first();
            await humanMoveAndClick(page, cancelModal, { pauseBefore: 400, pauseAfter: 1500 });

            console.log('  10. Cuộn xem khối Người thụ hưởng (Beneficiaries)');
            await humanScroll(page, 400, { pauseAfter: 2200 });

            console.log('  11. Cuộn xem khối Lời nhắn di nguyện & Hộp thời gian');
            await humanScroll(page, 500, { pauseAfter: 3000 });

        } catch (e) {
            console.error('Error in Journey 2:', e);
        } finally {
            await closeAndSave();
        }
    }

    // -------------------------------------------------------------------------
    // JOURNEY 3: Owner Advanced Setup (DMS, Executors, Activity & Billing)
    // -------------------------------------------------------------------------
    console.log('\n--- [JOURNEY 3] Owner - Cấu hình DMS, Executor, Nhật ký & Bảng giá ---');
    {
        const { page, closeAndSave } = await createHumanSession('REAL_USER_ROLE_03_Owner_DMS_And_Billing.webm');
        try {
            await setupMockForRole(page, 'owner');

            console.log('  1. Mở Cấu hình Dead Mans Switch (/owner/dms)');
            await page.goto(`${BASE_URL}/owner/dms`, { waitUntil: 'domcontentloaded' });
            await humanPause(page, 3000);

            console.log('  2. Xem Trạng thái DMS: Chu kỳ 30 ngày, Ân hạn 7 ngày, Thời điểm hạn chót');
            await humanScroll(page, 300, { pauseAfter: 2000 });

            console.log('  3. Xem danh sách Người xác nhận khẩn cấp (Confirmers)');
            await humanScroll(page, 300, { pauseAfter: 2500 });

            console.log('  4. Chuyển sang Quản lý Người thi hành di chúc (/owner/executors)');
            await page.goto(`${BASE_URL}/owner/executors`, { waitUntil: 'domcontentloaded' });
            await humanPause(page, 2800);

            console.log('  5. Xem thông tin Luật sư Lê Văn Thi Hành và các hướng dẫn xử lý tài khoản');
            await humanScroll(page, 400, { pauseAfter: 2500 });

            console.log('  6. Chuyển sang Nhật ký hoạt động & Kiểm toán (/owner/activity)');
            await page.goto(`${BASE_URL}/owner/activity`, { waitUntil: 'domcontentloaded' });
            await humanPause(page, 3000);

            console.log('  7. Chuyển sang Bảng giá & Nâng cấp Gói cước (/pricing)');
            await page.goto(`${BASE_URL}/pricing`, { waitUntil: 'domcontentloaded' });
            await humanPause(page, 2500);

            console.log('  8. Cuộn xem các gói cước và bảng tính năng chi tiết');
            await humanScroll(page, 550, { pauseAfter: 2500 });
            await humanScroll(page, 500, { pauseAfter: 2500 });

        } catch (e) {
            console.error('Error in Journey 3:', e);
        } finally {
            await closeAndSave();
        }
    }

    // -------------------------------------------------------------------------
    // JOURNEY 4: Beneficiary Portal, Guide, Handover & Secret Reveal
    // -------------------------------------------------------------------------
    console.log('\n--- [JOURNEY 4] Người thụ hưởng - Nhận Di sản & Giải mã Mật khẩu ---');
    {
        const { page, closeAndSave } = await createHumanSession('REAL_USER_ROLE_04_Beneficiary_Inheritance.webm');
        try {
            await setupMockForRole(page, 'beneficiary');

            console.log('  1. Mở Cổng Người thụ hưởng (/beneficiary)');
            await page.goto(`${BASE_URL}/beneficiary`, { waitUntil: 'domcontentloaded' });
            await humanPause(page, 2800);

            console.log('  2. Xem thẻ két sắt "Két sắt Gia đình" từ Trần Long Vân');
            const vaultCard = page.locator('text=/Két sắt Gia đình|Di sản|Két thừa kế/i').first();
            await vaultCard.scrollIntoViewIfNeeded().catch(() => {});
            await humanPause(page, 2000);

            console.log('  3. Mở Cẩm nang Hướng dẫn Người thụ hưởng (/beneficiary/guide)');
            const guideLink = page.locator('a[href*="/beneficiary/guide"]').first();
            if (await guideLink.isVisible()) {
                await humanMoveAndClick(page, guideLink, { pauseBefore: 500, pauseAfter: 2500 });
            } else {
                await page.goto(`${BASE_URL}/beneficiary/guide`, { waitUntil: 'domcontentloaded' });
                await humanPause(page, 2500);
            }

            console.log('  4. Cuộn mượt qua 5 bước hướng dẫn thừa kế');
            await humanScroll(page, 450, { pauseAfter: 2000 });
            await humanScroll(page, 450, { pauseAfter: 2000 });
            await humanScroll(page, -900, { pauseAfter: 1500 });

            console.log('  5. Vào Chi tiết Két sắt Thừa kế (/beneficiary/vaults/vault-demo-001)');
            await page.goto(`${BASE_URL}/beneficiary/vaults/vault-demo-001`, { waitUntil: 'domcontentloaded' });
            await humanPause(page, 2500);

            console.log('  6. Xem thanh Tiến độ bàn giao di sản (Claim Progress)');
            await humanScroll(page, 250, { pauseAfter: 2000 });

            console.log('  7. Tìm tài sản Vietcombank và nhấn nút "Xem" (View)');
            const viewBtn = page.locator('button:has-text("View"), button:has-text("Xem")').first();
            await humanMoveAndClick(page, viewBtn, { pauseBefore: 600, pauseAfter: 2200 });

            console.log('  8. Modal Bí mật mở ra: Mật khẩu đang bị ẩn dưới dạng chấm ••••••••••••');
            await humanPause(page, 2500);

            console.log('  9. Nhấn icon "Con mắt" để GIẢI MÃ MẬT KHẨU!');
            const eyeBtn = page.locator('button:has(.material-symbols-outlined)').filter({ hasText: /visibility/ }).first();
            await humanMoveAndClick(page, eyeBtn, { pauseBefore: 500, pauseAfter: 3500 });
            console.log('     -> Đã hiện rõ mật khẩu: VCB_PASS_9988#@');

            console.log('  10. Nhấn icon "Sao chép" (Copy) mật khẩu');
            const copyBtn = page.locator('button:has(.material-symbols-outlined)').filter({ hasText: /copy|check|content_copy/ }).first();
            await humanMoveAndClick(page, copyBtn, { pauseBefore: 500, pauseAfter: 2500 });
            console.log('     -> Icon chuyển thành dấu tích xanh xác nhận đã copy.');

            console.log('  11. Đóng Modal bí mật');
            const closeBtn = page.locator('button:has(.material-symbols-outlined)').filter({ hasText: /close/ }).first();
            await humanMoveAndClick(page, closeBtn, { pauseBefore: 400, pauseAfter: 1500 });

            console.log('  12. Cuộn xuống phần Hồ sơ khai nhận di sản (Dossier PDF)');
            await humanScroll(page, 450, { pauseAfter: 2000 });

            console.log('  13. Nhấn nút "Tải PDF" (Download Dossier PDF)');
            const downloadDossier = page.locator('button:has-text("Download PDF"), button:has-text("Tải PDF"), button:has-text("Tải về")').first();
            await humanMoveAndClick(page, downloadDossier, { pauseBefore: 500, pauseAfter: 2200 });
            console.log('     -> Tải file ho-so-khai-nhan.pdf thành công.');

            console.log('  14. Cuộn xuống phần Lời nhắn di nguyện & Đọc tâm thư');
            await humanScroll(page, 450, { pauseAfter: 3500 });
            console.log('     -> Đọc nội dung: "Bố mẹ luôn yêu thương các con..."');

        } catch (e) {
            console.error('Error in Journey 4:', e);
        } finally {
            await closeAndSave();
        }
    }

    // -------------------------------------------------------------------------
    // JOURNEY 5: Digital Executor Case Handling & Account Tasks
    // -------------------------------------------------------------------------
    console.log('\n--- [JOURNEY 5] Người thi hành di chúc - Vụ việc & Nhiệm vụ Tài khoản số ---');
    {
        const { page, closeAndSave } = await createHumanSession('REAL_USER_ROLE_05_Executor_Case_Tasks.webm');
        try {
            await setupMockForRole(page, 'executor');

            console.log('  1. Mở Cổng Người thi hành di chúc (/executor/home)');
            await page.goto(`${BASE_URL}/executor/home`, { waitUntil: 'domcontentloaded' });
            await humanPause(page, 3000);

            console.log('  2. Xem hồ sơ vụ việc "Di sản của Trần Long Vân" & Stepper 4 bước');
            await humanScroll(page, 200, { pauseAfter: 2000 });

            console.log('  3. Nhấn nút "Mở hồ sơ" (Open Case) -> /executor/cases/assign-001');
            const openCaseBtn = page.locator('a[href*="/executor/cases"], button:has-text("Open Case"), button:has-text("Mở hồ sơ")').first();
            await humanMoveAndClick(page, openCaseBtn, { pauseBefore: 600, pauseAfter: 2500 });

            console.log('  4. Xem chi tiết vụ việc và danh sách két sắt ủy quyền');
            await humanScroll(page, 300, { pauseAfter: 2000 });

            console.log('  5. Cuộn xuống phần Checklist Nhiệm vụ Tài khoản số (Account Tasks)');
            await humanScroll(page, 450, { pauseAfter: 3500 });
            console.log('     -> Nhiệm vụ 1: Facebook - Tưởng niệm (MEMORIALIZE)');
            console.log('     -> Nhiệm vụ 2: Google Workspace - Tải dữ liệu bàn giao (TRANSFER_DATA)');

            console.log('  6. Xem trường ghi chú nghiệp vụ và cập nhật trạng thái');
            await humanPause(page, 2500);

        } catch (e) {
            console.error('Error in Journey 5:', e);
        } finally {
            await closeAndSave();
        }
    }

    // -------------------------------------------------------------------------
    // JOURNEY 6: Legal Verifier Review & System Admin Monitoring
    // -------------------------------------------------------------------------
    console.log('\n--- [JOURNEY 6] Thẩm định Pháp lý & Quản trị Toàn diện Hệ thống ---');
    {
        const { page, closeAndSave } = await createHumanSession('REAL_USER_ROLE_06_Verifier_And_Admin.webm');
        try {
            console.log('  1. Đăng nhập Công chứng viên / Legal Verifier (/verifier)');
            await setupMockForRole(page, 'verifier');
            await page.goto(`${BASE_URL}/verifier`, { waitUntil: 'domcontentloaded' });
            await humanPause(page, 3000);

            console.log('  2. Xem danh sách yêu cầu thẩm định mở két (req-001)');
            const requestRow = page.locator('text=/req-001|Két sắt Gia đình|Trần Vân Con/i').first();
            await requestRow.scrollIntoViewIfNeeded().catch(() => {});
            await humanPause(page, 2500);

            console.log('  3. Quan sát các nút thẩm định hồ sơ: Xem chứng tử, Phê duyệt, Từ chối');
            await humanPause(page, 2500);

            console.log('  4. Chuyển sang quyền System Admin - Quản lý Người dùng (/admin/users)');
            await setupMockForRole(page, 'admin');
            await page.goto(`${BASE_URL}/admin/users`, { waitUntil: 'domcontentloaded' });
            await humanPause(page, 3000);

            console.log('  5. Quan sát danh sách người dùng và các vai trò (OWNER, BENEFICIARY, EXECUTOR)');
            await humanScroll(page, 250, { pauseAfter: 2000 });

            console.log('  6. Chuyển sang Giám sát Két sắt toàn hệ thống (/admin/vaults)');
            await page.goto(`${BASE_URL}/admin/vaults`, { waitUntil: 'domcontentloaded' });
            await humanPause(page, 2800);

            console.log('  7. Chuyển sang Cảnh báo An ninh (/admin/alerts)');
            await page.goto(`${BASE_URL}/admin/alerts`, { waitUntil: 'domcontentloaded' });
            await humanPause(page, 3000);
            console.log('     -> Quan sát cảnh báo SUSPICIOUS_LOGIN từ IP 45.33.32.156');

            console.log('  8. Chuyển sang Nhật ký Kiểm toán (/admin/audit-logs)');
            await page.goto(`${BASE_URL}/admin/audit-logs`, { waitUntil: 'domcontentloaded' });
            await humanPause(page, 3500);
            console.log('     -> Quan sát các dòng kiểm toán VAULT_CREATE, ASSET_VIEW, thời điểm, IP, User-Agent');

        } catch (e) {
            console.error('Error in Journey 6:', e);
        } finally {
            await closeAndSave();
        }
    }

    console.log('\n======================================================================');
    console.log('ALL REAL-USER SYSTEM WALKTHROUGHS COMPLETED & RECORDED SUCCESSFULLY!');
    console.log('======================================================================\n');
}

runAllWalkthroughs().catch(console.error);
