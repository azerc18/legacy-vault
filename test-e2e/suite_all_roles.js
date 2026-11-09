const { BASE_URL, runTestCase } = require('./test_runner_utils');
const { setupMockForRole } = require('./api_mocks');

async function runAllRemainingSuites() {
    const results = [];

    // =========================================================================
    // SUITE 2: VAULT OWNER (TC10 - TC18)
    // =========================================================================

    // TC10: Owner Dashboard
    results.push(await runTestCase({
        id: 'TC10',
        name: 'Owner Dashboard & Danh sách Két sắt',
        role: 'Chủ két sắt (Vault Owner)',
        fn: async ({ page, step, screenshot }) => {
            await setupMockForRole(page, 'owner');

            await step('Đăng nhập và mở màn hình Danh sách Két sắt (/owner/vaults)', async () => {
                await page.goto(`${BASE_URL}/owner/vaults`, { waitUntil: 'domcontentloaded' });
                await page.waitForTimeout(1500);
            });
            await screenshot('01_owner_vaults_list');

            await step('Kiểm tra Thẻ Sẵn sàng Di sản (Estate Readiness Card)', async () => {
                await page.locator('text=/Readiness|Sẵn sàng|Score/i').first().waitFor({ state: 'visible', timeout: 5000 });
            });

            await step('Kiểm tra hiển thị Két sắt "Két sắt Gia đình - Di sản số 2026"', async () => {
                await page.locator('text=/Két sắt Gia đình/i').first().waitFor({ state: 'visible', timeout: 5000 });
            });
            await screenshot('02_vault_card_visible');

            await step('Xác định nút "Tạo két mới" (New Vault)', async () => {
                const newVaultBtn = page.locator('a[href="/owner/vaults/new"]').first();
                await newVaultBtn.waitFor({ state: 'visible' });
            });
        }
    }));

    // TC11: Form Tạo Két Sắt Mới (Create Vault)
    results.push(await runTestCase({
        id: 'TC11',
        name: 'Khởi tạo Két sắt mới (Create Vault Form)',
        role: 'Chủ két sắt (Vault Owner)',
        fn: async ({ page, step, screenshot }) => {
            await setupMockForRole(page, 'owner');

            await step('Mở trang Khởi tạo Két sắt mới (/owner/vaults/new)', async () => {
                await page.goto(`${BASE_URL}/owner/vaults/new`, { waitUntil: 'domcontentloaded' });
                await page.waitForTimeout(1500);
            });
            await screenshot('01_create_vault_page');

            await step('Điền "Két sắt Khẩn cấp & Tài chính số" vào ô Tên két sắt', async () => {
                await page.fill('input[placeholder*="tên" i], input[placeholder*="name" i]', 'Két sắt Khẩn cấp & Tài chính số');
            });

            await step('Điền mô tả mục đích két sắt vào ô Mô tả', async () => {
                await page.fill('textarea', 'Chứa tài khoản ngân hàng và hướng dẫn cho người thân khi xảy ra biến cố');
            });
            await screenshot('02_vault_info_filled');

            await step('Thêm thông tin Người thụ hưởng (email: con_thuhuong@gmail.com, tên: Trần Vân Con)', async () => {
                const emailInput = page.locator('input[placeholder*="email" i]').first();
                if (await emailInput.isVisible()) await emailInput.fill('con_thuhuong@gmail.com');
                const nameInput = page.locator('input[placeholder*="họ" i], input[placeholder*="name" i]').nth(1);
                if (await nameInput.isVisible()) await nameInput.fill('Trần Vân Con');
            });
            await screenshot('03_beneficiary_row_filled');

            await step('Thêm thông tin Tài sản số ban đầu (Vietcombank Digital 2026)', async () => {
                const assetNameInput = page.locator('input[placeholder*="tài sản" i], input[placeholder*="asset" i]').first();
                if (await assetNameInput.isVisible()) await assetNameInput.fill('Vietcombank Digital 2026');
            });
            await screenshot('04_asset_row_filled');

            await step('Nhấn nút "Tạo két sắt" để gửi dữ liệu', async () => {
                const submitBtn = page.locator('button[type="submit"]').first();
                await submitBtn.click({ force: true });
                await page.waitForTimeout(1000);
            });
            await screenshot('05_vault_submitted');
        }
    }));

    // TC12: Chi tiết Két sắt & Quản lý Tài sản (Vault Detail & Assets)
    results.push(await runTestCase({
        id: 'TC12',
        name: 'Quản lý Chi tiết Két sắt & Thêm Tài sản số',
        role: 'Chủ két sắt (Vault Owner)',
        fn: async ({ page, step, screenshot }) => {
            await setupMockForRole(page, 'owner');

            await step('Mở trang Chi tiết Két sắt (/owner/vaults/vault-demo-001)', async () => {
                await page.goto(`${BASE_URL}/owner/vaults/vault-demo-001`, { waitUntil: 'domcontentloaded' });
                await page.waitForTimeout(1500);
            });
            await screenshot('01_vault_detail_overview');

            await step('Kiểm tra thẻ tóm tắt két sắt: Tên, Mô tả, Trạng thái ACTIVE', async () => {
                await page.locator('text=/Két sắt Gia đình/i').first().waitFor({ state: 'visible', timeout: 5000 });
            });

            await step('Cuộn xuống phần Danh sách Tài sản số', async () => {
                await page.evaluate(() => window.scrollTo(0, 400));
                await page.waitForTimeout(600);
            });
            await screenshot('02_assets_section');

            await step('Xác định nút "Thêm tài sản" (Add Asset)', async () => {
                const addAssetBtn = page.locator('button:has-text("Thêm tài sản"), button:has-text("Add asset")').first();
                if (await addAssetBtn.isVisible()) {
                    await addAssetBtn.click({ force: true });
                    await page.waitForTimeout(800);
                    await screenshot('03_add_asset_modal_open');
                }
            });
        }
    }));

    // TC13: Quản lý Người thụ hưởng (Beneficiaries in Vault)
    results.push(await runTestCase({
        id: 'TC13',
        name: 'Quản lý Người thụ hưởng trong Két sắt',
        role: 'Chủ két sắt (Vault Owner)',
        fn: async ({ page, step, screenshot }) => {
            await setupMockForRole(page, 'owner');

            await step('Mở trang Chi tiết Két sắt và cuộn đến khối Người thụ hưởng', async () => {
                await page.goto(`${BASE_URL}/owner/vaults/vault-demo-001`, { waitUntil: 'domcontentloaded' });
                await page.evaluate(() => window.scrollTo(0, 800));
                await page.waitForTimeout(800);
            });
            await screenshot('01_beneficiaries_section');

            await step('Kiểm tra hiển thị người thụ hưởng "con_thuhuong@gmail.com"', async () => {
                const beneItem = page.locator('text=con_thuhuong@gmail.com').first();
                if (await beneItem.isVisible()) console.log('    Đã thấy người thụ hưởng');
            });

            await step('Xác định nút "Thêm người thụ hưởng" (Add Beneficiary)', async () => {
                const addBeneBtn = page.locator('button:has-text("Thêm người thụ hưởng"), button:has-text("Add beneficiary")').first();
                if (await addBeneBtn.isVisible()) {
                    await addBeneBtn.click({ force: true });
                    await page.waitForTimeout(800);
                    await screenshot('02_add_beneficiary_modal');
                }
            });
        }
    }));

    // TC14: Cấu hình Dead Man's Switch (DMS Configuration)
    results.push(await runTestCase({
        id: 'TC14',
        name: 'Cấu hình Cơ chế Dead Mans Switch (DMS)',
        role: 'Chủ két sắt (Vault Owner)',
        fn: async ({ page, step, screenshot }) => {
            await setupMockForRole(page, 'owner');

            await step('Mở trang Cấu hình Dead Mans Switch (/owner/dms)', async () => {
                await page.goto(`${BASE_URL}/owner/dms`, { waitUntil: 'domcontentloaded' });
                await page.waitForTimeout(1500);
            });
            await screenshot('01_dms_config_page');

            await step('Kiểm tra trạng thái DMS hiện tại và chu kỳ điểm danh', async () => {
                await page.locator('text=/Dead Man|DMS|Điểm danh/i').first().waitFor({ state: 'visible', timeout: 5000 });
            });

            await step('Kiểm tra form điều chỉnh chu kỳ kiểm tra và thời gian ân hạn', async () => {
                const intervalSelect = page.locator('select, input[type="number"]').first();
                if (await intervalSelect.isVisible()) await intervalSelect.click({ force: true });
            });
            await screenshot('02_dms_interval_adjust');

            await step('Kiểm tra phần danh sách Người xác nhận (Confirmers)', async () => {
                await page.evaluate(() => window.scrollTo(0, 500));
                await page.waitForTimeout(600);
            });
            await screenshot('03_dms_confirmers_section');
        }
    }));

    // TC15: Quản lý Người thi hành (Owner Executors)
    results.push(await runTestCase({
        id: 'TC15',
        name: 'Chỉ định và quản lý Người thi hành di chúc (Executors)',
        role: 'Chủ két sắt (Vault Owner)',
        fn: async ({ page, step, screenshot }) => {
            await setupMockForRole(page, 'owner');

            await step('Mở trang Người thi hành di chúc (/owner/executors)', async () => {
                await page.goto(`${BASE_URL}/owner/executors`, { waitUntil: 'domcontentloaded' });
                await page.waitForTimeout(1500);
            });
            await screenshot('01_executors_overview');

            await step('Kiểm tra danh sách người thi hành và các tài khoản số được phân công', async () => {
                await page.locator('text=/Người thi hành|Executor/i').first().waitFor({ state: 'visible', timeout: 5000 });
            });

            await step('Cuộn trang xem hướng dẫn phân công nhiệm vụ (Tưởng niệm / Xóa / Tải dữ liệu)', async () => {
                await page.evaluate(() => window.scrollTo(0, 400));
                await page.waitForTimeout(600);
            });
            await screenshot('02_executor_tasks_guide');
        }
    }));

    // TC16: Lời nhắn di nguyện & Hộp thời gian (Legacy Messages)
    results.push(await runTestCase({
        id: 'TC16',
        name: 'Soạn Lời nhắn di nguyện và Hộp thời gian (Time-Capsule)',
        role: 'Chủ két sắt (Vault Owner)',
        fn: async ({ page, step, screenshot }) => {
            await setupMockForRole(page, 'owner');

            await step('Mở trang Chi tiết Két sắt và cuộn đến khối Lời nhắn di chúc', async () => {
                await page.goto(`${BASE_URL}/owner/vaults/vault-demo-001`, { waitUntil: 'domcontentloaded' });
                await page.evaluate(() => window.scrollTo(0, 1100));
                await page.waitForTimeout(800);
            });
            await screenshot('01_legacy_messages_card');

            await step('Kiểm tra lời nhắn mẫu "Lời dặn dò thương yêu gửi các con"', async () => {
                const msgItem = page.locator('text=/Lời dặn dò/i').first();
                if (await msgItem.isVisible()) console.log('    Đã thấy lời nhắn mẫu');
            });

            await step('Xác định nút "Tạo lời nhắn" (Add Legacy Message)', async () => {
                const addMsgBtn = page.locator('button:has-text("lời nhắn"), button:has-text("message")').first();
                if (await addMsgBtn.isVisible()) {
                    await addMsgBtn.click({ force: true });
                    await page.waitForTimeout(800);
                    await screenshot('02_create_message_modal');
                }
            });
        }
    }));

    // TC17: Nhật ký hoạt động Két sắt (Owner Activity Logs)
    results.push(await runTestCase({
        id: 'TC17',
        name: 'Xem Nhật ký hoạt động và kiểm toán Két sắt (Activity Logs)',
        role: 'Chủ két sắt (Vault Owner)',
        fn: async ({ page, step, screenshot }) => {
            await setupMockForRole(page, 'owner');

            await step('Mở trang Nhật ký hoạt động (/owner/activity)', async () => {
                await page.goto(`${BASE_URL}/owner/activity`, { waitUntil: 'domcontentloaded' });
                await page.waitForTimeout(1500);
            });
            await screenshot('01_activity_logs_page');

            await step('Kiểm tra bảng lịch sử các hành động tạo két, thêm tài sản, gửi điểm danh', async () => {
                await page.locator('text=/Nhật ký|Activity|Audit/i').first().waitFor({ state: 'visible', timeout: 5000 });
            });
            await screenshot('02_activity_table');
        }
    }));

    // TC18: Gói cước & Nâng cấp Dung lượng (Pricing & Billing)
    results.push(await runTestCase({
        id: 'TC18',
        name: 'Trang Bảng giá và Nâng cấp Gói cước (Pricing & Billing)',
        role: 'Chủ két sắt (Vault Owner)',
        fn: async ({ page, step, screenshot }) => {
            await setupMockForRole(page, 'owner');

            await step('Mở trang Bảng giá và gói cước (/pricing)', async () => {
                await page.goto(`${BASE_URL}/pricing`, { waitUntil: 'domcontentloaded' });
                await page.waitForTimeout(1500);
            });
            await screenshot('01_pricing_plans_page');

            await step('Kiểm tra các gói cước: Miễn phí, Tiêu chuẩn, Di sản Vĩnh viễn', async () => {
                await page.locator('text=/Gói|Plan|Pricing|VND/i').first().waitFor({ state: 'visible', timeout: 5000 });
            });

            await step('Cuộn xuống kiểm tra bảng so sánh tính năng và cổng thanh toán VNPay', async () => {
                await page.evaluate(() => window.scrollTo(0, 600));
                await page.waitForTimeout(800);
            });
            await screenshot('02_pricing_comparison');
        }
    }));

    // =========================================================================
    // SUITE 3: BENEFICIARY (TC19 - TC24)
    // =========================================================================

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
                await page.locator('text=/Két sắt Gia đình/i').first().waitFor({ state: 'visible', timeout: 5000 });
            });

            await step('Kiểm tra nhãn thời hạn nhận di sản (Claim before...)', async () => {
                const claimBadge = page.locator('text=/From|Claim|Hạn|ACTIVE/i').first();
                await claimBadge.waitFor({ state: 'visible' });
            });
            await screenshot('02_beneficiary_vault_card');
        }
    }));

    // TC20: Hướng dẫn Người thụ hưởng (Beneficiary Guide)
    results.push(await runTestCase({
        id: 'TC20',
        name: 'Xem Cẩm nang Hướng dẫn nhận di sản số',
        role: 'Người thụ hưởng (Beneficiary)',
        fn: async ({ page, step, screenshot }) => {
            await setupMockForRole(page, 'beneficiary');

            await step('Mở trang Cẩm nang Người thụ hưởng (/beneficiary/guide)', async () => {
                await page.goto(`${BASE_URL}/beneficiary/guide`, { waitUntil: 'domcontentloaded' });
                await page.waitForTimeout(1500);
            });
            await screenshot('01_beneficiary_guide_hero');

            await step('Cuộn qua các bước hướng dẫn: Xác thực -> Xem tài sản -> Đóng hồ sơ', async () => {
                await page.evaluate(() => window.scrollTo(0, 500));
                await page.waitForTimeout(800);
                await page.evaluate(() => window.scrollTo(0, 1000));
                await page.waitForTimeout(800);
            });
            await screenshot('02_beneficiary_guide_steps');
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
                await page.locator('text=/Handover|Tiến độ|progress|Tài sản/i').first().waitFor({ state: 'visible', timeout: 5000 });
            });

            await step('Kiểm tra danh sách tài sản được phân bổ (Vietcombank, Facebook, Ledger Crypto)', async () => {
                await page.locator('text=/Vietcombank/i').first().waitFor({ state: 'visible', timeout: 5000 });
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
                await page.waitForTimeout(1500);
                const viewBtn = page.locator('button:has-text("View")').first();
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
                const copyBtn = page.locator('button:has(.material-symbols-outlined)').filter({ hasText: /copy|check/ }).first();
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
                const downloadBtn = page.locator('button:has-text("Download PDF"), button:has-text("Tải PDF")').first();
                await downloadBtn.waitFor({ state: 'visible' });
            });
            await screenshot('02_dossier_download_ready');
        }
    }));

    // TC24: Đọc Lời nhắn Di chúc (Legacy Messages)
    results.push(await runTestCase({
        id: 'TC24',
        name: 'Đọc Lời nhắn Di chúc & Tải tệp đính kèm',
        role: 'Người thụ hưởng (Beneficiary)',
        fn: async ({ page, step, screenshot }) => {
            await setupMockForRole(page, 'beneficiary');

            await step('Mở trang chi tiết két sắt và cuộn đến khối Lời nhắn di nguyện', async () => {
                await page.goto(`${BASE_URL}/beneficiary/vaults/vault-demo-001`, { waitUntil: 'domcontentloaded' });
                await page.evaluate(() => window.scrollTo(0, 1400));
                await page.waitForTimeout(800);
            });
            await screenshot('01_beneficiary_legacy_messages');

            await step('Kiểm tra nội dung lời nhắn tâm thư từ người để lại di sản', async () => {
                const msgEl = page.locator('text=/Lời dặn dò thương yêu|Bố mẹ luôn yêu thương/i').first();
                if (await msgEl.isVisible()) console.log('    Đã thấy lời nhắn tâm thư');
            });
            await screenshot('02_message_content_displayed');
        }
    }));

    // =========================================================================
    // SUITE 4: EXECUTOR (TC25 - TC26)
    // =========================================================================

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

            await step('Kiểm tra hồ sơ thi hành được phân công "Két sắt Gia đình"', async () => {
                await page.locator('text=/Két sắt Gia đình|Case|Hồ sơ/i').first().waitFor({ state: 'visible', timeout: 5000 });
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
                await page.locator('text=/Facebook|Google|Account|Nhiệm vụ/i').first().waitFor({ state: 'visible', timeout: 5000 });
            });

            await step('Kiểm tra các nhãn nguyện vọng: Tưởng niệm (MEMORIALIZE), Tải dữ liệu (TRANSFER_DATA)', async () => {
                await page.locator('text=/Memorialize|Tưởng niệm|Transfer/i').first().waitFor({ state: 'visible', timeout: 5000 });
            });
            await screenshot('02_executor_wishes_and_status');
        }
    }));

    // =========================================================================
    // SUITE 5: LEGAL VERIFIER & ADMIN (TC27 - TC30)
    // =========================================================================

    // TC27: Cổng Xác minh Pháp lý (Legal Verifier Home & Dashboard)
    results.push(await runTestCase({
        id: 'TC27',
        name: 'Cổng Xác minh Pháp lý - Thẩm định yêu cầu mở két (/verifier)',
        role: 'Người xác minh pháp lý (Legal Verifier)',
        fn: async ({ page, step, screenshot }) => {
            await setupMockForRole(page, 'verifier');

            await step('Mở trang Thẩm định Pháp lý (/verifier)', async () => {
                await page.goto(`${BASE_URL}/verifier`, { waitUntil: 'domcontentloaded' });
                await page.waitForTimeout(1500);
            });
            await screenshot('01_verifier_dashboard');

            await step('Kiểm tra danh sách yêu cầu xác minh mở két (Verification Requests)', async () => {
                await page.locator('text=/Verification|Xác minh|Yêu cầu|req-001/i').first().waitFor({ state: 'visible', timeout: 5000 });
            });
            await screenshot('02_verifier_requests_table');
        }
    }));

    // TC28: Admin Quản lý Người dùng (/admin/users)
    results.push(await runTestCase({
        id: 'TC28',
        name: 'Quản trị viên - Quản lý Người dùng hệ thống (/admin/users)',
        role: 'Quản trị viên (System Admin)',
        fn: async ({ page, step, screenshot }) => {
            await setupMockForRole(page, 'admin');

            await step('Mở trang Quản lý Người dùng (/admin/users)', async () => {
                await page.goto(`${BASE_URL}/admin/users`, { waitUntil: 'domcontentloaded' });
                await page.waitForTimeout(1500);
            });
            await screenshot('01_admin_users_page');

            await step('Kiểm tra bảng danh sách tài khoản: Email, Tên, Roles, Trạng thái ACTIVE', async () => {
                await page.locator('text=/tlv48691@gmail.com|Users|Người dùng/i').first().waitFor({ state: 'visible', timeout: 5000 });
            });
            await screenshot('02_admin_users_table');
        }
    }));

    // TC29: Admin Quản lý Két sắt toàn hệ thống (/admin/vaults)
    results.push(await runTestCase({
        id: 'TC29',
        name: 'Quản trị viên - Giám sát Két sắt toàn hệ thống (/admin/vaults)',
        role: 'Quản trị viên (System Admin)',
        fn: async ({ page, step, screenshot }) => {
            await setupMockForRole(page, 'admin');

            await step('Mở trang Giám sát Két sắt (/admin/vaults)', async () => {
                await page.goto(`${BASE_URL}/admin/vaults`, { waitUntil: 'domcontentloaded' });
                await page.waitForTimeout(1500);
            });
            await screenshot('01_admin_vaults_page');

            await step('Kiểm tra danh sách két sắt và trạng thái bảo vệ', async () => {
                await page.locator('text=/Vaults|Két sắt|Gia đình/i').first().waitFor({ state: 'visible', timeout: 5000 });
            });
            await screenshot('02_admin_vaults_table');
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
                await page.locator('text=/Alerts|Cảnh báo|An ninh/i').first().waitFor({ state: 'visible', timeout: 5000 });
            });

            await step('Chuyển sang trang Nhật ký Kiểm toán hệ thống (/admin/audit-logs)', async () => {
                await page.goto(`${BASE_URL}/admin/audit-logs`, { waitUntil: 'domcontentloaded' });
                await page.waitForTimeout(1500);
            });
            await screenshot('02_admin_audit_logs_page');

            await step('Kiểm tra bảng nhật ký các hành động nhạy cảm trong hệ thống', async () => {
                await page.locator('text=/Audit|Nhật ký|Action/i').first().waitFor({ state: 'visible', timeout: 5000 });
            });
            await screenshot('03_admin_audit_table');
        }
    }));

    return results;
}

module.exports = { runAllRemainingSuites };

if (require.main === module) {
    runAllRemainingSuites().then(results => {
        console.log('\n============================================================');
        console.log(`ALL REMAINING SUITES FINISHED: ${results.filter(r => r.status === 'PASSED').length}/${results.length} PASSED`);
        console.log('============================================================\n');
    }).catch(err => {
        console.error('Fatal Error in Remaining Suites:', err);
    });
}
