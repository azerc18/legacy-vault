const { BASE_URL, runTestCase } = require('./test_runner_utils');

const mockUser = {
    id: '11111111-2222-3333-4444-555555555555',
    email: 'tlv48691@gmail.com',
    name: 'Trần Long Vân',
    roles: ['OWNER', 'BENEFICIARY'],
    role: 'OWNER'
};

const mockVault = {
    id: 'vault-demo-001',
    name: 'Két sắt Gia đình - Di sản số 2026',
    description: 'Chứa tài khoản ngân hàng, ví crypto và các tâm thư dặn dò người thân.',
    status: 'ACTIVE',
    modifiable: true,
    totalAssets: 3,
    totalDocuments: 1,
    beneficiaryEmail: 'child@testvault.com',
    createdAt: '2026-11-01T10:00:00Z',
    updatedAt: '2026-11-06T15:00:00Z'
};

async function setupMocks(page) {
    // 1. Set localStorage before any page script runs
    await page.addInitScript(({ user }) => {
        localStorage.setItem('token', 'mock-valid-token');
        localStorage.setItem('lv_user', JSON.stringify(user));
    }, { user: mockUser });

    // 2. Intercept all /api calls
    await page.route('**/api/**', async (route) => {
        const url = route.request().url();
        const method = route.request().method();

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
                body: JSON.stringify({ success: true, data: { ...mockUser, fullName: mockUser.name, status: 'ACTIVE' } })
            });
        }
        if (url.includes('/api/vaults/vault-demo-001/assets')) {
            return route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify({ success: true, data: [] })
            });
        }
        if (url.includes('/api/vaults/vault-demo-001/beneficiaries')) {
            return route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify({ success: true, data: [] })
            });
        }
        if (url.includes('/api/vaults/vault-demo-001/executors')) {
            return route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify({ success: true, data: [] })
            });
        }
        if (url.includes('/api/vaults/vault-demo-001/messages')) {
            return route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify({ success: true, data: [] })
            });
        }
        if (url.includes('/api/vaults/vault-demo-001')) {
            return route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify({ success: true, data: mockVault })
            });
        }
        if (url.includes('/api/vaults')) {
            return route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify({ success: true, data: [mockVault] })
            });
        }
        if (url.includes('/api/estate/readiness')) {
            return route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify({ success: true, data: { score: 85, level: 'GOOD', issues: [] } })
            });
        }
        if (url.includes('/api/owner/protection')) {
            return route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify({ success: true, data: { hasProtection: true } })
            });
        }

        // Default fallback for any other API
        return route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ success: true, data: [] })
        });
    });
}

async function testOwner() {
    await runTestCase({
        id: 'TC10',
        name: 'Owner Dashboard & Quản lý danh sách Két sắt',
        role: 'Chủ két sắt (Vault Owner)',
        fn: async ({ page, step, screenshot }) => {
            await setupMocks(page);

            await step('Đăng nhập và mở màn hình Danh sách Két sắt (/owner/vaults)', async () => {
                await page.goto(`${BASE_URL}/owner/vaults`, { waitUntil: 'domcontentloaded' });
                await page.waitForTimeout(2000);
            });
            await screenshot('01_owner_vaults_list');

            await step('Kiểm tra Thẻ Sẵn sàng Di sản (Estate Readiness Card)', async () => {
                const card = page.locator('text=Két sắt|Vault|Estate|Sẵn sàng').first();
                await card.waitFor({ state: 'visible', timeout: 5000 });
            });

            await step('Kiểm tra hiển thị Két sắt mẫu "Két sắt Gia đình - Di sản số 2026"', async () => {
                const vaultCard = page.locator('text=Két sắt Gia đình').first();
                await vaultCard.waitFor({ state: 'visible', timeout: 5000 });
            });
            await screenshot('02_vault_card_visible');

            await step('Xác định nút "Tạo két mới" (New Vault)', async () => {
                const newVaultBtn = page.locator('a[href="/owner/vaults/new"]').first();
                await newVaultBtn.waitFor({ state: 'visible' });
            });
        }
    });
}

testOwner();
