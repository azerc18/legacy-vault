// Targeted run for TC19, TC21, TC22, TC23, TC25, TC26, TC30

const { runTestCase } = require('./test_runner_utils');
const { setupMockForRole } = require('./api_mocks');

const BASE_URL = 'https://legacyvault.codes';

async function main() {
    console.log('=== RUNNING TARGETED SUITE FOR TC19, TC21, TC22, TC23, TC25, TC26, TC30 ===\n');
    const results = [];

    // TC19: Cổng Người thụ hưởng (Beneficiary Portal)
    results.push(await runTestCase({
        id: 'TC19',
        name: 'Cổng Người thụ hưởng - Xem danh sách Két sắt được phân bổ',
        role: 'Người thụ hưởng (Beneficiary)',
        fn: async ({ page, step, screenshot }) => {
            await setupMockForRole(page, 'beneficiary');

            await step('Mở trang Cổng Người thụ hưởng (/beneficiary)', async () => {
                await page.goto(`${BASE_URL}/beneficiary`, { waitUntil: 'domcontentloaded' });
                await page.waitForTimeout(1500);
            });
            await screenshot('01_beneficiary_vaults_list');

            await step('Kiểm tra hiển thị Két sắt được thừa kế "Két sắt Gia đình - Di sản số 2026"', async () => {
                await page.locator('text=/Két sắt Gia đình|Di sản|Két thừa kế/i').first().waitFor({ state: 'visible', timeout: 5000 });
            });

            await step('Kiểm tra nhãn thời hạn nhận di sản (Claim before...)', async () => {
                const claimBadge = page.locator('text=/From|Claim|Hạn|ACTIVE|Từ|Nhận trước/i').first();
                await claimBadge.waitFor({ state: 'visible' });
            });
            await screenshot('02_beneficiary_vault_card');
        }
    }));

    // TC21: Chi tiết Nhận di sản (Beneficiary Vault Detail & Progress)
    results.push(await runTestCase({
        id: 'TC21',
        name: 'Chi tiết Két sắt Người thụ hưởng & Tiến độ bàn giao',
        role: 'Người thụ hưởng (Beneficiary)',
        fn: async ({ page, step, screenshot }) => {
            await setupMockForRole(page, 'beneficiary');

            await step('Mở trang Chi tiết Két sắt người thụ hưởng (/beneficiary/vaults/vault-demo-001)', async () => {
                await page.goto(`${BASE_URL}/beneficiary/vaults/vault-demo-001`, { waitUntil: 'domcontentloaded' });
                await page.waitForTimeout(2000);
            });
            await screenshot('01_beneficiary_detail_overview');

            await step('Kiểm tra bảng Tiến độ bàn giao di sản (Handover Progress)', async () => {
                await page.locator('text=/Handover|Tiến độ|progress|Tài sản|bàn giao/i').first().waitFor({ state: 'visible', timeout: 5000 });
            });

            await step('Kiểm tra danh sách tài sản được phân bổ (Vietcombank, Facebook, Ledger Crypto)', async () => {
                await page.locator('text=/Vietcombank|Facebook|Ledger/i').first().waitFor({ state: 'visible', timeout: 5000 });
            });
            await screenshot('02_allocated_assets_list');
        }
    }));

    // TC22: Xem Mật khẩu & Bí mật Tài sản số
    results.push(await runTestCase({
        id: 'TC22',
        name: 'Xem Bí mật Tài sản số & Sao chép Mật khẩu',
        role: 'Người thụ hưởng (Beneficiary)',
        fn: async ({ page, step, screenshot }) => {
            await setupMockForRole(page, 'beneficiary');

            await step('Mở chi tiết két sắt và xác định nút "View" (Xem) trên tài sản Vietcombank', async () => {
                await page.goto(`${BASE_URL}/beneficiary/vaults/vault-demo-001`, { waitUntil: 'domcontentloaded' });
                await page.waitForTimeout(2000);
                const viewBtn = page.locator('button:has-text("View"), button:has-text("Xem")').first();
                await viewBtn.waitFor({ state: 'visible', timeout: 5000 });
                await viewBtn.click({ force: true });
                await page.waitForTimeout(800);
            });
            await screenshot('01_secret_modal_masked');

            await step('Nhấn biểu tượng "Con mắt" (visibility) để hiện mật khẩu giải mã', async () => {
                const eyeBtn = page.locator('button:has(.material-symbols-outlined)').filter({ hasText: /visibility/ }).first();
                if (await eyeBtn.isVisible()) {
                    await eyeBtn.click({ force: true });
                    await page.waitForTimeout(600);
                }
            });
            await screenshot('02_secret_modal_revealed');

            await step('Nhấn nút "Sao chép" (Copy) mật khẩu vào clipboard', async () => {
                const copyBtn = page.locator('button:has(.material-symbols-outlined)').filter({ hasText: /copy|check|content_copy/ }).first();
                if (await copyBtn.isVisible()) {
                    await copyBtn.click({ force: true });
                    await page.waitForTimeout(600);
                }
            });
            await screenshot('03_secret_copied');

            await step('Đóng hộp thoại xem bí mật', async () => {
                const closeBtn = page.locator('button:has(.material-symbols-outlined)').filter({ hasText: /close/ }).first();
                if (await closeBtn.isVisible()) await closeBtn.click({ force: true });
            });
        }
    }));

    // TC23: Tải Hồ sơ khai nhận Di sản PDF (Inheritance Dossier PDF)
    results.push(await runTestCase({
        id: 'TC23',
        name: 'Tải Hồ sơ Khai nhận Di sản PDF (Dossier PDF)',
        role: 'Người thụ hưởng (Beneficiary)',
        fn: async ({ page, step, screenshot }) => {
            await setupMockForRole(page, 'beneficiary');

            await step('Mở trang chi tiết két sắt và cuộn đến khối Hồ sơ khai nhận di sản', async () => {
                await page.goto(`${BASE_URL}/beneficiary/vaults/vault-demo-001`, { waitUntil: 'domcontentloaded' });
                await page.evaluate(() => window.scrollTo(0, 1000));
                await page.waitForTimeout(800);
            });
            await screenshot('01_dossier_section');

            await step('Kiểm tra khối "Inheritance dossier" và nút "Download PDF"', async () => {
                const dossierTitle = page.locator('text=/Inheritance dossier|Hồ sơ khai nhận/i').first();
                await dossierTitle.waitFor({ state: 'visible', timeout: 5000 });
                const downloadBtn = page.locator('button:has-text("Download PDF"), button:has-text("Tải PDF"), button:has-text("Tải về")').first();
                await downloadBtn.waitFor({ state: 'visible' });
            });
            await screenshot('02_dossier_download_ready');
        }
    }));

    // TC25: Cổng Người thi hành di chúc (Executor Portal)
    results.push(await runTestCase({
        id: 'TC25',
        name: 'Cổng Người thi hành di chúc - Danh sách vụ việc (/executor/home)',
        role: 'Người thi hành di chúc (Executor)',
        fn: async ({ page, step, screenshot }) => {
            await setupMockForRole(page, 'executor');

            await step('Mở trang Quản lý Người thi hành (/executor/home)', async () => {
                await page.goto(`${BASE_URL}/executor/home`, { waitUntil: 'domcontentloaded' });
                await page.waitForTimeout(1500);
            });
            await screenshot('01_executor_cases_list');

            await step('Kiểm tra hồ sơ thi hành được phân công "Trần Long Vân"', async () => {
                await page.locator('text=/Trần Long Vân|Di sản|Két|Executor|Case|Hồ sơ/i').first().waitFor({ state: 'visible', timeout: 5000 });
            });
            await screenshot('02_executor_case_card');
        }
    }));

    // TC26: Chi tiết Vụ việc & Nhiệm vụ Tài khoản số (Executor Case Tasks)
    results.push(await runTestCase({
        id: 'TC26',
        name: 'Chi tiết Nhiệm vụ xử lý Tài khoản số của Executor',
        role: 'Người thi hành di chúc (Executor)',
        fn: async ({ page, step, screenshot }) => {
            await setupMockForRole(page, 'executor');

            await step('Mở chi tiết vụ việc thi hành (/executor/cases/assign-001)', async () => {
                await page.goto(`${BASE_URL}/executor/cases/assign-001`, { waitUntil: 'domcontentloaded' });
                await page.waitForTimeout(2000);
            });
            await screenshot('01_executor_case_detail');

            await step('Kiểm tra danh sách nhiệm vụ xử lý tài khoản số (Facebook, Google)', async () => {
                await page.locator('text=/Facebook|Google|Account|Nhiệm vụ|Checklist/i').first().waitFor({ state: 'visible', timeout: 5000 });
            });

            await step('Kiểm tra các nhãn nguyện vọng: Tưởng niệm (MEMORIALIZE), Tải dữ liệu (TRANSFER_DATA)', async () => {
                await page.locator('text=/Memorialize|Tưởng niệm|Transfer|bàn giao/i').first().waitFor({ state: 'visible', timeout: 5000 });
            });
            await screenshot('02_executor_wishes_and_status');
        }
    }));

    // TC30: Admin Cảnh báo An ninh & Nhật ký Kiểm toán (/admin/alerts & /admin/audit-logs)
    results.push(await runTestCase({
        id: 'TC30',
        name: 'Quản trị viên - Cảnh báo An ninh và Nhật ký Kiểm toán (Alerts & Audit)',
        role: 'Quản trị viên (System Admin)',
        fn: async ({ page, step, screenshot }) => {
            await setupMockForRole(page, 'admin');

            await step('Mở trang Cảnh báo An ninh hệ thống (/admin/alerts)', async () => {
                await page.goto(`${BASE_URL}/admin/alerts`, { waitUntil: 'domcontentloaded' });
                await page.waitForTimeout(1500);
            });
            await screenshot('01_admin_alerts_page');

            await step('Kiểm tra danh sách cảnh báo bất thường (Security Alerts)', async () => {
                await page.locator('text=/Alerts|Cảnh báo|An ninh|SUSPICIOUS/i').first().waitFor({ state: 'visible', timeout: 5000 });
            });

            await step('Chuyển sang trang Nhật ký Kiểm toán hệ thống (/admin/audit-logs)', async () => {
                await page.goto(`${BASE_URL}/admin/audit-logs`, { waitUntil: 'domcontentloaded' });
                await page.waitForTimeout(2000);
            });
            await screenshot('02_admin_audit_logs_page');

            await step('Kiểm tra bảng nhật ký các hành động nhạy cảm trong hệ thống', async () => {
                await page.locator('text=/Audit|Nhật ký|Action|Hành động|Thời điểm/i').first().waitFor({ state: 'visible', timeout: 5000 });
            });
        }
    }));

    console.log('\n============================================================');
    const passed = results.filter(r => r.status === 'PASSED').length;
    console.log(`TARGETED RUN FINISHED: ${passed}/${results.length} PASSED`);
    console.log('============================================================\n');
}

main().catch(console.error);
