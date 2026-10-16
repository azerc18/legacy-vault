// Lớp truy cập dữ liệu cho module Executor (góc nhìn của user có role EXECUTOR).
// Các trang CHỈ gọi hàm trong file này, không tự gọi axios/fetch.
// BE hiện có: POST /api/executor-assignments/{id}/acknowledge (FR-07).
// BE CHƯA có: API lấy danh sách hồ sơ của executor, API nộp giấy chứng tử -> đang mock.

import http from './http.js';

const USE_MOCK = true;

const delay = (ms = 400) => new Promise((r) => setTimeout(r, ms));

// ---------- DỮ LIỆU GIẢ ----------
// Mỗi phần tử = 1 ExecutorAssignment mà user đang đăng nhập là executorUser.
// status: INACTIVE (owner còn sống, chờ) | ACTIVE (DMS đã kích hoạt) | REVOKED
// verificationStatus: NOT_SUBMITTED | PENDING | APPROVED | SIGNED | REJECTED (hồ sơ pháp lý gửi Legal Verifier)
// vault.status: LOCKED | UNLOCKED
let mockCases = [
    {
        id: 'a1',
        ownerName: 'John Doe',
        ownerEmail: 'john@example.com',
        executorType: 'PRIMARY',
        status: 'ACTIVE',
        activatedAt: '2026-10-12T09:30:00',
        acknowledgedAt: null,
        verificationStatus: 'NOT_SUBMITTED',
        vaults: [
            { id: 'v1', name: 'Family Trust & Wills', status: 'LOCKED', assetCount: 4 },
            { id: 'v2', name: 'Crypto & Digital Assets', status: 'LOCKED', assetCount: 7 },
        ],
    },
    {
        id: 'a3',
        ownerName: 'Tran Van B',
        ownerEmail: 'tvb@example.com',
        executorType: 'PRIMARY',
        status: 'ACTIVE',
        activatedAt: '2026-09-20T08:00:00',
        acknowledgedAt: '2026-09-21T10:15:00',
        verificationStatus: 'SIGNED',
        vaults: [{ id: 'v3', name: 'Personal Directives', status: 'UNLOCKED', assetCount: 3 }],
    },
    {
        id: 'a2',
        ownerName: 'Alice Smith',
        ownerEmail: 'alice@example.com',
        executorType: 'BACKUP',
        status: 'INACTIVE',
        activatedAt: null,
        acknowledgedAt: null,
        verificationStatus: 'NOT_SUBMITTED',
        vaults: [],
    },
];

// ---------- HÀM CÔNG KHAI ----------
export async function getMyCases() {
    if (USE_MOCK) { await delay(); return mockCases.map((c) => ({ ...c, vaults: [...c.vaults] })); }
    return (await http.get('/executor-assignments/me')).data.data; // TODO: BE chưa có endpoint này
}

// FR-07: executor xác nhận đã nhận thông báo kích hoạt
export async function acknowledgeAssignment(id) {
    if (USE_MOCK) {
        await delay();
        const c = mockCases.find((x) => x.id === id);
        if (!c) throw new Error('Assignment not found');
        if (c.status !== 'ACTIVE') throw new Error('Only active assignments can be acknowledged');
        c.acknowledgedAt = new Date().toISOString();
        return { id: c.id, status: c.status, acknowledgedAt: c.acknowledgedAt };
    }
    return (await http.post(`/executor-assignments/${id}/acknowledge`)).data.data;
}
