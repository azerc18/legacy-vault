// Chuông thông báo: thông báo trong app của người đang đăng nhập (mọi vai trò dùng chung /api/notifications)
import http from './http.js';

const unwrap = (res) => res.data?.data;

export const notificationApi = {
    // Mới nhất trước; trả { items, last } để chuông biết còn trang sau hay không
    list: ({ page = 0, size = 20, unreadOnly = false } = {}) =>
        http.get('/notifications/me', { params: { page, size, unreadOnly } }).then((r) => {
            const data = unwrap(r);
            // Spring Page có thể để thông tin trang ở gốc hoặc trong "page" (tùy cấu hình serialize)
            const totalPages = data?.totalPages ?? data?.page?.totalPages;
            const last = data?.last ?? (typeof totalPages === 'number' ? page + 1 >= totalPages : true);
            return { items: data?.content ?? [], last };
        }),
    unreadCount: () => http.get('/notifications/me/unread-count').then((r) => Number(unwrap(r)) || 0),
    markRead: (id) => http.patch(`/notifications/${id}/read`),
    markAllRead: () => http.patch('/notifications/me/read-all'),
};
