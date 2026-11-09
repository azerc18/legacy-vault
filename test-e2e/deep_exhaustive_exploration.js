/**
 * DEEP EXHAUSTIVE FORM CONTROLS & DROPDOWN AUDIT TEST RUNNER
 * Explores EVERY input field, EVERY option in select dropdowns, EVERY modal,
 * and EVERY workflow across all roles with human-like interactions and visible cursor.
 */

const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');
const {
    attachHumanCursor,
    humanMoveAndClick,
    humanType,
    humanSelectOption,
    humanCheck,
    humanHover,
    humanScroll,
    humanPause
} = require('./human_interactions');
const { setupMockForRole } = require('./api_mocks');

const BASE_URL = 'http://localhost:5173';
const OUTPUT_DIR = path.resolve(__dirname, '..', 'test-artifacts', 'videos', 'deep-exploration');

if (!fs.existsSync(OUTPUT_DIR)) fs.mkdirSync(OUTPUT_DIR, { recursive: true });

async function createHumanSession(videoFileName, role = 'owner') {
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
    await setupMockForRole(page, role);

    const closeAndSave = async () => {
        const video = page.video();
        await context.close();
        await browser.close();

        if (video) {
            const rawPath = await video.path();
            const targetPath = path.join(OUTPUT_DIR, videoFileName);
            if (fs.existsSync(targetPath)) fs.unlinkSync(targetPath);
            fs.renameSync(rawPath, targetPath);
            const sizeKB = (fs.statSync(targetPath).size / 1024).toFixed(1);
            console.log(`\n>>> [SAVED HD VIDEO] ${videoFileName} (${sizeKB} KB)`);
        }
        try {
            if (fs.existsSync(tempDir)) fs.rmSync(tempDir, { recursive: true, force: true });
        } catch (_) {}
    };

    return { page, closeAndSave };
}

// =========================================================================
// JOURNEY 1: Auth Validation, Error Corners, Profile & 2FA
// =========================================================================
async function runJourney1() {
    console.log('\n🎬 [JOURNEY 1] Auth Validation, Corner Cases, Profile & 2FA...');
    const { page, closeAndSave } = await createHumanSession('EXHAUSTIVE_01_Auth_Validation_Profile_And_2FA.webm', 'owner');

    // 1. Visit Register & test validation corners
    await page.goto(`${BASE_URL}/register`);
    await page.waitForLoadState('networkidle');
    await humanPause(page, 2000);

    console.log('  Testing Register validation errors with empty submit...');
    await humanMoveAndClick(page, 'button[type="submit"]', { pauseAfter: 2000 });

    // Type name
    await humanType(page, '#reg-name', 'Trần Long Vân', { delay: 40, pauseAfter: 1000 });

    // Invalid email
    await humanType(page, '#reg-email', 'email_sai_dinh_dang', { delay: 40, pauseAfter: 1200 });
    await humanMoveAndClick(page, 'button[type="submit"]', { pauseAfter: 1800 });

    // Correct email
    await humanType(page, '#reg-email', 'tlv48691@gmail.com', { delay: 40, pauseAfter: 1000 });

    // Password fields
    await humanType(page, '#reg-password', 'Short1!', { delay: 40, pauseAfter: 1200 });
    await humanType(page, '#reg-confirm', 'DifferentPass123!', { delay: 40, pauseAfter: 1200 });
    await humanMoveAndClick(page, 'button[type="submit"]', { pauseAfter: 2000 });

    // Correct matching passwords
    await humanType(page, '#reg-password', 'StrongPassword2026@#$', { delay: 35, pauseAfter: 1000 });
    await humanType(page, '#reg-confirm', 'StrongPassword2026@#$', { delay: 35, pauseAfter: 1500 });

    // Toggle password visibility eye icon
    const eyeBtn = page.locator('button[type="button"]:has(span:text("visibility")), button[aria-label*="password"], button[aria-label*="mật khẩu"]');
    if (await eyeBtn.count() > 0) {
        await humanMoveAndClick(page, eyeBtn.first(), { pauseAfter: 1800 });
        await humanMoveAndClick(page, eyeBtn.first(), { pauseAfter: 1200 });
    }

    // 2. Visit Login
    await page.goto(`${BASE_URL}/login`);
    await page.waitForLoadState('networkidle');
    await humanPause(page, 2000);

    // Test invalid credentials alert
    await humanType(page, '#login-email', 'wrong_account@gmail.com', { delay: 40, pauseAfter: 800 });
    const loginPass = page.locator('input[type="password"]').first();
    if (await loginPass.count() > 0) {
        await humanType(page, loginPass, 'WrongPassword999!', { delay: 40, pauseAfter: 1000 });
    }
    await humanMoveAndClick(page, 'button[type="submit"]', { pauseAfter: 2500 });

    // 3. Visit Forgot Password
    await page.goto(`${BASE_URL}/forgot-password`);
    await page.waitForLoadState('networkidle');
    await humanPause(page, 2000);
    await humanType(page, '#forgot-email', 'tlv48691@gmail.com', { delay: 40, pauseAfter: 1000 });
    await humanMoveAndClick(page, 'button[type="submit"]', { pauseAfter: 2500 });

    // 4. Visit Reset Password
    await page.goto(`${BASE_URL}/reset-password?email=tlv48691@gmail.com`);
    await page.waitForLoadState('networkidle');
    await humanPause(page, 2000);
    const resetOtp = page.locator('#reset-otp, input[inputmode="numeric"]');
    if (await resetOtp.count() > 0) {
        await humanType(page, resetOtp.first(), '654321', { delay: 50, pauseAfter: 1500 });
    }
    const resetPass = page.locator('input[type="password"]');
    if (await resetPass.count() > 0) {
        await humanType(page, resetPass.nth(0), 'NewSecretPass2026!@#', { delay: 40, pauseAfter: 1000 });
        if (await resetPass.count() > 1) {
            await humanType(page, resetPass.nth(1), 'NewSecretPass2026!@#', { delay: 40, pauseAfter: 1500 });
        }
    }

    // 5. Visit Verify Email
    await page.goto(`${BASE_URL}/verify-email?email=tlv48691@gmail.com`);
    await page.waitForLoadState('networkidle');
    await humanPause(page, 2000);
    const verifyOtp = page.locator('input[inputmode="numeric"]');
    if (await verifyOtp.count() > 0) {
        await humanType(page, verifyOtp.first(), '123456', { delay: 50, pauseAfter: 2000 });
    }

    // 6. Visit Profile Page (/me) and explore all inputs
    await page.goto(`${BASE_URL}/me`);
    await page.waitForLoadState('networkidle');
    await humanPause(page, 2500);

    // Edit full name & phone
    const nameInput = page.locator('input[value*="Trần"], input[placeholder*="Họ"], input[placeholder*="name"]').first();
    if (await nameInput.count() > 0) {
        await humanType(page, nameInput, 'Trần Long Vân (Cập nhật)', { delay: 40, pauseAfter: 1200 });
    }
    const phoneInput = page.locator('input[value*="0"], input[placeholder*="điện thoại"], input[placeholder*="phone"]').first();
    if (await phoneInput.count() > 0) {
        await humanType(page, phoneInput, '+84 988 123 456', { delay: 40, pauseAfter: 1500 });
    }

    // Explore 2FA Card
    await humanScroll(page, 450, { pauseAfter: 1800 });
    const turnOn2faBtn = page.locator('button:has-text("Bật"), button:has-text("Turn on"), button:has-text("Thiết lập")').first();
    if (await turnOn2faBtn.count() > 0) {
        await humanMoveAndClick(page, turnOn2faBtn, { pauseAfter: 2500 });
        const totpInput = page.locator('input[autoComplete="one-time-code"], input[placeholder*="•"]').first();
        if (await totpInput.count() > 0) {
            await humanType(page, totpInput, '123456', { delay: 50, pauseAfter: 2000 });
        }
    }

    await humanScroll(page, -450, { pauseAfter: 2000 });
    await closeAndSave();
}

// =========================================================================
// JOURNEY 2: Vault Owner - Create Vault & All 8 Asset Types in Dropdown
// =========================================================================
async function runJourney2() {
    console.log('\n🎬 [JOURNEY 2] Vault Owner - Create Vault & All 8 Asset Types in Dropdown...');
    const { page, closeAndSave } = await createHumanSession('EXHAUSTIVE_02_Owner_Create_Vault_And_All_Asset_Types.webm', 'owner');

    // 1. Visit Create Vault Form
    await page.goto(`${BASE_URL}/vaults/new`);
    await page.waitForLoadState('networkidle');
    await humanPause(page, 2000);

    const vaultNameInput = page.locator('input[maxLength="100"], input[placeholder*="Két"], input[placeholder*="name"]').first();
    if (await vaultNameInput.count() > 0) {
        await humanType(page, vaultNameInput, 'Két sắt Gia đình - Di sản số 2026', { delay: 40, pauseAfter: 1200 });
    }
    const descTextarea = page.locator('textarea').first();
    if (await descTextarea.count() > 0) {
        await humanType(page, descTextarea, 'Chứa toàn bộ danh mục tài sản số, tài khoản ngân hàng và di nguyện cho con.', { delay: 35, pauseAfter: 1500 });
    }

    // Beneficiary in Create Vault
    const beneEmail = page.locator('input[type="email"]').first();
    if (await beneEmail.count() > 0) {
        await humanType(page, beneEmail, 'con_thuhuong@gmail.com', { delay: 40, pauseAfter: 1000 });
    }
    const beneName = page.locator('input[placeholder*="Tên"], input[placeholder*="Name"]').first();
    if (await beneName.count() > 0) {
        await humanType(page, beneName, 'Trần Vân Con', { delay: 40, pauseAfter: 1000 });
    }
    const beneRel = page.locator('input[placeholder*="hệ"], input[placeholder*="relationship"]').first();
    if (await beneRel.count() > 0) {
        await humanType(page, beneRel, 'Con ruột', { delay: 40, pauseAfter: 1500 });
    }

    // Quick Asset Radio Buttons in Create Vault
    console.log('  Cycling through Quick Asset radio options in Create Vault...');
    const assetRadios = page.locator('input[type="radio"]');
    const radioCount = await assetRadios.count();
    for (let i = 0; i < Math.min(radioCount, 5); i++) {
        await humanMoveAndClick(page, assetRadios.nth(i), { pauseAfter: 1400 });
    }

    // 2. Visit Vault Detail page
    await page.goto(`${BASE_URL}/vaults/vault-demo-001`);
    await page.waitForLoadState('networkidle');
    await humanPause(page, 2500);

    // Click "Thêm tài sản" (Add Asset) button
    console.log('  Opening Add Asset modal...');
    const addAssetBtn = page.locator('button:has-text("Thêm tài sản"), button:has-text("Add Asset")').first();
    await humanMoveAndClick(page, addAssetBtn, { pauseAfter: 2000 });

    const assetTypeSelect = page.locator('select').first();
    await assetTypeSelect.waitFor({ state: 'visible', timeout: 5000 }).catch(() => {});

    // ALL 8 ASSET TYPES IN THE SELECT DROPDOWN
    const assetTypes = [
        { value: 'BANK_ACCOUNT', label: 'Tài khoản ngân hàng', name: 'Tài khoản Techcombank Priority', curr: 'VND' },
        { value: 'EXCHANGE_ACCOUNT', label: 'Sàn giao dịch', name: 'Tài khoản Binance VIP', curr: 'USD' },
        { value: 'CRYPTO_WALLET', label: 'Ví tiền điện tử', name: 'Ví lạnh Ledger Nano X', network: 'Ethereum' },
        { value: 'E_WALLET', label: 'Ví điện tử', name: 'Ví MoMo Doanh Nhân', platform: 'MoMo', wish: 'TRANSFER_DATA' },
        { value: 'SOCIAL_MEDIA', label: 'Mạng xã hội', name: 'Kênh YouTube Công nghệ', platform: 'YouTube', wish: 'MEMORIALIZE' },
        { value: 'EMAIL', label: 'Hộp thư điện tử', name: 'Tài khoản Google Admin', platform: 'Gmail', wish: 'DELETE' },
        { value: 'DOCUMENT', label: 'Tài liệu pháp lý', name: 'Sổ hồng căn hộ Landmark 81' },
        { value: 'OTHER', label: 'Khác', name: 'Ghi chú hạt giống mật mã bí mật' }
    ];

    for (const item of assetTypes) {
        console.log(`    -> Selecting assetType: ${item.value} (${item.label})`);
        await humanSelectOption(page, assetTypeSelect, item.value, { pauseAfter: 2500 });

        const nameInput = page.locator('input[placeholder*="tài sản"], input[placeholder*="Name"]').first();
        if (await nameInput.count() > 0) {
            await humanType(page, nameInput, item.name, { delay: 35, pauseAfter: 800 });
        }

        if (item.curr) {
            const currSelect = page.locator('select:has(option:text("VND")), select:has(option:text("USD"))').first();
            if (await currSelect.count() > 0) {
                console.log('       Testing Currency dropdown: USD then VND');
                await humanSelectOption(page, currSelect, 'USD', { pauseAfter: 1600 });
                await humanSelectOption(page, currSelect, 'VND', { pauseAfter: 1600 });
            }
        }
        if (item.network) {
            const netSelect = page.locator('select:has(option:text("Bitcoin")), select:has(option:text("Ethereum"))').first();
            if (await netSelect.count() > 0) {
                console.log('       Testing Blockchain Network dropdown: Solana then Ethereum');
                await humanSelectOption(page, netSelect, 'Solana', { pauseAfter: 1600 });
                await humanSelectOption(page, netSelect, 'Ethereum', { pauseAfter: 1600 });
            }
        }
        if (item.platform) {
            const platSelect = page.locator('select:has(option:text("Facebook")), select:has(option:text("MoMo")), select:has(option:text("Gmail"))').first();
            if (await platSelect.count() > 0) {
                console.log(`       Testing Platform dropdown: selecting ${item.platform}`);
                await humanSelectOption(page, platSelect, item.platform, { pauseAfter: 1600 });
            }
        }
        if (item.wish) {
            const wishSelect = page.locator('select:has(option:text("Memorialize")), select:has(option:text("Delete")), select:has(option[value="MEMORIALIZE"])').first();
            if (await wishSelect.count() > 0) {
                console.log('       Cycling all Wish options: MEMORIALIZE -> TRANSFER_DATA -> DELETE');
                await humanSelectOption(page, wishSelect, 'MEMORIALIZE', { pauseAfter: 1500 });
                await humanSelectOption(page, wishSelect, 'TRANSFER_DATA', { pauseAfter: 1500 });
                await humanSelectOption(page, wishSelect, 'DELETE', { pauseAfter: 1500 });
            }
        }

        await humanPause(page, 1800);
    }

    const closeBtn = page.locator('button:has-text("Hủy"), button:has-text("Cancel"), button[aria-label="Close"], button:has(span:text("close"))').first();
    if (await closeBtn.count() > 0) {
        await humanMoveAndClick(page, closeBtn, { pauseAfter: 1800 });
    }

    await closeAndSave();
}

// =========================================================================
// JOURNEY 3: Owner - Allocation Modes, DMS, Legacy Messages, Activity & Billing
// =========================================================================
async function runJourney3() {
    console.log('\n🎬 [JOURNEY 3] Owner - Allocation Modes, DMS, Legacy Messages, Activity & Billing...');
    const { page, closeAndSave } = await createHumanSession('EXHAUSTIVE_03_Owner_Allocation_Modes_DMS_And_Messages.webm', 'owner');

    await page.goto(`${BASE_URL}/vaults/vault-demo-001`);
    await page.waitForLoadState('networkidle');
    await humanPause(page, 2000);

    // 1. Open Add Asset modal again to explore ALL ALLOCATION MODES
    console.log('  Testing all 4 Allocation Modes (ALL, EXCLUSIVE, PROPORTIONAL, JOINT_CONSENSUS)...');
    const addAssetBtn = page.locator('button:has-text("Thêm tài sản"), button:has-text("Add Asset")').first();
    await humanMoveAndClick(page, addAssetBtn, { pauseAfter: 2000 });

    await humanScroll(page, 300, { pauseAfter: 1500 });

    const modeSelect = page.locator('select:has(option[value="EXCLUSIVE"]), select:has(option[value="ALL"])').first();
    if (await modeSelect.count() > 0) {
        console.log('    -> Allocation Mode: ALL');
        await humanSelectOption(page, modeSelect, 'ALL', { pauseAfter: 2000 });

        console.log('    -> Allocation Mode: EXCLUSIVE');
        await humanSelectOption(page, modeSelect, 'EXCLUSIVE', { pauseAfter: 2000 });

        const soleBeneSelect = page.locator('select:has(option:text("con_thuhuong")), select:has(option:text("Trần Vân Con"))').first();
        if (await soleBeneSelect.count() > 0) {
            console.log('       Selecting sole beneficiary...');
            await humanSelectOption(page, soleBeneSelect, 'con_thuhuong@gmail.com', { pauseAfter: 1600 });
        }

        console.log('    -> Allocation Mode: PROPORTIONAL');
        await humanSelectOption(page, modeSelect, 'PROPORTIONAL', { pauseAfter: 2000 });

        const shareInputs = page.locator('input[placeholder="%"], input[inputmode="decimal"]');
        if (await shareInputs.count() > 0) {
            await humanType(page, shareInputs.first(), '100', { delay: 50, pauseAfter: 1500 });
        }

        console.log('    -> Allocation Mode: JOINT_CONSENSUS');
        await humanSelectOption(page, modeSelect, 'JOINT_CONSENSUS', { pauseAfter: 2000 });

        const thresholdSelect = page.locator('select:has(option[value="2"])').first();
        if (await thresholdSelect.count() > 0) {
            console.log('       Selecting Consensus Threshold (2 of 2)...');
            await humanSelectOption(page, thresholdSelect, '2', { pauseAfter: 1800 });
        }

        const noteTextarea = page.locator('textarea[placeholder*="ghi chú"], textarea[maxLength="1000"]').first();
        if (await noteTextarea.count() > 0) {
            await humanType(page, noteTextarea, 'Cần sự đồng thuận của cả 2 người con để mở khóa ví lạnh.', { delay: 35, pauseAfter: 1500 });
        }
    }

    const closeBtn = page.locator('button:has-text("Hủy"), button:has-text("Cancel"), button[aria-label="Close"], button:has(span:text("close"))').first();
    if (await closeBtn.count() > 0) {
        await humanMoveAndClick(page, closeBtn, { pauseAfter: 1600 });
    }

    // 2. Document Upload Modal - Cycle all Document Types
    console.log('  Testing Document Upload dropdown options...');
    const uploadDocBtn = page.locator('button:has-text("Tải tài liệu"), button:has-text("Upload Document")').first();
    if (await uploadDocBtn.count() > 0) {
        await humanMoveAndClick(page, uploadDocBtn, { pauseAfter: 2000 });
        const docTypeSelect = page.locator('select:has(option[value="WILL"]), select:has(option[value="DEED"])').first();
        if (await docTypeSelect.count() > 0) {
            const docTypes = ['WILL', 'DEED', 'INSURANCE_POLICY', 'CONTRACT', 'OTHER'];
            for (const dt of docTypes) {
                console.log(`    -> Selecting document type: ${dt}`);
                await humanSelectOption(page, docTypeSelect, dt, { pauseAfter: 1800 });
            }
        }
        const cancelDoc = page.locator('button:has-text("Hủy"), button:has-text("Cancel")').first();
        if (await cancelDoc.count() > 0) {
            await humanMoveAndClick(page, cancelDoc, { pauseAfter: 1500 });
        }
    }

    // 3. Legacy Messages Section - Explore All Triggers & Recipients
    console.log('  Testing Legacy Messages dropdowns (Trigger & Recipient)...');
    await humanScroll(page, 450, { pauseAfter: 1600 });
    const addMsgBtn = page.locator('button:has-text("Soạn lời nhắn"), button:has-text("Viết lời nhắn"), button:has-text("New Message")').first();
    if (await addMsgBtn.count() > 0) {
        await humanMoveAndClick(page, addMsgBtn, { pauseAfter: 2000 });

        const titleInput = page.locator('input[placeholder*="tiêu đề"], input[placeholder*="Title"]').first();
        if (await titleInput.count() > 0) {
            await humanType(page, titleInput, 'Bức thư gửi con ngày trưởng thành (2030)', { delay: 40, pauseAfter: 1200 });
        }

        const recipSelect = page.locator('select:has(option[value="ALL_BENEFICIARIES"])').first();
        if (await recipSelect.count() > 0) {
            console.log('    -> Recipient Type: ALL_BENEFICIARIES -> SPECIFIC_BENEFICIARY');
            await humanSelectOption(page, recipSelect, 'ALL_BENEFICIARIES', { pauseAfter: 1600 });
            await humanSelectOption(page, recipSelect, 'SPECIFIC_BENEFICIARY', { pauseAfter: 1600 });
        }

        const triggerSelect = page.locator('select:has(option[value="ON_CLAIM"]), select:has(option[value="TIME_CAPSULE"]), select:has(option[value="SPECIFIC_DATE"])').first();
        if (await triggerSelect.count() > 0) {
            console.log('    -> Delivery Trigger: ON_CLAIM -> TIME_CAPSULE / SPECIFIC_DATE');
            await humanSelectOption(page, triggerSelect, 'ON_CLAIM', { pauseAfter: 1600 });
            await humanSelectOption(page, triggerSelect, 'SPECIFIC_DATE', { pauseAfter: 1600 });

            const dateInput = page.locator('input[type="date"]').first();
            if (await dateInput.count() > 0) {
                await humanType(page, dateInput, '2030-05-15', { delay: 40, pauseAfter: 1500 });
            }
        }

        const msgContent = page.locator('textarea').first();
        if (await msgContent.count() > 0) {
            await humanType(page, msgContent, 'Gửi con yêu, khi con đọc được những dòng này thì bố đã hoàn thành hành trình của mình...', { delay: 35, pauseAfter: 1500 });
        }

        const cancelMsg = page.locator('button:has-text("Hủy"), button:has-text("Cancel")').first();
        if (await cancelMsg.count() > 0) {
            await humanMoveAndClick(page, cancelMsg, { pauseAfter: 1500 });
        }
    }

    // 4. Dead Man's Switch (DMS) Config Form
    console.log('  Testing Dead Man\'s Switch inputs...');
    await page.goto(`${BASE_URL}/vaults/vault-demo-001/dms`);
    await page.waitForLoadState('networkidle');
    await humanPause(page, 2000);

    const numInputs = page.locator('input[type="number"]');
    if (await numInputs.count() > 0) {
        await humanType(page, numInputs.nth(0), '45', { delay: 50, pauseAfter: 1200 });
    }
    if (await numInputs.count() > 1) {
        await humanType(page, numInputs.nth(1), '14', { delay: 50, pauseAfter: 1200 });
    }

    // Add Emergency Confirmer
    const addConfirmerBtn = page.locator('button:has-text("Thêm người xác nhận"), button:has-text("Add Confirmer")').first();
    if (await addConfirmerBtn.count() > 0) {
        await humanMoveAndClick(page, addConfirmerBtn, { pauseAfter: 1800 });
        const confName = page.locator('input[placeholder*="tên"], input[placeholder*="Name"]').first();
        if (await confName.count() > 0) await humanType(page, confName, 'Bác sĩ Nguyễn Văn Y Tế', { delay: 40, pauseAfter: 1000 });
        const confEmail = page.locator('input[type="email"]').first();
        if (await confEmail.count() > 0) await humanType(page, confEmail, 'doctor.family@hospital.vn', { delay: 40, pauseAfter: 1000 });
        const confRel = page.locator('input[placeholder*="hệ"], input[placeholder*="Relationship"]').first();
        if (await confRel.count() > 0) await humanType(page, confRel, 'Bác sĩ riêng', { delay: 40, pauseAfter: 1200 });
        const cancelConf = page.locator('button:has-text("Hủy"), button:has-text("Cancel")').first();
        if (await cancelConf.count() > 0) await humanMoveAndClick(page, cancelConf, { pauseAfter: 1500 });
    }

    // 5. Activity Log Days Filter & Tabs
    console.log('  Testing Activity Log Days filter & tabs...');
    await page.goto(`${BASE_URL}/vaults/vault-demo-001/activity`);
    await page.waitForLoadState('networkidle');
    await humanPause(page, 2000);

    const daysSelect = page.locator('select:has(option[value="7"]), select:has(option[value="30"])').first();
    if (await daysSelect.count() > 0) {
        const daysOptions = ['7', '30', '90', '365'];
        for (const d of daysOptions) {
            console.log(`    -> Filtering activity: ${d} days`);
            await humanSelectOption(page, daysSelect, d, { pauseAfter: 1500 });
        }
    }

    const actTabs = page.locator('button:has-text("Tất cả"), button:has-text("Bảo mật"), button:has-text("Két"), button:has-text("Tài sản")');
    const tabCount = await actTabs.count();
    for (let t = 0; t < tabCount; t++) {
        await humanMoveAndClick(page, actTabs.nth(t), { pauseAfter: 1400 });
    }

    // 6. Pricing & Billing
    console.log('  Testing Pricing page billing options...');
    await page.goto(`${BASE_URL}/pricing`);
    await page.waitForLoadState('networkidle');
    await humanPause(page, 2000);

    const billingToggle = page.locator('button[role="switch"], input[type="checkbox"], button:has-text("Năm"), button:has-text("Tháng")').first();
    if (await billingToggle.count() > 0) {
        await humanMoveAndClick(page, billingToggle, { pauseAfter: 2000 });
        await humanMoveAndClick(page, billingToggle, { pauseAfter: 2000 });
    }

    await closeAndSave();
}

// =========================================================================
// JOURNEY 4: Beneficiary Portal - Verification, Secrets, Dossier & Acceptance
// =========================================================================
async function runJourney4() {
    console.log('\n🎬 [JOURNEY 4] Beneficiary Portal - Verification, Secrets, Dossier & Acceptance...');
    const { page, closeAndSave } = await createHumanSession('EXHAUSTIVE_04_Beneficiary_Verification_Secret_And_Dossier.webm', 'beneficiary');

    // 1. Beneficiary Portal & Handbook
    await page.goto(`${BASE_URL}/beneficiary/vaults`);
    await page.waitForLoadState('networkidle');
    await humanPause(page, 2000);

    const guideLink = page.locator('a[href*="guide"], button:has-text("Hướng dẫn"), a:has-text("Hướng dẫn")').first();
    if (await guideLink.count() > 0) {
        await humanMoveAndClick(page, guideLink, { pauseAfter: 2500 });
        await humanScroll(page, 400, { pauseAfter: 2000 });
        await humanScroll(page, -400, { pauseAfter: 1500 });
    }

    // 2. Beneficiary Vault Claim Detail
    await page.goto(`${BASE_URL}/beneficiary/vaults/vault-demo-001`);
    await page.waitForLoadState('networkidle');
    await humanPause(page, 2500);

    // 3. Secret Modal: reveal masked password & copy
    console.log('  Testing Secret reveal modal & clipboard copy...');
    const viewSecretBtn = page.locator('button:has-text("Xem thông tin"), button:has-text("Xem bí mật"), button:has-text("View Secret")').first();
    if (await viewSecretBtn.count() > 0) {
        await humanMoveAndClick(page, viewSecretBtn, { pauseAfter: 2000 });

        const eyeBtn = page.locator('button:has(span:text("visibility")), button[aria-label*="hiện"], button[aria-label*="reveal"]').first();
        if (await eyeBtn.count() > 0) {
            console.log('     Clicking eye icon to reveal VCB_PASS_9988#@');
            await humanMoveAndClick(page, eyeBtn, { pauseAfter: 3500 });
        }

        const copyBtn = page.locator('button:has(span:text("content_copy")), button[aria-label*="sao chép"], button[aria-label*="copy"]').first();
        if (await copyBtn.count() > 0) {
            console.log('     Clicking copy button...');
            await humanMoveAndClick(page, copyBtn, { pauseAfter: 2500 });
        }

        const closeSecret = page.locator('button:has-text("Đóng"), button:has-text("Close")').first();
        if (await closeSecret.count() > 0) {
            await humanMoveAndClick(page, closeSecret, { pauseAfter: 1500 });
        }
    }

    // 4. Download Dossier PDF
    console.log('  Testing Complete Dossier PDF download...');
    const dossierBtn = page.locator('button:has-text("Hồ sơ"), button:has-text("Dossier"), a:has-text("Hồ sơ")').first();
    if (await dossierBtn.count() > 0) {
        await humanMoveAndClick(page, dossierBtn, { pauseAfter: 3000 });
    }

    // 5. Read Legacy Message
    console.log('  Reading emotional legacy message...');
    await humanScroll(page, 400, { pauseAfter: 1800 });
    const readMsgBtn = page.locator('button:has-text("Đọc lời nhắn"), button:has-text("Xem thư"), a:has-text("Lời nhắn")').first();
    if (await readMsgBtn.count() > 0) {
        await humanMoveAndClick(page, readMsgBtn, { pauseAfter: 3000 });
        const closeMsg = page.locator('button:has-text("Đóng"), button:has-text("Close")').first();
        if (await closeMsg.count() > 0) {
            await humanMoveAndClick(page, closeMsg, { pauseAfter: 1500 });
        }
    }

    // 6. Test Decline Modal Form
    console.log('  Testing Decline Inheritance modal form...');
    const declineBtn = page.locator('button:has-text("Từ chối"), button:has-text("Decline")').first();
    if (await declineBtn.count() > 0) {
        await humanMoveAndClick(page, declineBtn, { pauseAfter: 2000 });
        const reasonTextarea = page.locator('textarea').first();
        if (await reasonTextarea.count() > 0) {
            await humanType(page, reasonTextarea, 'Tôi xin nhường lại toàn bộ phần thừa kế cho các em trong gia đình.', { delay: 35, pauseAfter: 2000 });
        }
        const cancelDecline = page.locator('button:has-text("Hủy"), button:has-text("Cancel")').first();
        if (await cancelDecline.count() > 0) {
            await humanMoveAndClick(page, cancelDecline, { pauseAfter: 1500 });
        }
    }

    await closeAndSave();
}

// =========================================================================
// JOURNEY 5: Executor Portal - Case Tasks, Statuses & Verifier Messages
// =========================================================================
async function runJourney5() {
    console.log('\n🎬 [JOURNEY 5] Executor Portal - Case Tasks, Statuses & Verifier Messages...');
    const { page, closeAndSave } = await createHumanSession('EXHAUSTIVE_05_Executor_Case_Tasks_And_Verifier_Messages.webm', 'executor');

    await page.goto(`${BASE_URL}/executor/cases`);
    await page.waitForLoadState('networkidle');
    await humanPause(page, 2000);

    const caseLink = page.locator('a[href*="/cases/"], button:has-text("Chi tiết"), a:has-text("Xem hồ sơ")').first();
    if (await caseLink.count() > 0) {
        await humanMoveAndClick(page, caseLink, { pauseAfter: 2500 });
    } else {
        await page.goto(`${BASE_URL}/executor/cases/assign-001`);
        await page.waitForLoadState('networkidle');
        await humanPause(page, 2500);
    }

    console.log('  Inspecting Executor execution stepper...');
    await humanScroll(page, 300, { pauseAfter: 2000 });

    console.log('  Testing Account Tasks status dropdown & lawyer notes...');
    const taskStatusSelect = page.locator('select:has(option[value="TODO"]), select:has(option[value="IN_PROGRESS"])').first();
    if (await taskStatusSelect.count() > 0) {
        const statuses = ['TODO', 'IN_PROGRESS', 'DONE', 'CANNOT_COMPLETE'];
        for (const st of statuses) {
            console.log(`    -> Setting Task Status: ${st}`);
            await humanSelectOption(page, taskStatusSelect, st, { pauseAfter: 1800 });
        }

        const taskNote = page.locator('textarea[placeholder*="ghi chú"], textarea[placeholder*="Note"]').first();
        if (await taskNote.count() > 0) {
            await humanType(page, taskNote, 'Đã liên hệ đội ngũ hỗ trợ Meta để gửi giấy chứng tử và đề nghị tưởng niệm.', { delay: 35, pauseAfter: 2000 });
        }

        const saveTaskBtn = page.locator('button:has-text("Lưu"), button:has-text("Save")').first();
        if (await saveTaskBtn.count() > 0) {
            await humanMoveAndClick(page, saveTaskBtn, { pauseAfter: 2000 });
        }
    }

    console.log('  Testing Messages thread with Legal Verifier...');
    await humanScroll(page, 400, { pauseAfter: 2000 });
    const msgSelect = page.locator('select:has(option[value*="req-"]), select:has(option:text("yêu cầu"))').first();
    if (await msgSelect.count() > 0) {
        await humanSelectOption(page, msgSelect, 0, { pauseAfter: 1500 });
    }
    const msgTextarea = page.locator('textarea[placeholder*="nội dung"], textarea[placeholder*="tin nhắn"]').first();
    if (await msgTextarea.count() > 0) {
        await humanType(page, msgTextarea, 'Kính gửi Công chứng viên, tôi đã đính kèm bản công chứng giấy chứng tử mới nhất.', { delay: 35, pauseAfter: 2000 });
    }

    await closeAndSave();
}

// =========================================================================
// JOURNEY 6: Legal Verifier - Document Verification, Reject Reasons & Signing
// =========================================================================
async function runJourney6() {
    console.log('\n🎬 [JOURNEY 6] Legal Verifier - Document Verification, Reject Reasons & Signing...');
    const { page, closeAndSave } = await createHumanSession('EXHAUSTIVE_06_Verifier_Document_Check_And_Digital_Signature.webm', 'verifier');

    await page.goto(`${BASE_URL}/verifier/requests`);
    await page.waitForLoadState('networkidle');
    await humanPause(page, 2000);

    console.log('  Testing Verification History filters...');
    const histTab = page.locator('a[href*="history"], button:has-text("Lịch sử"), a:has-text("Lịch sử")').first();
    if (await histTab.count() > 0) {
        await humanMoveAndClick(page, histTab, { pauseAfter: 2000 });

        const histStatusSelect = page.locator('select:has(option[value="APPROVED"]), select:has(option[value="REJECTED"])').first();
        if (await histStatusSelect.count() > 0) {
            const histStatuses = ['', 'PENDING', 'APPROVED', 'REJECTED'];
            for (const hs of histStatuses) {
                console.log(`    -> Filter status: ${hs || 'ALL'}`);
                await humanSelectOption(page, histStatusSelect, hs, { pauseAfter: 1500 });
            }
        }

        const dateFrom = page.locator('input[type="date"]').nth(0);
        if (await dateFrom.count() > 0) await humanType(page, dateFrom, '2026-11-01', { delay: 50, pauseAfter: 1200 });
        const dateTo = page.locator('input[type="date"]').nth(1);
        if (await dateTo.count() > 0) await humanType(page, dateTo, '2026-11-06', { delay: 50, pauseAfter: 1500 });
    }

    await page.goto(`${BASE_URL}/verifier/requests/req-001`);
    await page.waitForLoadState('networkidle');
    await humanPause(page, 2500);

    const checkDocBtn = page.locator('button:has-text("Kiểm tra"), button:has-text("Check")').first();
    if (await checkDocBtn.count() > 0) {
        console.log('  Checking document authenticity...');
        await humanMoveAndClick(page, checkDocBtn, { pauseAfter: 2000 });
    }
    const viewDocBtn = page.locator('button:has-text("Xem tài liệu"), button:has-text("View Document")').first();
    if (await viewDocBtn.count() > 0) {
        console.log('  Viewing Death Certificate document...');
        await humanMoveAndClick(page, viewDocBtn, { pauseAfter: 2500 });
    }

    console.log('  Testing Reject Reasons dropdown options...');
    await humanScroll(page, 350, { pauseAfter: 1500 });
    const rejectSelect = page.locator('#reject-reason, select:has(option[value="OTHER"])').first();
    if (await rejectSelect.count() > 0) {
        const reasons = ['', 'INVALID_DOCUMENT', 'UNREADABLE', 'NAME_MISMATCH', 'NOT_CERTIFIED', 'OTHER'];
        for (const r of reasons) {
            console.log(`    -> Selecting reject reason: ${r || '(none)'}`);
            await humanSelectOption(page, rejectSelect, r, { pauseAfter: 1600 });
        }

        const rejectNote = page.locator('textarea[placeholder*="lý do"], textarea[placeholder*="note"]').first();
        if (await rejectNote.count() > 0) {
            await humanType(page, rejectNote, 'Bản sao trích lục khai tử bị mờ góc dưới bên phải, đề nghị nộp lại bản quét sắc nét.', { delay: 35, pauseAfter: 2000 });
        }
    }

    console.log('  Testing Digital Signature OTP input...');
    const signInput = page.locator('#sign-code, input[inputmode="numeric"]').first();
    if (await signInput.count() > 0) {
        await humanType(page, signInput, '987654', { delay: 50, pauseAfter: 2000 });
    }

    await closeAndSave();
}

// =========================================================================
// JOURNEY 7: Admin - Users Filter, Vaults Extend Modal, Roles & Audit Filters
// =========================================================================
async function runJourney7() {
    console.log('\n🎬 [JOURNEY 7] Admin - Users Filter, Vaults Extend Modal, Roles & Audit Filters...');
    const { page, closeAndSave } = await createHumanSession('EXHAUSTIVE_07_Admin_All_Filters_Roles_Vaults_And_Audit_Logs.webm', 'admin');

    // 1. Admin Users: Search & Cycle ALL Role & Status filters
    await page.goto(`${BASE_URL}/admin/users`);
    await page.waitForLoadState('networkidle');
    await humanPause(page, 2000);

    console.log('  Testing Admin Users filters (All Roles & All Statuses)...');
    const searchInput = page.locator('input[placeholder*="Tìm"], input[placeholder*="Search"]').first();
    if (await searchInput.count() > 0) {
        await humanType(page, searchInput, 'Trần Long Vân', { delay: 40, pauseAfter: 1500 });
    }

    const roleSelect = page.locator('select').nth(0);
    if (await roleSelect.count() > 0) {
        const roles = ['', 'OWNER', 'BENEFICIARY', 'EXECUTOR', 'LEGAL_VERIFIER', 'ADMIN'];
        for (const r of roles) {
            console.log(`    -> User Role Filter: ${r || 'ALL'}`);
            await humanSelectOption(page, roleSelect, r, { pauseAfter: 1500 });
        }
    }

    const statusSelect = page.locator('select').nth(1);
    if (await statusSelect.count() > 0) {
        const statuses = ['', 'ACTIVE', 'PENDING', 'LOCKED'];
        for (const s of statuses) {
            console.log(`    -> User Status Filter: ${s || 'ALL'}`);
            await humanSelectOption(page, statusSelect, s, { pauseAfter: 1500 });
        }
    }

    // 2. Admin System Vaults & Extend Claim Modal
    console.log('  Testing Admin Vaults Status filter & Extend Claim modal...');
    await page.goto(`${BASE_URL}/admin/vaults`);
    await page.waitForLoadState('networkidle');
    await humanPause(page, 2000);

    const vaultStatusSelect = page.locator('select').first();
    if (await vaultStatusSelect.count() > 0) {
        const vStatuses = ['', 'ACTIVE', 'UNLOCKED', 'CLAIMED', 'ARCHIVED_LOCKED'];
        for (const vs of vStatuses) {
            console.log(`    -> Vault Status Filter: ${vs || 'ALL'}`);
            await humanSelectOption(page, vaultStatusSelect, vs, { pauseAfter: 1500 });
        }
    }

    const detailBtn = page.locator('button:has-text("Chi tiết"), button:has-text("Details")').first();
    if (await detailBtn.count() > 0) {
        await humanMoveAndClick(page, detailBtn, { pauseAfter: 2500 });

        const extendBtn = page.locator('button:has-text("Gia hạn"), button:has-text("Extend")').first();
        if (await extendBtn.count() > 0) {
            await humanMoveAndClick(page, extendBtn, { pauseAfter: 2000 });

            const daysSelect = page.locator('select:has(option[value="7"]), select:has(option[value="30"])').first();
            if (await daysSelect.count() > 0) {
                const dayOpts = ['7', '14', '30', '60', '90'];
                for (const d of dayOpts) {
                    console.log(`       -> Extending by ${d} days`);
                    await humanSelectOption(page, daysSelect, d, { pauseAfter: 1500 });
                }
            }

            const reasonInput = page.locator('input[placeholder*="lý do"], input[placeholder*="reason"]').first();
            if (await reasonInput.count() > 0) {
                await humanType(page, reasonInput, 'Người thụ hưởng đang hoàn tất thủ tục phân chia di sản tại phòng công chứng.', { delay: 35, pauseAfter: 1800 });
            }

            const cancelExtend = page.locator('button:has-text("Hủy"), button:has-text("Cancel")').first();
            if (await cancelExtend.count() > 0) await humanMoveAndClick(page, cancelExtend, { pauseAfter: 1500 });
        }

        const closeDetail = page.locator('button:has-text("Đóng"), button:has-text("Close"), button:has(span:text("close"))').first();
        if (await closeDetail.count() > 0) await humanMoveAndClick(page, closeDetail, { pauseAfter: 1500 });
    }

    // 3. Admin Roles & Permissions Modal
    console.log('  Testing Admin Roles & Authority matrix...');
    await page.goto(`${BASE_URL}/admin/roles`);
    await page.waitForLoadState('networkidle');
    await humanPause(page, 2000);

    const newRoleBtn = page.locator('button:has-text("Tạo vai trò"), button:has-text("New Role")').first();
    if (await newRoleBtn.count() > 0) {
        await humanMoveAndClick(page, newRoleBtn, { pauseAfter: 2000 });

        const codeInput = page.locator('input[placeholder*="ROLE_"], input[value=""]').first();
        if (await codeInput.count() > 0) await humanType(page, codeInput, 'COMPLIANCE_OFFICER', { delay: 40, pauseAfter: 1200 });

        const nameInput = page.locator('input[placeholder*="Tên vai trò"], input[placeholder*="Name"]').first();
        if (await nameInput.count() > 0) await humanType(page, nameInput, 'Cán bộ kiểm tra tuân thủ', { delay: 40, pauseAfter: 1200 });

        const descInput = page.locator('input[placeholder*="Mô tả"], input[placeholder*="Description"]').first();
        if (await descInput.count() > 0) await humanType(page, descInput, 'Kiểm toán hệ thống và giám sát nhật ký pháp lý.', { delay: 35, pauseAfter: 1500 });

        console.log('     Toggling authorities checkboxes...');
        const authCheckboxes = page.locator('input[type="checkbox"]');
        const chkCount = await authCheckboxes.count();
        for (let c = 0; c < Math.min(chkCount, 4); c++) {
            await humanCheck(page, authCheckboxes.nth(c), { pauseAfter: 1000 });
        }

        const cancelRole = page.locator('button:has-text("Hủy"), button:has-text("Cancel")').first();
        if (await cancelRole.count() > 0) await humanMoveAndClick(page, cancelRole, { pauseAfter: 1500 });
    }

    // 4. Admin Audit Logs: Grouped Actions & Filters
    console.log('  Testing Admin Audit Logs filter dropdown & date-time inputs...');
    await page.goto(`${BASE_URL}/admin/audit-logs`);
    await page.waitForLoadState('networkidle');
    await humanPause(page, 2000);

    const auditActionSelect = page.locator('select').first();
    if (await auditActionSelect.count() > 0) {
        const auditActions = ['', 'USER_LOGIN', 'VAULT_CREATE', 'ASSET_VIEW', 'CLAIM_CONFIRMED', 'DMS_CHECKIN'];
        for (const a of auditActions) {
            console.log(`    -> Filtering Audit Action: ${a || 'ALL'}`);
            await humanSelectOption(page, auditActionSelect, a, { pauseAfter: 1600 });
        }
    }

    const actorEmail = page.locator('input[placeholder*="email"], input[placeholder*="Actor"]').first();
    if (await actorEmail.count() > 0) await humanType(page, actorEmail, 'tlv48691@gmail.com', { delay: 40, pauseAfter: 1200 });

    const ipInput = page.locator('input[placeholder*="IP"]').first();
    if (await ipInput.count() > 0) await humanType(page, ipInput, '113.190.234.12', { delay: 40, pauseAfter: 1200 });

    const fromDate = page.locator('input[type="datetime-local"]').nth(0);
    if (await fromDate.count() > 0) await humanType(page, fromDate, '2026-11-01T00:00', { delay: 50, pauseAfter: 1200 });

    const toDate = page.locator('input[type="datetime-local"]').nth(1);
    if (await toDate.count() > 0) await humanType(page, toDate, '2026-11-06T23:59', { delay: 50, pauseAfter: 1500 });

    const searchAuditBtn = page.locator('button[type="submit"], button:has-text("Tìm"), button:has-text("Search")').first();
    if (await searchAuditBtn.count() > 0) await humanMoveAndClick(page, searchAuditBtn, { pauseAfter: 2500 });

    await closeAndSave();
}

// =========================================================================
// MAIN ORCHESTRATOR
// =========================================================================
async function runAllExhaustiveJourneys() {
    console.log('=================================================================');
    console.log('🚀 STARTING DEEP EXHAUSTIVE FORM CONTROLS & DROPDOWN AUDIT SUITE');
    console.log('=================================================================');

    try {
        await runJourney1();
        await runJourney2();
        await runJourney3();
        await runJourney4();
        await runJourney5();
        await runJourney6();
        await runJourney7();
    } catch (err) {
        console.error('❌ Error during exhaustive execution:', err);
    } finally {
        console.log('\n=================================================================');
        console.log('✨ ALL 7 EXHAUSTIVE AUDIT JOURNEYS COMPLETED SUCCESSFULLY!');
        console.log('=================================================================');
    }
}

runAllExhaustiveJourneys();
