package com.ltld.app.legacyvault.dto.logindto;

// FE đọc lúc chạy để biết có hiện nút "Đăng nhập bằng Google" không (không cần build lại FE khi đổi Client ID)
public record GoogleConfigResponse(boolean enabled, String clientId) {}
