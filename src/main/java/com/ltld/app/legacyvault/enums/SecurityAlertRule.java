package com.ltld.app.legacyvault.enums;

/** FR-20: luật phát hiện truy cập bất thường, mỗi luật có mức độ cố định. */
public enum SecurityAlertRule {
    ACCOUNT_LOCKED(SecurityAlertSeverity.MEDIUM),          // sai mật khẩu nhiều lần, tài khoản bị khoá tạm
    LOGIN_FAILURES_FROM_IP(SecurityAlertSeverity.HIGH),    // nhiều lần đăng nhập sai từ 1 IP (dò mật khẩu)
    REFRESH_TOKEN_REUSE(SecurityAlertSeverity.HIGH),       // refresh token đã xoay vòng bị dùng lại (bị đánh cắp)
    MASS_DOWNLOAD(SecurityAlertSeverity.HIGH),             // 1 user tải hàng loạt tài sản / tài liệu
    ADMIN_ROLE_GRANTED(SecurityAlertSeverity.MEDIUM);      // có người vừa được cấp quyền ADMIN

    private final SecurityAlertSeverity severity;

    SecurityAlertRule(SecurityAlertSeverity severity) {
        this.severity = severity;
    }

    public SecurityAlertSeverity getSeverity() {
        return severity;
    }
}