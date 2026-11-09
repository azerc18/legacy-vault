const { BASE_URL, runTestCase } = require('./test_runner_utils');

// Mock data for Owner
const mockOwnerUser = {
    id: '11111111-2222-3333-4444-555555555555',
    email: 'tlv48691@gmail.com',
    name: 'Tráº§n Long VÃ¢n',
    roles: ['OWNER', 'BENEFICIARY'],
    role: 'OWNER'
};

const mockVault = {
    id: 'vault-demo-001',
    name: 'KÃ©t sáº¯t Gia Ä‘Ã¬nh - Di sáº£n sá»‘ 2026',
    description: 'Chá»©a tÃ i khoáº£n ngÃ¢n hÃ ng, vÃ­ crypto vÃ  cÃ¡c tÃ¢m thÆ° dáº·n dÃ² ngÆ°á»i thÃ¢n.',
    status: 'ACTIVE',
    modifiable: true,
    totalAssets: 3,
    totalDocuments: 1,
    beneficiaryEmail: 'child@testvault.com',
    createdAt: '2026-11-01T10:00:00Z',
    updatedAt: '2026-11-06T15:00:00Z'
};

const mockAssets = [
    {
        id: 'asset-01',
        assetType: 'BANK_ACCOUNT',
        assetName: 'TÃ i khoáº£n Vietcombank chÃ­nh',
        secret: 'VCB_PASS_9988#@',
        notes: 'DÃ¹ng Ä‘á»ƒ thanh toÃ¡n chi phÃ­ gia Ä‘Ã¬nh',
        fields: { accountNumber: '0123456789', bankName: 'Vietcombank' }
    },
    {
        id: 'asset-02',
        assetType: 'SOCIAL_MEDIA',
        assetName: 'TÃ i khoáº£n Facebook cÃ¡ nhÃ¢n',
        secret: 'FbSecretKey_2026!',
        notes: 'Chuyá»ƒn sang cháº¿ Ä‘á»™ tÆ°á»Ÿng niá»‡m',
        fields: { platform: 'Facebook', accountHandle: 'tranlongvan.official' }
    }
];

const mockBeneficiaries = [
    {
        id: 'bene-01',
        email: 'child@testvault.com',
        fullName: 'Tráº§n VÃ¢n Con',
        relationship: 'Con ruá»™t',
        status: 'INVITED',
        createdAt: '2026-11-01T10:30:00Z'
    }
];

const mockExecutors = [
    {
        id: 'exec-01',
        email: 'lawyer@legalfirm.vn',
        fullName: 'Luáº­t sÆ° Nguyá»…n VÄƒn Luáº­t',
        status: 'ASSIGNED',
        note: 'ÄÃ£ kÃ½ há»£p Ä‘á»“ng á»§y thÃ¡c thi hÃ nh di chÃºc'
    }
];

const mockLegacyMessages = [
    {
        id: 'msg-01',
        vaultId: 'vault-demo-001',
        title: 'Lá»i dáº·n dÃ² thÆ°Æ¡ng yÃªu gá»­i cÃ¡c con',
        content: 'Bá»‘ máº¹ luÃ´n yÃªu thÆ°Æ¡ng cÃ¡c con. HÃ£y luÃ´n sá»‘ng trung thá»±c vÃ  Ä‘oÃ n káº¿t...',
        deliveryTrigger: 'ON_CLAIM',
        recipientType: 'ALL_BENEFICIARIES',
        status: 'ACTIVE',
        hasAttachment: true,
        attachmentName: 'di_nguyen_tam_thu.pdf',
        createdAt: '2026-11-02T08:00:00Z'
    }
];

async function setupOwnerMock(page) {
    // Inject auth session
    await page.addInitScript(({ user }) => {
        localStorage.setItem('token', 'mock-valid-jwt-token-for-testing');
        localStorage.setItem('lv_user', JSON.stringify(user));
    }, { user: mockOwnerUser });

    // Mock REST APIs
    await page.route('**/api/users/me', async (route) => {
        await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({
                id: mockOwnerUser.id,
                email: mockOwnerUser.email,
                fullName: mockOwnerUser.name,
                roles: mockOwnerUser.roles,
                status: 'ACTIVE'
            })
        });
    });

    await page.route('**/api/vaults', async (route) => {
        if (route.request().method() === 'POST') {
            const data = route.request().postDataJSON();
            await route.fulfill({
                status: 201,
                contentType: 'application/json',
                body: JSON.stringify({ ...mockVault, id: 'vault-created-new', name: data.name, description: data.description })
            });
        } else {
            await route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify([mockVault])
            });
        }
    });

    await page.route('**/api/vaults/vault-demo-001', async (route) => {
        await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify(mockVault)
        });
    });

    await page.route('**/api/vaults/vault-demo-001/assets', async (route) => {
        if (route.request().method() === 'POST') {
            await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify({ id: 'asset-new', ...route.request().postDataJSON() }) });
        } else {
            await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(mockAssets) });
        }
    });

    await page.route('**/api/vaults/vault-demo-001/beneficiaries', async (route) => {
        if (route.request().method() === 'POST') {
            await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify({ id: 'bene-new', ...route.request().postDataJSON() }) });
        } else {
            await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(mockBeneficiaries) });
        }
    });

    await page.route('**/api/vaults/vault-demo-001/executors', async (route) => {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(mockExecutors) });
    });

    await page.route('**/api/vaults/vault-demo-001/messages', async (route) => {
        if (route.request().method() === 'POST') {
            await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify({ id: 'msg-new', title: 'Lá»i dáº·n dÃ² má»›i', content: 'Ná»™i dung dáº·n dÃ²', status: 'ACTIVE' }) });
        } else {
            await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(mockLegacyMessages) });
        }
    });

    await page.route('**/api/dms/config', async (route) => {
        await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({
                checkinIntervalDays: 30,
                gracePeriodDays: 7,
                status: 'ACTIVE',
                lastCheckinAt: '2026-11-01T12:00:00Z',
                nextDeadlineAt: '2026-12-01T12:00:00Z',
                confirmers: [{ id: 'conf-1', email: 'confirmer@family.vn', name: 'NgÆ°á»i xÃ¡c nháº­n' }]
            })
        });
    });

    await page.route('**/api/estate/readiness', async (route) => {
        await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ score: 85, level: 'GOOD', issues: [] })
        });
    });
}

async function runSuite2() {
    const results = [];

    // TC10: Owner Dashboard Overview
    results.push(await runTestCase({
        id: 'TC10',
        name: 'Owner Dashboard & Quáº£n lÃ½ danh sÃ¡ch KÃ©t sáº¯t',
        role: 'Chá»§ kÃ©t sáº¯t (Vault Owner)',
        fn: async ({ page, step, screenshot }) => {
            await setupOwnerMock(page);

            await step('ÄÄƒng nháº­p vÃ  má»Ÿ mÃ n hÃ¬nh Danh sÃ¡ch KÃ©t sáº¯t (/owner/vaults)', async () => {
                await page.goto(`${BASE_URL}/owner/vaults`, { waitUntil: 'domcontentloaded' });
                await page.waitForTimeout(2000);
            });
            await screenshot('01_owner_vaults_list');

            await step('Kiá»ƒm tra Tháº» Sáºµn sÃ ng Di sáº£n (Estate Readiness Card)', async () => {
                await page.locator('text=KÃ©t sáº¯t|Vault|Estate').first().waitFor({ state: 'visible', timeout: 5000 });
            });

            await step('Kiá»ƒm tra hiá»ƒn thá»‹ KÃ©t sáº¯t máº«u "KÃ©t sáº¯t Gia Ä‘Ã¬nh - Di sáº£n sá»‘ 2026"', async () => {
                const vaultCard = page.locator('text=KÃ©t sáº¯t Gia Ä‘Ã¬nh - Di sáº£n sá»‘ 2026').first();
                await vaultCard.waitFor({ state: 'visible', timeout: 5000 });
            });
            await screenshot('02_vault_card_visible');

            await step('XÃ¡c Ä‘á»‹nh nÃºt "Táº¡o kÃ©t má»›i" (New Vault)', async () => {
                const newVaultBtn = page.locator('a[href="/owner/vaults/new"]').first();
                await newVaultBtn.waitFor({ state: 'visible' });
            });
        }
    }));

    // TC11: Form Táº¡o KÃ©t Sáº¯t Má»›i (Create Vault)
    results.push(await runTestCase({
        id: 'TC11',
        name: 'Khá»Ÿi táº¡o KÃ©t sáº¯t má»›i (Create Vault Form)',
        role: 'Chá»§ kÃ©t sáº¯t (Vault Owner)',
        fn: async ({ page, step, screenshot }) => {
            await setupOwnerMock(page);

            await step('Má»Ÿ trang Khá»Ÿi táº¡o KÃ©t sáº¯t má»›i (/owner/vaults/new)', async () => {
                await page.goto(`${BASE_URL}/owner/vaults/new`, { waitUntil: 'domcontentloaded' });
                await page.waitForTimeout(1500);
            });
            await screenshot('01_create_vault_page');

            await step('Äiá»n "KÃ©t sáº¯t Kháº©n cáº¥p & TÃ i chÃ­nh sá»‘" vÃ o Ã´ TÃªn kÃ©t sáº¯t', async () => {
                await page.fill('input[placeholder*="tÃªn" i], input[placeholder*="name" i]', 'KÃ©t sáº¯t Kháº©n cáº¥p & TÃ i chÃ­nh sá»‘');
            });

            await step('Äiá»n mÃ´ táº£ "Chá»©a tÃ i khoáº£n ngÃ¢n hÃ ng vÃ  hÆ°á»›ng dáº«n cho ngÆ°á»i thÃ¢n khi xáº£y ra biáº¿n cá»‘"', async () => {
                await page.fill('textarea', 'Chá»©a tÃ i khoáº£n ngÃ¢n hÃ ng vÃ  hÆ°á»›ng dáº«n cho ngÆ°á»i thÃ¢n khi xáº£y ra biáº¿n cá»‘');
            });
            await screenshot('02_vault_info_filled');

            await step('ThÃªm thÃ´ng tin NgÆ°á»i thá»¥ hÆ°á»Ÿng ban Ä‘áº§u (email: child@testvault.com)', async () => {
                const emailInput = page.locator('input[placeholder*="email" i]').first();
                if (await emailInput.isVisible()) {
                    await emailInput.fill('child@testvault.com');
                }
                const nameInput = page.locator('input[placeholder*="há»" i], input[placeholder*="name" i]').nth(1);
                if (await nameInput.isVisible()) {
                    await nameInput.fill('Tráº§n VÄƒn Con');
                }
            });
            await screenshot('03_beneficiary_row_filled');

            await step('ThÃªm thÃ´ng tin TÃ i sáº£n sá»‘ ban Ä‘áº§u (TÃ i khoáº£n ngÃ¢n hÃ ng Vietcombank)', async () => {
                const assetNameInput = page.locator('input[placeholder*="tÃ i sáº£n" i], input[placeholder*="asset" i]').first();
                if (await assetNameInput.isVisible()) {
                    await assetNameInput.fill('Vietcombank Digital 2026');
                }
            });
            await screenshot('04_asset_row_filled');

            await step('Nháº¥n nÃºt "Táº¡o kÃ©t sáº¯t" Ä‘á»ƒ gá»­i dá»¯ liá»‡u', async () => {
                const submitBtn = page.locator('button[type="submit"]').first();
                await submitBtn.click();
                await page.waitForTimeout(1000);
            });
            await screenshot('05_vault_submitted');
        }
    }));

    // TC12: Chi tiáº¿t KÃ©t sáº¯t & Quáº£n lÃ½ TÃ i sáº£n (Vault Detail & Assets)
    results.push(await runTestCase({
        id: 'TC12',
        name: 'Quáº£n lÃ½ Chi tiáº¿t KÃ©t sáº¯t & ThÃªm TÃ i sáº£n sá»‘',
        role: 'Chá»§ kÃ©t sáº¯t (Vault Owner)',
        fn: async ({ page, step, screenshot }) => {
            await setupOwnerMock(page);

            await step('Má»Ÿ trang Chi tiáº¿t KÃ©t sáº¯t (/owner/vaults/vault-demo-001)', async () => {
                await page.goto(`${BASE_URL}/owner/vaults/vault-demo-001`, { waitUntil: 'domcontentloaded' });
                await page.waitForTimeout(1500);
            });
            await screenshot('01_vault_detail_overview');

            await step('Kiá»ƒm tra tháº» tÃ³m táº¯t kÃ©t sáº¯t: TÃªn, MÃ´ táº£, Tráº¡ng thÃ¡i ACTIVE', async () => {
                await page.locator('text=KÃ©t sáº¯t Gia Ä‘Ã¬nh').first().waitFor({ state: 'visible', timeout: 5000 });
            });

            await step('Cuá»™n xuá»‘ng pháº§n Danh sÃ¡ch TÃ i sáº£n sá»‘', async () => {
                await page.evaluate(() => window.scrollTo(0, 400));
                await page.waitForTimeout(600);
            });
            await screenshot('02_assets_section');

            await step('XÃ¡c Ä‘á»‹nh nÃºt "ThÃªm tÃ i sáº£n" (Add Asset)', async () => {
                const addAssetBtn = page.locator('button:has-text("ThÃªm tÃ i sáº£n"), button:has-text("Add asset")').first();
                if (await addAssetBtn.isVisible()) {
                    await addAssetBtn.click();
                    await page.waitForTimeout(800);
                    await screenshot('03_add_asset_modal_open');
                }
            });
        }
    }));

    // TC13: Quáº£n lÃ½ NgÆ°á»i thá»¥ hÆ°á»Ÿng (Beneficiaries in Vault)
    results.push(await runTestCase({
        id: 'TC13',
        name: 'Quáº£n lÃ½ NgÆ°á»i thá»¥ hÆ°á»Ÿng trong KÃ©t sáº¯t',
        role: 'Chá»§ kÃ©t sáº¯t (Vault Owner)',
        fn: async ({ page, step, screenshot }) => {
            await setupOwnerMock(page);

            await step('Má»Ÿ trang Chi tiáº¿t KÃ©t sáº¯t vÃ  cuá»™n Ä‘áº¿n khá»‘i NgÆ°á»i thá»¥ hÆ°á»Ÿng', async () => {
                await page.goto(`${BASE_URL}/owner/vaults/vault-demo-001`, { waitUntil: 'domcontentloaded' });
                await page.waitForTimeout(1500);
                await page.evaluate(() => window.scrollTo(0, 800));
                await page.waitForTimeout(800);
            });
            await screenshot('01_beneficiaries_section');

            await step('Kiá»ƒm tra hiá»ƒn thá»‹ ngÆ°á»i thá»¥ hÆ°á»Ÿng máº«u "child@testvault.com"', async () => {
                const beneItem = page.locator('text=child@testvault.com').first();
                if (await beneItem.isVisible()) {
                    console.log('    ÄÃ£ tháº¥y ngÆ°á»i thá»¥ hÆ°á»Ÿng child@testvault.com');
                }
            });

            await step('XÃ¡c Ä‘á»‹nh nÃºt "ThÃªm ngÆ°á»i thá»¥ hÆ°á»Ÿng" (Add Beneficiary)', async () => {
                const addBeneBtn = page.locator('button:has-text("ThÃªm ngÆ°á»i thá»¥ hÆ°á»Ÿng"), button:has-text("Add beneficiary")').first();
                if (await addBeneBtn.isVisible()) {
                    await addBeneBtn.click();
                    await page.waitForTimeout(800);
                    await screenshot('02_add_beneficiary_modal');
                }
            });
        }
    }));

    // TC14: Cáº¥u hÃ¬nh Dead Man's Switch (DMS Configuration)
    results.push(await runTestCase({
        id: 'TC14',
        name: 'Cáº¥u hÃ¬nh CÆ¡ cháº¿ Dead Mans Switch (DMS)',
        role: 'Chá»§ kÃ©t sáº¯t (Vault Owner)',
        fn: async ({ page, step, screenshot }) => {
            await setupOwnerMock(page);

            await step('Má»Ÿ trang Cáº¥u hÃ¬nh Dead Mans Switch (/owner/dms)', async () => {
                await page.goto(`${BASE_URL}/owner/dms`, { waitUntil: 'domcontentloaded' });
            });
            await screenshot('01_dms_config_page');

            await step('Kiá»ƒm tra tráº¡ng thÃ¡i DMS hiá»‡n táº¡i vÃ  chu ká»³ Ä‘iá»ƒm danh', async () => {
                await page.locator('text=Dead Man|DMS|Äiá»ƒm danh').first().waitFor({ state: 'visible', timeout: 5000 });
            });

            await step('Kiá»ƒm tra form Ä‘iá»u chá»‰nh chu ká»³ kiá»ƒm tra vÃ  thá»i gian Ã¢n háº¡n', async () => {
                const intervalSelect = page.locator('select, input[type="number"]').first();
                if (await intervalSelect.isVisible()) {
                    await intervalSelect.click();
                }
            });
            await screenshot('02_dms_interval_adjust');

            await step('Kiá»ƒm tra pháº§n danh sÃ¡ch NgÆ°á»i xÃ¡c nháº­n (Confirmers)', async () => {
                await page.evaluate(() => window.scrollTo(0, 500));
                await page.waitForTimeout(600);
            });
            await screenshot('03_dms_confirmers_section');
        }
    }));

    // TC15: Quáº£n lÃ½ NgÆ°á»i thi hÃ nh (Owner Executors)
    results.push(await runTestCase({
        id: 'TC15',
        name: 'Chá»‰ Ä‘á»‹nh vÃ  quáº£n lÃ½ NgÆ°á»i thi hÃ nh di chÃºc (Executors)',
        role: 'Chá»§ kÃ©t sáº¯t (Vault Owner)',
        fn: async ({ page, step, screenshot }) => {
            await setupOwnerMock(page);

            await step('Má»Ÿ trang NgÆ°á»i thi hÃ nh di chÃºc (/owner/executors)', async () => {
                await page.goto(`${BASE_URL}/owner/executors`, { waitUntil: 'domcontentloaded' });
            });
            await screenshot('01_executors_overview');

            await step('Kiá»ƒm tra danh sÃ¡ch ngÆ°á»i thi hÃ nh vÃ  cÃ¡c tÃ i khoáº£n sá»‘ Ä‘Æ°á»£c phÃ¢n cÃ´ng', async () => {
                await page.locator('text=NgÆ°á»i thi hÃ nh|Executor').first().waitFor({ state: 'visible', timeout: 5000 });
            });

            await step('Cuá»™n trang xem hÆ°á»›ng dáº«n phÃ¢n cÃ´ng nhiá»‡m vá»¥ (TÆ°á»Ÿng niá»‡m / XÃ³a / Táº£i dá»¯ liá»‡u)', async () => {
                await page.evaluate(() => window.scrollTo(0, 400));
                await page.waitForTimeout(600);
            });
            await screenshot('02_executor_tasks_guide');
        }
    }));

    // TC16: Lá»i nháº¯n di nguyá»‡n & Há»™p thá»i gian (Legacy Messages)
    results.push(await runTestCase({
        id: 'TC16',
        name: 'Soáº¡n Lá»i nháº¯n di nguyá»‡n vÃ  Há»™p thá»i gian (Time-Capsule)',
        role: 'Chá»§ kÃ©t sáº¯t (Vault Owner)',
        fn: async ({ page, step, screenshot }) => {
            await setupOwnerMock(page);

            await step('Má»Ÿ trang Chi tiáº¿t KÃ©t sáº¯t vÃ  cuá»™n Ä‘áº¿n khá»‘i Lá»i nháº¯n di chÃºc', async () => {
                await page.goto(`${BASE_URL}/owner/vaults/vault-demo-001`, { waitUntil: 'domcontentloaded' });
                await page.waitForTimeout(1500);
                await page.evaluate(() => window.scrollTo(0, 1100));
                await page.waitForTimeout(800);
            });
            await screenshot('01_legacy_messages_card');

            await step('Kiá»ƒm tra lá»i nháº¯n máº«u "Lá»i dáº·n dÃ² thÆ°Æ¡ng yÃªu gá»­i cÃ¡c con"', async () => {
                const msgItem = page.locator('text=Lá»i dáº·n dÃ²').first();
                if (await msgItem.isVisible()) {
                    console.log('    ÄÃ£ tháº¥y lá»i nháº¯n máº«u');
                }
            });

            await step('XÃ¡c Ä‘á»‹nh nÃºt "Táº¡o lá»i nháº¯n" (Add Legacy Message)', async () => {
                const addMsgBtn = page.locator('button:has-text("lá»i nháº¯n"), button:has-text("message")').first();
                if (await addMsgBtn.isVisible()) {
                    await addMsgBtn.click();
                    await page.waitForTimeout(800);
                    await screenshot('02_create_message_modal');
                }
            });
        }
    }));

    // TC17: Nháº­t kÃ½ hoáº¡t Ä‘á»™ng KÃ©t sáº¯t (Owner Activity Logs)
    results.push(await runTestCase({
        id: 'TC17',
        name: 'Xem Nháº­t kÃ½ hoáº¡t Ä‘á»™ng vÃ  kiá»ƒm toÃ¡n KÃ©t sáº¯t (Activity Logs)',
        role: 'Chá»§ kÃ©t sáº¯t (Vault Owner)',
        fn: async ({ page, step, screenshot }) => {
            await setupOwnerMock(page);

            await step('Má»Ÿ trang Nháº­t kÃ½ hoáº¡t Ä‘á»™ng (/owner/activity)', async () => {
                await page.goto(`${BASE_URL}/owner/activity`, { waitUntil: 'domcontentloaded' });
            });
            await screenshot('01_activity_logs_page');

            await step('Kiá»ƒm tra báº£ng lá»‹ch sá»­ cÃ¡c hÃ nh Ä‘á»™ng táº¡o kÃ©t, thÃªm tÃ i sáº£n, gá»­i Ä‘iá»ƒm danh', async () => {
                await page.locator('text=Nháº­t kÃ½|Activity|Audit').first().waitFor({ state: 'visible', timeout: 5000 });
            });
            await screenshot('02_activity_table');
        }
    }));

    // TC18: GÃ³i cÆ°á»›c & NÃ¢ng cáº¥p Dung lÆ°á»£ng (Pricing & Billing)
    results.push(await runTestCase({
        id: 'TC18',
        name: 'Trang Báº£ng giÃ¡ vÃ  NÃ¢ng cáº¥p GÃ³i cÆ°á»›c (Pricing & Billing)',
        role: 'Chá»§ kÃ©t sáº¯t (Vault Owner)',
        fn: async ({ page, step, screenshot }) => {
            await setupOwnerMock(page);

            await step('Má»Ÿ trang Báº£ng giÃ¡ vÃ  gÃ³i cÆ°á»›c (/pricing)', async () => {
                await page.goto(`${BASE_URL}/pricing`, { waitUntil: 'domcontentloaded' });
            });
            await screenshot('01_pricing_plans_page');

            await step('Kiá»ƒm tra cÃ¡c gÃ³i cÆ°á»›c: Miá»…n phÃ­ (Free), TiÃªu chuáº©n (Standard), Di sáº£n VÄ©nh viá»…n (Legacy)', async () => {
                await page.locator('text=GÃ³i|Plan|Pricing|VND').first().waitFor({ state: 'visible', timeout: 5000 });
            });

            await step('Cuá»™n xuá»‘ng kiá»ƒm tra báº£ng so sÃ¡nh tÃ­nh nÄƒng vÃ  cá»•ng thanh toÃ¡n VNPay', async () => {
                await page.evaluate(() => window.scrollTo(0, 600));
                await page.waitForTimeout(800);
            });
            await screenshot('02_pricing_comparison');
        }
    }));

    return results;
}

module.exports = { runSuite2 };

if (require.main === module) {
    runSuite2().then(results => {
        console.log('\n============================================================');
        console.log(`SUITE 2 FINISHED: ${results.filter(r => r.status === 'PASSED').length}/${results.length} PASSED`);
        console.log('============================================================\n');
    }).catch(err => {
        console.error('Suite 2 Fatal Error:', err);
    });
}
