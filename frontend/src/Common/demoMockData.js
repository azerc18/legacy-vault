/**
 * Mock data dùng cho chế độ Demo / Kiểm thử trực tiếp khi Backend offline
 */

export const DEMO_USER = {
    id: '11111111-2222-3333-4444-555555555555',
    email: 'chutaisan@legacyvault.test',
    name: 'Trần Long Vân',
    fullName: 'Trần Long Vân',
    roles: ['OWNER', 'BENEFICIARY', 'EXECUTOR'],
    role: 'OWNER'
};

export const DEMO_TOKEN = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.' + btoa(JSON.stringify({
    sub: DEMO_USER.id,
    roles: DEMO_USER.roles
})) + '.fake_demo_signature';

export const DEMO_VAULTS = [
    {
        id: 'v-demo-1',
        name: 'Két Di Sản Gia Đình & Quỹ Đầu Tư',
        description: 'Két sắt bảo mật chuẩn quân sự AES-256 bảo vệ tài sản số chuyển giao thế hệ sau',
        status: 'ACTIVE',
        createdAt: '2026-01-01T08:00:00',
        updatedAt: '2026-11-01T10:00:00',
        totalAssets: 3,
        totalDocuments: 2,
        modifiable: true,
        beneficiaryEmail: 'nam.nguyen@legacyvault.test',
        beneficiaries: [
            { id: 'ben-1', fullName: 'Nguyễn Hoàng Nam', email: 'nam.nguyen@legacyvault.test', relationship: 'Con trai cả', percentage: 60, status: 'ACTIVE' },
            { id: 'ben-2', fullName: 'Nguyễn Mai Anh', email: 'maianh@legacyvault.test', relationship: 'Con gái út', percentage: 40, status: 'ACTIVE' }
        ]
    },
    {
        id: 'v-demo-2',
        name: 'Két Tài Liệu Pháp Lý & Khóa Bảo Mật',
        description: 'Lưu trữ giấy tờ đất đai, hợp đồng bảo hiểm và khóa ví lạnh',
        status: 'ACTIVE',
        createdAt: '2026-02-15T09:00:00',
        updatedAt: '2026-11-05T14:30:00',
        totalAssets: 2,
        totalDocuments: 4,
        modifiable: true,
        beneficiaryEmail: 'nam.nguyen@legacyvault.test',
        beneficiaries: [
            { id: 'ben-1', fullName: 'Nguyễn Hoàng Nam', email: 'nam.nguyen@legacyvault.test', relationship: 'Con trai cả', percentage: 100, status: 'ACTIVE' }
        ]
    }
];

export const DEMO_ASSETS = [
    { id: 'ast-1', name: 'Tài khoản Chứng khoán SSI', type: 'FINANCIAL', details: 'Mã TK: 001C889988', createdAt: '2026-02-01T08:00:00' },
    { id: 'ast-2', name: 'Ví Crypto Lạnh Ledger Nano X', type: 'CRYPTO', details: 'ETH / BTC Cold Storage', createdAt: '2026-02-15T09:00:00' },
    { id: 'ast-3', name: 'Kênh Youtube & Bản quyền tác giả', type: 'DIGITAL_ACCOUNT', details: 'Kênh 250k subscribers', createdAt: '2026-03-01T10:00:00' }
];

export const DEMO_DOCUMENTS = [
    {
        id: 'doc-1',
        vaultId: 'v-demo-1',
        documentGroupId: 'grp-1',
        documentType: 'WILL',
        fileName: 'Di_chuc_hop_phap_2026.pdf',
        uploadedAt: '2026-03-01T10:00:00',
        version: 1,
        isCurrent: true,
        totalVersions: 1,
        archived: false
    },
    {
        id: 'doc-2',
        vaultId: 'v-demo-1',
        documentGroupId: 'grp-2',
        documentType: 'OWNERSHIP_PAPER',
        fileName: 'Giay_chung_nhan_quyen_so_huu_nha_dat.pdf',
        uploadedAt: '2026-04-12T14:30:00',
        version: 1,
        isCurrent: true,
        totalVersions: 1,
        archived: false
    }
];

export const DEMO_MESSAGES = [
    {
        id: 'msg-1',
        title: 'Lời nhắn gửi các con khi đến ngày nhận di sản',
        content: 'Các con yêu quý, đây là toàn bộ tâm huyết và sự chuẩn bị của bố...',
        createdAt: '2026-05-01T09:00:00'
    }
];

export const DEMO_LOGS = {
    content: [
        { id: 'log-1', createdAt: '2026-11-09T08:30:00Z', action: 'VAULT_ACCESS', actorName: 'You', description: 'Đăng nhập và điểm danh định kỳ thành công' },
        { id: 'log-2', createdAt: '2026-11-08T14:15:00Z', action: 'ASSET_UPDATE', actorName: 'You', description: 'Cập nhật khóa bí mật ví lạnh Crypto' },
        { id: 'log-3', createdAt: '2026-11-05T10:00:00Z', action: 'BENEFICIARY_ASSIGNED', actorName: 'You', description: 'Chỉ định Nguyễn Hoàng Nam làm người thụ hưởng 60%' }
    ],
    items: [],
    totalElements: 3,
    totalPages: 1,
    number: 0,
    size: 20
};

export const DEMO_DMS = {
    configured: true,
    status: 'ACTIVE',
    checkinIntervalDays: 30,
    gracePeriodDays: 7,
    reminderCount: 3,
    remindersSent: 0,
    notificationChannel: 'EMAIL',
    lastCheckinAt: '2026-11-01T08:30:00',
    checkinDueAt: '2026-12-01T08:30:00',
    triggerAt: '2026-12-08T08:30:00',
    nextReminderAt: '2026-11-25T08:30:00',
    hasUsableExecutor: true,
    confirmers: [
        { id: 'c-1', name: 'BS. Trần Văn Hưng', email: 'hung.tran@bvchoray.vn', relationship: 'Bác sĩ gia đình', status: 'CONFIRMED' },
        { id: 'c-2', name: 'LS. Nguyễn Đức Trọng', email: 'trong.lawyer@vanphongluat.vn', relationship: 'Luật sư riêng', status: 'CONFIRMED' }
    ]
};

export const DEMO_EXECUTORS = [
    {
        executorType: 'PRIMARY',
        name: 'Lê Văn Cường',
        fullName: 'Lê Văn Cường',
        email: 'cuong.le@thucthi.test',
        phone: '0912345678',
        status: 'ACTIVE',
        assignedAt: '2026-01-10T08:00:00'
    },
    {
        executorType: 'BACKUP',
        name: 'LS. Nguyễn Đức Trọng',
        fullName: 'LS. Nguyễn Đức Trọng',
        email: 'trong.lawyer@vanphongluat.vn',
        phone: '0987654321',
        status: 'ACTIVE',
        assignedAt: '2026-02-15T09:00:00'
    }
];
DEMO_EXECUTORS.primaryExecutor = DEMO_EXECUTORS[0];
DEMO_EXECUTORS.backupExecutor = DEMO_EXECUTORS[1];

export const DEMO_READINESS = {
    score: 85,
    maxScore: 100,
    grade: 'GOOD',
    completedItems: 5,
    totalItems: 7,
    assetsNeedReverification: 0,
    assetsExpiringSoon: 0,
    items: [
        { key: 'masterVaultAndAsset', titleKey: 'items.masterVaultAndAsset', title: 'Tạo két di sản chính và thêm ít nhất 1 tài sản số', done: true, points: 20, fixUrl: '/owner/vaults' },
        { key: 'assetBeneficiaryAllocated', titleKey: 'items.assetBeneficiaryAllocated', title: 'Chọn người nhận cụ thể cho từng tài sản số', done: true, points: 20, fixUrl: '/owner/vaults' },
        { key: 'legalWillDocument', titleKey: 'items.legalWillDocument', title: 'Tải lên Di chúc cho các tài sản ngân hàng và tài sản cần hồ sơ pháp lý', done: false, points: 15, fixUrl: '/owner/vaults' },
        { key: 'executorsAssigned', titleKey: 'items.executorsAssigned', title: 'Chỉ định cả người thi hành chính và người thi hành dự phòng', done: true, points: 15, fixUrl: '/owner/executors' },
        { key: 'dmsActive', titleKey: 'items.dmsActive', title: 'Bật Dead Man\'s Switch', done: true, points: 15, fixUrl: '/owner/dms' },
        { key: 'safetyNetConfigured', titleKey: 'items.safetyNetConfigured', title: 'Chỉ định người thụ hưởng dự phòng hoặc người xác nhận chéo DMS', done: false, points: 10, fixUrl: '/owner/dms' },
        { key: 'recentReview', titleKey: 'items.recentReview', title: 'Rà soát hoặc check-in hồ sơ di sản trong 6 tháng gần nhất', done: true, points: 5, fixUrl: '/owner/dms' }
    ]
};

export const DEMO_BENEFICIARY_VAULTS = [
    {
        vaultId: 'v-demo-1',
        vaultName: 'Két Di Sản Gia Đình & Quỹ Đầu Tư',
        ownerName: 'Trần Long Vân',
        status: 'CLAIMABLE',
        claimedAssets: 0,
        totalAssets: 2
    }
];

export const DEMO_EXECUTOR_CASES = [
    {
        assignmentId: 'asg-01',
        ownerName: 'Trần Long Vân',
        dmsTriggeredAt: '2026-11-07T09:00:00',
        status: 'ACTIVE',
        executorType: 'PRIMARY',
        assignedAt: '2026-01-10T08:00:00',
        vaultCount: 2
    }
];

export const DEMO_VERIFIER_REQUESTS = [
    {
        requestId: 'req-01',
        ownerName: 'Trần Long Vân',
        documentType: 'TRICH_LUC_KHAI_TU',
        aiConfidence: 98,
        status: 'PENDING_REVIEW',
        submittedAt: '2026-11-08T10:00:00'
    }
];
