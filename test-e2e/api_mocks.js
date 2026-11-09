// Mock Data and API route handlers for Playwright E2E Tests

const mockData = {
    owner: {
        id: 'usr-owner-001',
        email: 'tlv48691@gmail.com',
        fullName: 'Trần Long Vân',
        roles: ['OWNER'],
        status: 'ACTIVE'
    },
    beneficiary: {
        id: 'usr-bene-002',
        email: 'con_thuhuong@gmail.com',
        fullName: 'Trần Vân Con',
        roles: ['BENEFICIARY'],
        status: 'ACTIVE'
    },
    executor: {
        id: 'usr-exec-003',
        email: 'luatsu_executor@gmail.com',
        fullName: 'Luật sư Lê Văn Thi Hành',
        roles: ['EXECUTOR'],
        status: 'ACTIVE'
    },
    verifier: {
        id: 'usr-verif-004',
        email: 'congchung_verifier@gmail.com',
        fullName: 'Công chứng viên Nguyễn Pháp Lý',
        roles: ['LEGAL_VERIFIER'],
        status: 'ACTIVE'
    },
    admin: {
        id: 'usr-admin-005',
        email: 'admin@legacyvault.codes',
        fullName: 'System Administrator',
        roles: ['ADMIN'],
        status: 'ACTIVE'
    },
    vault: {
        id: 'vault-demo-001',
        vaultId: 'vault-demo-001',
        name: 'Két sắt Gia đình - Di sản số 2026',
        vaultName: 'Két sắt Gia đình - Di sản số 2026',
        description: 'Chứa tài khoản ngân hàng, ví crypto và các tâm thư dặn dò người thân.',
        status: 'ACTIVE',
        vaultStatus: 'ACTIVE',
        claimStatus: 'UNLOCKED',
        myStatus: 'ACCEPTED',
        modifiable: true,
        totalAssets: 3,
        totalDocuments: 2,
        beneficiaryEmail: 'con_thuhuong@gmail.com',
        ownerName: 'Trần Long Vân',
        ownerEmail: 'tlv48691@gmail.com',
        createdAt: '2026-11-01T10:00:00Z',
        updatedAt: '2026-11-06T15:00:00Z',
        unlockedAt: '2026-11-02T10:00:00Z',
        claimDeadlineAt: '2026-12-31T23:59:59Z'
    },
    assets: [
        {
            id: 'asset-01',
            assetId: 'asset-01',
            assetType: 'BANK_ACCOUNT',
            assetName: 'Tài khoản Vietcombank chính',
            secret: 'VCB_PASS_9988#@',
            notes: 'Mật khẩu Internet Banking và mã PIN thẻ',
            fields: { accountNumber: '0123456789', bankName: 'Vietcombank' },
            allocationMode: 'EXCLUSIVE',
            viewed: true
        },
        {
            id: 'asset-02',
            assetId: 'asset-02',
            assetType: 'SOCIAL_MEDIA',
            assetName: 'Tài khoản Facebook cá nhân',
            secret: 'FbSecretKey_2026!',
            notes: 'Chuyển sang chế độ tưởng niệm',
            fields: { platform: 'Facebook', accountHandle: 'tranlongvan.official' },
            allocationMode: 'EXCLUSIVE',
            viewed: false
        },
        {
            id: 'asset-03',
            assetId: 'asset-03',
            assetType: 'CRYPTO_WALLET',
            assetName: 'Ví lạnh Ledger Crypto',
            secret: 'twelve secret seed phrase words restore wallet backup ledger 2026',
            notes: 'Cụm 12 từ khóa khôi phục ví Ethereum và Bitcoin',
            allocationMode: 'JOINT_CONSENSUS',
            consensus: { reached: true, approved: 2, total: 2, threshold: 2, myApproved: true },
            viewed: false
        }
    ],
    documents: [
        {
            id: 'doc-01',
            fileName: 'Di_chuc_hop_phap_2026.pdf',
            documentType: 'WILL',
            uploadedAt: '2026-11-01T11:00:00Z'
        },
        {
            id: 'doc-02',
            fileName: 'Can_cuoc_cong_dan.pdf',
            documentType: 'CCCD',
            uploadedAt: '2026-11-01T11:05:00Z'
        }
    ],
    beneficiaries: [
        {
            id: 'bene-01',
            email: 'con_thuhuong@gmail.com',
            fullName: 'Trần Vân Con',
            relationship: 'Con ruột',
            status: 'ACCEPTED',
            createdAt: '2026-11-01T10:30:00Z'
        }
    ],
    executors: [
        {
            id: 'exec-01',
            email: 'luatsu_executor@gmail.com',
            fullName: 'Luật sư Lê Văn Thi Hành',
            status: 'ASSIGNED',
            note: 'Đã ký thỏa thuận thực hiện di nguyện số'
        }
    ],
    legacyMessages: [
        {
            id: 'msg-01',
            vaultId: 'vault-demo-001',
            title: 'Lời dặn dò thương yêu gửi các con',
            content: 'Bố mẹ luôn yêu thương các con. Hãy luôn sống trung thực, chăm chỉ và yêu thương nhau...',
            deliveryTrigger: 'ON_CLAIM',
            recipientType: 'ALL_BENEFICIARIES',
            status: 'DELIVERED',
            unlocked: true,
            hasAttachment: true,
            attachmentName: 'di_nguyen_tam_thu.pdf',
            deliveredAt: '2026-11-06T10:00:00Z',
            createdAt: '2026-11-02T08:00:00Z'
        }
    ],
    executorTasks: [
        {
            id: 'task-01',
            assetName: 'Tài khoản Facebook cá nhân',
            platform: 'Facebook',
            accountHandle: 'tranlongvan.official',
            wish: 'MEMORIALIZE',
            actionWish: 'MEMORIALIZE',
            status: 'TODO',
            note: 'Chuyển sang tài khoản tưởng niệm cho gia đình'
        },
        {
            id: 'task-02',
            assetName: 'Tài khoản Google Workspace cá nhân',
            platform: 'Google',
            accountHandle: 'tlv48691@gmail.com',
            wish: 'TRANSFER_DATA',
            actionWish: 'TRANSFER_DATA',
            status: 'DOING',
            note: 'Tải bộ dữ liệu Google Takeout bàn giao cho người thụ hưởng'
        }
    ],
    verifierRequests: [
        {
            requestId: 'req-001',
            vaultId: 'vault-demo-001',
            vaultName: 'Két sắt Gia đình - Di sản số 2026',
            ownerName: 'Trần Long Vân',
            requesterName: 'Trần Vân Con',
            status: 'PENDING_APPROVAL',
            submittedAt: '2026-11-05T14:00:00Z',
            documentsCount: 2
        }
    ],
    adminUsers: [
        { id: 'u-1', email: 'tlv48691@gmail.com', fullName: 'Trần Long Vân', roles: ['OWNER'], status: 'ACTIVE', createdAt: '2026-11-01' },
        { id: 'u-2', email: 'con_thuhuong@gmail.com', fullName: 'Trần Vân Con', roles: ['BENEFICIARY'], status: 'ACTIVE', createdAt: '2026-11-02' },
        { id: 'u-3', email: 'luatsu_executor@gmail.com', fullName: 'Luật sư Lê Văn', roles: ['EXECUTOR'], status: 'ACTIVE', createdAt: '2026-11-03' }
    ]
};

async function setupMockForRole(page, roleKey) {
    const user = mockData[roleKey] || mockData.owner;

    // 1. Inject localStorage session
    await page.addInitScript(({ u }) => {
        localStorage.setItem('token', 'mock-jwt-token-active-session');
        localStorage.setItem('lv_user', JSON.stringify({
            id: u.id,
            email: u.email,
            name: u.fullName,
            roles: u.roles,
            role: u.roles[0]
        }));
    }, { u: user });

    // 2. Intercept and mock ALL API calls safely (strictly /api/ endpoints, NOT /src/api/ Vite modules)
    await page.route(url => {
        try {
            const u = new URL(url);
            return u.pathname.startsWith('/api/');
        } catch (_) {
            return false;
        }
    }, async (route) => {
        const url = route.request().url();
        const method = route.request().method();

        const json = (data, status = 200) => route.fulfill({
            status,
            contentType: 'application/json',
            body: JSON.stringify({ success: true, message: 'OK', data })
        });

        // Auth & Users
        if (url.includes('/api/auth/refresh')) return json({ accessToken: 'mock-jwt-token-active-session' });
        if (url.includes('/api/users/me')) return json(user);
        if (url.includes('/api/auth/logout')) return json({ message: 'Logged out' });

        // Asset types schema
        if (url.includes('/api/asset-types')) {
            return json([
                {
                    assetType: 'BANK_ACCOUNT',
                    label: 'Bank account',
                    mechanism: 'LEGAL_CLAIM_DOSSIER',
                    fields: [
                        { key: 'institutionName', label: 'Tên ngân hàng', type: 'TEXT', required: true, sensitive: false, pattern: null, options: [] },
                        { key: 'accountNumber', label: 'Số tài khoản', type: 'TEXT', required: true, sensitive: false, pattern: '^\\d{6,19}$', options: [] },
                        { key: 'branchName', label: 'Chi nhánh', type: 'TEXT', required: true, sensitive: false, pattern: null, options: [] },
                        { key: 'estimatedValue', label: 'Giá trị ước tính', type: 'NUMBER', required: true, sensitive: false, pattern: null, options: [] },
                        { key: 'currency', label: 'Tiền tệ', type: 'SELECT', required: true, sensitive: false, pattern: null, options: ['VND', 'USD', 'EUR'] },
                        { key: 'legalInstruction', label: 'Hướng dẫn giải quyết', type: 'TEXTAREA', required: true, sensitive: false, pattern: null, options: [] },
                        { key: 'pinOrPasswordNote', label: 'Ghi chú mã PIN / mật khẩu', type: 'SECRET', required: false, sensitive: true, pattern: null, options: [] }
                    ]
                },
                {
                    assetType: 'EXCHANGE_ACCOUNT',
                    label: 'Sàn giao dịch',
                    mechanism: 'LEGAL_CLAIM_DOSSIER',
                    fields: [
                        { key: 'institutionName', label: 'Tên sàn giao dịch', type: 'TEXT', required: true, sensitive: false, pattern: null, options: [] },
                        { key: 'accountId', label: 'Mã tài khoản (UID)', type: 'TEXT', required: true, sensitive: false, pattern: null, options: [] },
                        { key: 'estimatedValue', label: 'Giá trị ước tính', type: 'NUMBER', required: true, sensitive: false, pattern: null, options: [] },
                        { key: 'currency', label: 'Tiền tệ', type: 'SELECT', required: true, sensitive: false, pattern: null, options: ['VND', 'USD', 'EUR'] },
                        { key: 'legalInstruction', label: 'Hướng dẫn xác nhận tài sản', type: 'TEXTAREA', required: true, sensitive: false, pattern: null, options: [] },
                        { key: 'pinOrPasswordNote', label: 'Ghi chú đăng nhập bảo mật', type: 'SECRET', required: false, sensitive: true, pattern: null, options: [] }
                    ]
                },
                {
                    assetType: 'DOCUMENT',
                    label: 'Tài liệu pháp lý',
                    mechanism: 'LEGAL_CLAIM_DOSSIER',
                    fields: [
                        { key: 'documentTitle', label: 'Tên tài liệu', type: 'TEXT', required: true, sensitive: false, pattern: null, options: [] },
                        { key: 'issuer', label: 'Cơ quan cấp', type: 'TEXT', required: false, sensitive: false, pattern: null, options: [] },
                        { key: 'location', label: 'Nơi lưu bản gốc', type: 'TEXT', required: true, sensitive: false, pattern: null, options: [] },
                        { key: 'legalInstruction', label: 'Hướng dẫn thừa kế', type: 'TEXTAREA', required: false, sensitive: false, pattern: null, options: [] }
                    ]
                },
                {
                    assetType: 'CRYPTO_WALLET',
                    label: 'Ví tiền điện tử',
                    mechanism: 'CRYPTOGRAPHIC_SPLIT_KEY',
                    fields: [
                        { key: 'walletProvider', label: 'Loại ví (MetaMask, Ledger...)', type: 'TEXT', required: true, sensitive: false, pattern: null, options: [] },
                        { key: 'blockchainNetwork', label: 'Mạng Blockchain', type: 'SELECT', required: true, sensitive: false, pattern: null, options: ['Bitcoin', 'Ethereum', 'Solana', 'BNB Chain', 'Other'] },
                        { key: 'walletAddress', label: 'Địa chỉ ví công khai', type: 'TEXT', required: true, sensitive: false, pattern: '^[A-Za-z0-9]{20,100}$', options: [] },
                        { key: 'seedPhrase', label: 'Cụm từ phục hồi (12 hoặc 24 từ khóa)', type: 'SECRET', required: false, sensitive: true, pattern: '^\\s*(\\S+\\s+){11}\\S+\\s*$|^\\s*(\\S+\\s+){23}\\S+\\s*$', options: [] },
                        { key: 'privateKey', label: 'Khóa riêng tư (Private Key)', type: 'SECRET', required: false, sensitive: true, pattern: null, options: [] },
                        { key: 'passphrase', label: 'Cụm mật khẩu mở rộng (Passphrase)', type: 'SECRET', required: false, sensitive: true, pattern: null, options: [] }
                    ]
                },
                {
                    assetType: 'E_WALLET',
                    label: 'Ví điện tử',
                    mechanism: 'EXECUTOR_DISPOSITION',
                    fields: [
                        { key: 'platform', label: 'Nền tảng ví', type: 'SELECT', required: true, sensitive: false, pattern: null, options: ['MoMo', 'ZaloPay', 'ViettelPay', 'PayPal', 'Other'] },
                        { key: 'usernameOrHandle', label: 'Số điện thoại / Định danh ví', type: 'TEXT', required: true, sensitive: false, pattern: null, options: [] },
                        { key: 'wish', label: 'Di nguyện xử lý', type: 'SELECT', required: true, sensitive: false, pattern: null, options: ['MEMORIALIZE', 'DELETE', 'TRANSFER_DATA'] },
                        { key: 'deviceOrSimLocation', label: 'Nơi cất giữ SIM / Thiết bị nhận OTP', type: 'TEXT', required: false, sensitive: false, pattern: null, options: [] },
                        { key: 'password', label: 'Mật khẩu / Mã PIN ví', type: 'SECRET', required: false, sensitive: true, pattern: null, options: [] },
                        { key: 'twoFactorRecoveryCodes', label: 'Mã khôi phục bảo mật 2FA', type: 'SECRET', required: false, sensitive: true, pattern: null, options: [] }
                    ]
                },
                {
                    assetType: 'SOCIAL_MEDIA',
                    label: 'Mạng xã hội',
                    mechanism: 'EXECUTOR_DISPOSITION',
                    fields: [
                        { key: 'platform', label: 'Nền tảng', type: 'SELECT', required: true, sensitive: false, pattern: null, options: ['Facebook', 'Instagram', 'TikTok', 'X', 'LinkedIn', 'YouTube', 'Other'] },
                        { key: 'usernameOrHandle', label: 'Tên người dùng / Link tài khoản', type: 'TEXT', required: true, sensitive: false, pattern: null, options: [] },
                        { key: 'wish', label: 'Di nguyện xử lý tài khoản', type: 'SELECT', required: true, sensitive: false, pattern: null, options: ['MEMORIALIZE', 'DELETE', 'TRANSFER_DATA'] },
                        { key: 'deviceOrSimLocation', label: 'Nơi cất giữ thiết bị xác thực 2 bước', type: 'TEXT', required: false, sensitive: false, pattern: null, options: [] },
                        { key: 'password', label: 'Mật khẩu tài khoản', type: 'SECRET', required: false, sensitive: true, pattern: null, options: [] },
                        { key: 'twoFactorRecoveryCodes', label: 'Mã bảo mật khôi phục khẩn cấp', type: 'SECRET', required: false, sensitive: true, pattern: null, options: [] }
                    ]
                },
                {
                    assetType: 'EMAIL',
                    label: 'Hộp thư điện tử',
                    mechanism: 'EXECUTOR_DISPOSITION',
                    fields: [
                        { key: 'platform', label: 'Dịch vụ Email', type: 'SELECT', required: true, sensitive: false, pattern: null, options: ['Gmail', 'Outlook', 'Yahoo', 'Apple ID', 'Other'] },
                        { key: 'usernameOrHandle', label: 'Địa chỉ Email', type: 'TEXT', required: true, sensitive: false, pattern: null, options: [] },
                        { key: 'wish', label: 'Di nguyện xử lý hòm thư', type: 'SELECT', required: true, sensitive: false, pattern: null, options: ['MEMORIALIZE', 'DELETE', 'TRANSFER_DATA'] },
                        { key: 'deviceOrSimLocation', label: 'Nơi cất giữ khóa bảo mật FIDO2 / SIM', type: 'TEXT', required: false, sensitive: false, pattern: null, options: [] },
                        { key: 'password', label: 'Mật khẩu ứng dụng / Email', type: 'SECRET', required: false, sensitive: true, pattern: null, options: [] },
                        { key: 'twoFactorRecoveryCodes', label: 'Mã dự phòng Google / MS Authenticator', type: 'SECRET', required: false, sensitive: true, pattern: null, options: [] }
                    ]
                },
                {
                    assetType: 'OTHER',
                    label: 'Tài sản số khác',
                    mechanism: null,
                    fields: [
                        { key: 'title', label: 'Tên định danh tài sản', type: 'TEXT', required: true, sensitive: false, pattern: null, options: [] },
                        { key: 'description', label: 'Mô tả chi tiết', type: 'TEXTAREA', required: false, sensitive: false, pattern: null, options: [] },
                        { key: 'secret', label: 'Dữ liệu bí mật cần bảo vệ', type: 'SECRET', required: false, sensitive: true, pattern: null, options: [] }
                    ]
                }
            ]);
        }

        // Owner APIs
        if (url.includes('/api/estate/readiness')) return json({ score: 92, level: 'EXCELLENT', issues: [] });
        if (url.includes('/api/owner/protection')) return json({ hasProtection: true, issues: [] });
        if (url.includes('/api/dms/config')) {
            return json({
                checkinIntervalDays: 30,
                gracePeriodDays: 7,
                status: 'ACTIVE',
                lastCheckinAt: '2026-11-01T12:00:00Z',
                nextDeadlineAt: '2026-12-01T12:00:00Z',
                confirmers: [{ id: 'conf-1', email: 'confirmer@family.vn', name: 'Nguyễn Văn Xác Nhận' }]
            });
        }
        if (url.includes('/api/vaults/vault-demo-001/assets')) return json(mockData.assets);
        if (url.includes('/api/vaults/vault-demo-001/beneficiaries')) return json(mockData.beneficiaries);
        if (url.includes('/api/vaults/vault-demo-001/executors')) return json(mockData.executors);
        if (url.includes('/api/vaults/vault-demo-001/messages')) return json(mockData.legacyMessages);
        if (url.includes('/api/vaults/vault-demo-001/activity')) return json([{ id: 'act-1', action: 'VAULT_CREATED', createdAt: '2026-11-01' }]);
        if (url.includes('/api/vaults/admin/all')) {
            return json({
                content: [mockData.vault],
                totalPages: 1,
                totalElements: 1,
                number: 0
            });
        }
        if (url.includes('/api/vaults/vault-demo-001')) return json(mockData.vault);
        if (url.includes('/api/vaults')) {
            if (method === 'POST') return json({ ...mockData.vault, id: 'vault-newly-created' }, 201);
            return json([mockData.vault]);
        }

        // Beneficiary APIs (uses /api/beneficiaries/...)
        if (url.includes('/api/beneficiaries/vaults/vault-demo-001/progress')) {
            return json({
                vaultStatus: 'ACTIVE',
                myStatus: 'ACCEPTED',
                totalBeneficiaries: 1,
                acceptedBeneficiaries: 1,
                closedBeneficiaries: 0,
                declinedBeneficiaries: 0,
                totalAssets: 3,
                viewedAssets: 1,
                assets: mockData.assets
            });
        }
        if (url.includes('/api/beneficiaries/vaults/vault-demo-001/assets/asset-01')) return json(mockData.assets[0]);
        if (url.includes('/api/beneficiaries/vaults/vault-demo-001/assets')) return json(mockData.assets);
        if (url.includes('/api/beneficiaries/vaults/vault-demo-001/documents')) return json(mockData.documents);
        if (url.includes('/api/beneficiaries/vaults/vault-demo-001/messages')) return json(mockData.legacyMessages);
        if (url.includes('/api/beneficiaries/vaults/vault-demo-001/tasks') || url.includes('/api/beneficiaries/vaults/vault-demo-001/executor-tasks')) return json(mockData.executorTasks);
        if (url.includes('/api/beneficiaries/vaults/vault-demo-001/dossier')) {
            return route.fulfill({
                status: 200,
                contentType: 'application/pdf',
                headers: { 'content-disposition': 'attachment; filename="ho-so-khai-nhan.pdf"' },
                body: Buffer.from('%PDF-1.4 Mock Dossier PDF')
            });
        }
        if (url.includes('/api/beneficiaries/vaults')) return json([mockData.vault]);

        // Executor APIs (uses /api/executor-assignments/...)
        if (url.includes('/api/executor-assignments/me')) {
            return json([
                {
                    assignmentId: 'assign-001',
                    ownerFullName: 'Trần Long Vân',
                    ownerEmail: 'tlv48691@gmail.com',
                    executorType: 'PRIMARY',
                    status: 'ACTIVE',
                    assignedAt: '2026-11-01T10:00:00Z',
                    activatedAt: '2026-11-02T10:00:00Z',
                    acknowledgedAt: '2026-11-02T12:00:00Z',
                    vaultCount: 1,
                    latestRequestStatus: 'APPROVED',
                    unreadMessageCount: 0
                }
            ]);
        }
        if (url.includes('/api/executor-assignments/assign-001/vaults')) {
            return json({
                ownerFullName: 'Trần Long Vân',
                vaults: [
                    {
                        vaultId: 'vault-demo-001',
                        name: 'Két sắt Gia đình - Di sản số 2026',
                        status: 'ACTIVE',
                        handoverInstructions: 'Thực hiện bàn giao tài khoản mạng xã hội và ví tiền số theo di chúc.',
                        beneficiary: { id: 'bene-01', fullName: 'Trần Vân Con' },
                        beneficiaryPending: false,
                        assets: [
                            { assetId: 'asset-01', assetType: 'BANK_ACCOUNT', assetName: 'Tài khoản Vietcombank chính' },
                            { assetId: 'asset-02', assetType: 'SOCIAL_MEDIA', assetName: 'Tài khoản Facebook cá nhân' }
                        ]
                    }
                ]
            });
        }
        if (url.includes('/api/executor-assignments/assign-001/progress')) {
            return json([
                {
                    vaultId: 'vault-demo-001',
                    vaultName: 'Két sắt Gia đình - Di sản số 2026',
                    handoverStatus: 'UNLOCKED',
                    totalBeneficiaries: 1,
                    claimedBeneficiaries: 1
                }
            ]);
        }
        if (url.includes('/api/executor-assignments/assign-001/tasks')) {
            return json(mockData.executorTasks);
        }
        if (url.includes('/api/executor-assignments/assign-001/claims')) {
            return json([
                {
                    vaultBeneficiaryId: 'vb-01',
                    fullName: 'Trần Vân Con',
                    maskedEmail: 'c***g@gmail.com',
                    relationship: 'Con ruột',
                    status: 'CLAIMED',
                    claimedAt: '2026-11-06T12:00:00Z',
                    canRemind: false
                }
            ]);
        }
        if (url.includes('/api/executor-assignments/assign-001/messages')) {
            return json(mockData.legacyMessages);
        }

        if (url.includes('/ocr-death-certificate') || url.includes('/document/ocr')) {
            return json({
                documentType: 'Trích lục khai tử (Mẫu TP/HT-2024-TLKT)',
                certificateNumber: '248/TLKT-BS',
                bookNumber: '02/2026/TP-HT',
                deceasedFullName: 'TRẦN LONG VÂN',
                dateOfBirth: '18/04/1965',
                gender: 'Nam',
                idNumber: '079065004812',
                dateOfDeath: '08:30 ngày 01/11/2026',
                placeOfDeath: 'Bệnh viện Đại học Y Dược TP. Hồ Chí Minh',
                causeOfDeath: 'Suy tuần hoàn hô hấp cấp',
                issuingAuthority: 'UBND Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh',
                issueDate: '04/11/2026',
                signerName: 'Lê Quốc Tuấn (Phó Chủ tịch UBND)',
                hasOfficialSeal: true,
                ownerNameMatched: true,
                expectedOwnerName: 'Trần Long Vân',
                confidenceScore: 98,
                engineSource: 'SMART_FALLBACK_ENGINE',
                summaryNote: '[AI OCR Xác thực] Trích lục khai tử số 248/TLKT-BS do UBND Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh cấp. Họ tên người mất: TRẦN LONG VÂN (Thời điểm mất: 08:30 ngày 01/11/2026). Đối chiếu định danh: KHỚP 100% với thông tin Chủ két (Trần Long Vân). Đã nhận diện con dấu chứng thực hợp lệ.',
                warnings: []
            });
        }

        // Verifier APIs
        if (url.includes('/api/verifier/requests')) return json(mockData.verifierRequests);

        // Admin APIs
        if (url.includes('/api/admin/users')) {
            return json({
                content: mockData.adminUsers,
                totalPages: 1,
                totalElements: mockData.adminUsers.length,
                number: 0
            });
        }
        if (url.includes('/api/admin/security-alerts/count')) return json({ open: 1 });
        if (url.includes('/api/admin/security-alerts')) {
            return json({
                content: [
                    {
                        id: 'alert-01',
                        alertType: 'SUSPICIOUS_LOGIN',
                        severity: 'MEDIUM',
                        status: 'OPEN',
                        actorEmail: 'hacker@unknown.com',
                        ipAddress: '45.33.32.156',
                        description: 'Phát hiện đăng nhập sai mật khẩu 5 lần liên tiếp từ IP lạ',
                        createdAt: '2026-11-06T08:00:00Z'
                    }
                ],
                totalPages: 1,
                totalElements: 1,
                number: 0
            });
        }
        if (url.includes('/api/admin/audit-logs/actions')) {
            return json(['USER_LOGIN', 'USER_REGISTER', 'VAULT_CREATE', 'VAULT_UNLOCK', 'ASSET_VIEW', 'DMS_CHECKIN']);
        }
        if (url.includes('/api/admin/audit-logs')) {
            return json({
                content: [
                    {
                        id: 'audit-01',
                        action: 'VAULT_CREATE',
                        result: 'SUCCESS',
                        actorId: 'usr-owner-001',
                        actorEmail: 'tlv48691@gmail.com',
                        entityType: 'VAULT',
                        entityId: 'vault-demo-001',
                        vaultId: 'vault-demo-001',
                        ipAddress: '113.190.234.12',
                        detail: 'Tạo két sắt Gia đình',
                        userAgent: 'Mozilla/5.0 Chrome/130.0',
                        createdAt: '2026-11-01T10:00:00Z'
                    },
                    {
                        id: 'audit-02',
                        action: 'ASSET_VIEW',
                        result: 'SUCCESS',
                        actorId: 'usr-bene-002',
                        actorEmail: 'con_thuhuong@gmail.com',
                        entityType: 'ASSET',
                        entityId: 'asset-01',
                        vaultId: 'vault-demo-001',
                        ipAddress: '113.190.234.12',
                        detail: 'Người thụ hưởng xem bí mật Vietcombank',
                        userAgent: 'Mozilla/5.0 Chrome/130.0',
                        createdAt: '2026-11-06T14:30:00Z'
                    }
                ],
                totalPages: 1,
                totalElements: 2,
                number: 0
            });
        }

        // Default response for any other call
        return json([]);
    });
}

module.exports = {
    mockData,
    setupMockForRole
};
