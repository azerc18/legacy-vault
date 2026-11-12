package com.ltld.app.legacyvault.dto.logindto;

// FE đọc lúc chạy để biết có vẽ widget Turnstile không (đổi key trên Azure không cần build lại FE)
public record CaptchaConfigResponse(boolean enabled, String siteKey, int loginAfterFailures) {}
