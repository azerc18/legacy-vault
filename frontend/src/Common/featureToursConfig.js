/**
 * Cấu hình danh mục Hướng dẫn sử dụng nhanh (Onboarding / Feature Spotlight Tour)
 * tự động kích hoạt khi người dùng lần đầu truy cập vào bất kỳ tính năng nào.
 * Áp dụng cho mọi vai trò (Owner, Beneficiary, Executor, Legal Verifier), ngoại trừ Admin.
 */

// CreateVault phát khi tạo két xong, để tour tạo két (SpotlightTour) tự hoàn tất
export const VAULT_CREATED_EVENT = 'lv:vault-created';

export const FEATURE_TOURS = [
    // 1. Owner: Nhật ký hoạt động (/owner/activity)
    {
        id: 'owner_activity',
        role: 'OWNER',
        match: (pathname) => pathname === '/owner/activity',
        badgeKey: 'featureTours.owner_activity.badge',
        steps: [
            {
                selector: '[data-tour="activity-header"]',
                padding: 8,
                titleKey: 'featureTours.owner_activity.step1.title',
                instructionKey: 'featureTours.owner_activity.step1.instruction',
                tipKey: 'featureTours.owner_activity.step1.tip',
            },
            {
                selector: '[data-tour="activity-filter"]',
                padding: 8,
                titleKey: 'featureTours.owner_activity.step2.title',
                instructionKey: 'featureTours.owner_activity.step2.instruction',
                tipKey: 'featureTours.owner_activity.step2.tip',
            },
            {
                selector: '[data-tour="activity-list"]',
                padding: 10,
                titleKey: 'featureTours.owner_activity.step3.title',
                instructionKey: 'featureTours.owner_activity.step3.instruction',
                tipKey: 'featureTours.owner_activity.step3.tip',
            },
        ],
    },

    // 2. Owner: Công tắc tử thần DMS (/owner/dms)
    {
        id: 'owner_dms',
        role: 'OWNER',
        match: (pathname) => pathname === '/owner/dms',
        badgeKey: 'featureTours.owner_dms.badge',
        steps: [
            {
                selector: '[data-tour="dms-status"]',
                padding: 10,
                titleKey: 'featureTours.owner_dms.step1.title',
                instructionKey: 'featureTours.owner_dms.step1.instruction',
                tipKey: 'featureTours.owner_dms.step1.tip',
            },
            {
                selector: '[data-tour="dms-interval"]',
                padding: 10,
                titleKey: 'featureTours.owner_dms.step2.title',
                instructionKey: 'featureTours.owner_dms.step2.instruction',
                tipKey: 'featureTours.owner_dms.step2.tip',
            },
            {
                selector: '[data-tour="dms-confirmers"]',
                padding: 10,
                titleKey: 'featureTours.owner_dms.step3.title',
                instructionKey: 'featureTours.owner_dms.step3.instruction',
                tipKey: 'featureTours.owner_dms.step3.tip',
            },
        ],
    },

    // 3. Owner: Người thi hành di chúc (/owner/executors)
    {
        id: 'owner_executors',
        role: 'OWNER',
        match: (pathname) => pathname === '/owner/executors',
        badgeKey: 'featureTours.owner_executors.badge',
        steps: [
            {
                selector: '[data-tour="executors-overview"]',
                padding: 10,
                titleKey: 'featureTours.owner_executors.step1.title',
                instructionKey: 'featureTours.owner_executors.step1.instruction',
                tipKey: 'featureTours.owner_executors.step1.tip',
            },
            {
                selector: '[data-tour="executors-primary"]',
                padding: 8,
                titleKey: 'featureTours.owner_executors.step2.title',
                instructionKey: 'featureTours.owner_executors.step2.instruction',
                tipKey: 'featureTours.owner_executors.step2.tip',
            },
            {
                selector: '[data-tour="executors-backup"]',
                padding: 8,
                titleKey: 'featureTours.owner_executors.step3.title',
                instructionKey: 'featureTours.owner_executors.step3.instruction',
                tipKey: 'featureTours.owner_executors.step3.tip',
            },
        ],
    },

    // 4. Owner: Chi tiết két sắt (/owner/vaults/:vaultId)
    {
        id: 'owner_vault_detail',
        role: 'OWNER',
        match: (pathname) => pathname.startsWith('/owner/vaults/') && pathname !== '/owner/vaults/new',
        badgeKey: 'featureTours.owner_vault_detail.badge',
        steps: [
            {
                selector: '[data-tour="vault-detail-info"]',
                padding: 10,
                titleKey: 'featureTours.owner_vault_detail.step1.title',
                instructionKey: 'featureTours.owner_vault_detail.step1.instruction',
                tipKey: 'featureTours.owner_vault_detail.step1.tip',
            },
            {
                selector: '[data-tour="vault-detail-assets"]',
                padding: 10,
                titleKey: 'featureTours.owner_vault_detail.step2.title',
                instructionKey: 'featureTours.owner_vault_detail.step2.instruction',
                tipKey: 'featureTours.owner_vault_detail.step2.tip',
            },
            {
                selector: '[data-tour="vault-detail-beneficiaries"]',
                padding: 10,
                titleKey: 'featureTours.owner_vault_detail.step3.title',
                instructionKey: 'featureTours.owner_vault_detail.step3.instruction',
                tipKey: 'featureTours.owner_vault_detail.step3.tip',
            },
        ],
    },

    // 5. Beneficiary: Két sắt thừa kế (/beneficiary)
    {
        id: 'beneficiary_vaults',
        role: 'BENEFICIARY',
        match: (pathname) => pathname === '/beneficiary',
        badgeKey: 'featureTours.beneficiary_vaults.badge',
        steps: [
            {
                selector: '[data-tour="beneficiary-header"]',
                padding: 8,
                titleKey: 'featureTours.beneficiary_vaults.step1.title',
                instructionKey: 'featureTours.beneficiary_vaults.step1.instruction',
                tipKey: 'featureTours.beneficiary_vaults.step1.tip',
            },
            {
                selector: '[data-tour="beneficiary-vault-list"]',
                padding: 10,
                titleKey: 'featureTours.beneficiary_vaults.step2.title',
                instructionKey: 'featureTours.beneficiary_vaults.step2.instruction',
                tipKey: 'featureTours.beneficiary_vaults.step2.tip',
            },
        ],
    },

    // 6. Executor: Bảng hồ sơ thi hành (/executor/home)
    {
        id: 'executor_home',
        role: 'EXECUTOR',
        match: (pathname) => pathname === '/executor/home',
        badgeKey: 'featureTours.executor_home.badge',
        steps: [
            {
                selector: '[data-tour="executor-stats"]',
                padding: 8,
                titleKey: 'featureTours.executor_home.step1.title',
                instructionKey: 'featureTours.executor_home.step1.instruction',
                tipKey: 'featureTours.executor_home.step1.tip',
            },
            {
                selector: '[data-tour="executor-cases-list"]',
                padding: 10,
                titleKey: 'featureTours.executor_home.step2.title',
                instructionKey: 'featureTours.executor_home.step2.instruction',
                tipKey: 'featureTours.executor_home.step2.tip',
            },
        ],
    },

    // 7. Legal Verifier: Bảng thẩm định pháp lý (/verifier)
    {
        id: 'verifier_dashboard',
        role: 'LEGAL_VERIFIER',
        match: (pathname) => pathname === '/verifier',
        badgeKey: 'featureTours.verifier_dashboard.badge',
        steps: [
            {
                selector: '[data-tour="verifier-history-link"]',
                padding: 8,
                titleKey: 'featureTours.verifier_dashboard.step1.title',
                instructionKey: 'featureTours.verifier_dashboard.step1.instruction',
                tipKey: 'featureTours.verifier_dashboard.step1.tip',
            },
            {
                selector: '[data-tour="verifier-requests"]',
                padding: 10,
                titleKey: 'featureTours.verifier_dashboard.step2.title',
                instructionKey: 'featureTours.verifier_dashboard.step2.instruction',
                tipKey: 'featureTours.verifier_dashboard.step2.tip',
            },
        ],
    },
];

export function getFeatureTourForPath(pathname) {
    return FEATURE_TOURS.find((tour) => tour.match(pathname));
}
