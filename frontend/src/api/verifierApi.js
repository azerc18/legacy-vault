// Lớp truy cập dữ liệu cho module Legal Verifier (Người 5).
// Các trang CHỈ gọi hàm trong file này, không tự gọi axios/fetch.
// Khi BE cả nhóm xong: đặt USE_MOCK = false rồi kiểm tra lại đường dẫn so với LegalVerificationController.

import axios from 'axios';

const USE_MOCK = true;

const http = axios.create({ baseURL: '/api/verifier' });
http.interceptors.request.use((config) => {
    const token = localStorage.getItem('token'); // TODO: khớp với cách Người 1 lưu JWT
    if (token) config.headers.Authorization = `Bearer ${token}`;
    return config;
});

const delay = (ms = 400) => new Promise((r) => setTimeout(r, ms));

// ---------- DỮ LIỆU GIẢ ----------
let mockRequests = [
    {
        id: 1,
        ownerName: 'John Doe',
        executorName: 'Robert',
        executorEmail: 'robert@example.com',
        relationship: 'Son',
        submittedDate: '2026-10-01',
        status: 'PENDING', // PENDING | APPROVED | SIGNED | REJECTED
        deathCertificate: 'death_cert_john_doe.pdf',
        vaults: ['Family Trust & Wills', 'Crypto & Digital Assets'],
        rejectReason: null,
    },
    {
        id: 2,
        ownerName: 'Alice Smith',
        executorName: 'David Johnson',
        executorEmail: 'david@example.com',
        relationship: 'Brother',
        submittedDate: '2026-09-28',
        status: 'PENDING',
        deathCertificate: 'certificate_alice_smith.pdf',
        vaults: ['Personal Directives & Insurance'],
        rejectReason: null,
    },
];
let mockHistory = [
    { id: 101, requestId: 0, action: 'REJECTED', owner: 'Nguyen Van A', at: '2026-09-20', note: 'Death certificate is blurry' },
];

// ---------- HÀM CÔNG KHAI ----------
export async function getRequests() {
    if (USE_MOCK) { await delay(); return mockRequests.map((r) => ({ ...r })); }
    return (await http.get('/requests')).data;
}

export async function getRequestById(id) {
    if (USE_MOCK) {
        await delay();
        const r = mockRequests.find((x) => x.id === Number(id));
        if (!r) throw new Error('Request not found');
        return { ...r };
    }
    return (await http.get(`/requests/${id}`)).data;
}

// Bước 1 của luồng: duyệt (UC12)
export async function approveRequest(id) {
    if (USE_MOCK) {
        await delay();
        const r = mockRequests.find((x) => x.id === Number(id));
        if (!r) throw new Error('Request not found');
        if (r.status !== 'PENDING') throw new Error('This request has already been processed');
        r.status = 'APPROVED';
        return { ...r };
    }
    return (await http.post(`/requests/${id}/approve`)).data; // TODO: kiểm tra path với controller
}

// Bước 2 của luồng: ký số (UC14) -> Vault chuyển UNLOCKED
export async function signRequest(id, pin) {
    if (USE_MOCK) {
        await delay(800);
        const r = mockRequests.find((x) => x.id === Number(id));
        if (!r) throw new Error('Request not found');
        if (r.status !== 'APPROVED') throw new Error('The request must be approved before signing');
        if (!/^\d{6}$/.test(pin)) throw new Error('PIN must be exactly 6 digits');
        r.status = 'SIGNED';
        mockHistory.unshift({ id: Date.now(), requestId: r.id, action: 'SIGNED', owner: r.ownerName, at: new Date().toISOString().slice(0, 10), note: '' });
        return { ...r };
    }
    return (await http.post(`/requests/${id}/sign`, { pin })).data; // TODO: kiểm tra path/body với controller
}

export async function rejectRequest(id, reason) {
    if (USE_MOCK) {
        await delay();
        const r = mockRequests.find((x) => x.id === Number(id));
        if (!r) throw new Error('Request not found');
        if (r.status !== 'PENDING') throw new Error('This request has already been processed');
        r.status = 'REJECTED';
        r.rejectReason = reason;
        mockHistory.unshift({ id: Date.now(), requestId: r.id, action: 'REJECTED', owner: r.ownerName, at: new Date().toISOString().slice(0, 10), note: reason });
        return { ...r };
    }
    return (await http.post(`/requests/${id}/reject`, { reason })).data;
}

// UC15: lịch sử, có lọc theo action
export async function getHistory({ action } = {}) {
    if (USE_MOCK) {
        await delay();
        return mockHistory.filter((h) => !action || h.action === action);
    }
    return (await http.get('/history', { params: { action } })).data; // TODO: kiểm tra path/param
}

// UC13: xác thực tài liệu (mock)
export async function verifyDocument(id) {
    if (USE_MOCK) { await delay(1000); return { valid: true, checkedAt: new Date().toISOString() }; }
    return (await http.post(`/requests/${id}/verify-document`)).data; // TODO: kiểm tra path
}