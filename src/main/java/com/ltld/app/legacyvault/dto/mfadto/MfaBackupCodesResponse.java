package com.ltld.app.legacyvault.dto.mfadto;

import java.util.List;

// Chỉ trả 1 lần khi bật 2FA; BE không lưu bản gốc
public record MfaBackupCodesResponse(List<String> backupCodes) {}