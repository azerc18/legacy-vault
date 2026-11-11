import React from 'react'
import ReactDOM from 'react-dom/client'
import './i18n/index.js'
import App from './App.jsx'
import { ToastProvider } from './Common/Toast.jsx'
import './index.css'
import './design-system/tokens.css'
import './design-system/themes.css'
import './design-system/materials.css'
import './design-system/page-hero.css'
import './design-system/motion.css'

// Trang tách code (React.lazy): sau mỗi lần deploy, file chunk đổi tên. Tab mở từ trước chuyển trang sẽ tải chunk cũ đã bị xoá
// -> import() lỗi -> trắng trang. Vite báo bằng sự kiện vite:preloadError: tải lại trang một lần để lấy index.html mới.
// Ghi mốc thời gian: nếu vừa tải lại < 10 giây mà vẫn lỗi thì để lỗi hiện ra, không tải lại vòng lặp.
const CHUNK_RELOAD_KEY = 'lv_chunk_reload_at'
window.addEventListener('vite:preloadError', (event) => {
    let last = 0
    try { last = Number(sessionStorage.getItem(CHUNK_RELOAD_KEY)) || 0 } catch { /* sessionStorage bị chặn */ }
    if (Date.now() - last < 10000) return
    try { sessionStorage.setItem(CHUNK_RELOAD_KEY, String(Date.now())) } catch { /* bỏ qua */ }
    event.preventDefault()
    window.location.reload()
})

ReactDOM.createRoot(document.getElementById('root')).render(
    <React.StrictMode>
        <ToastProvider>
            <App />
        </ToastProvider>
    </React.StrictMode>,
)
