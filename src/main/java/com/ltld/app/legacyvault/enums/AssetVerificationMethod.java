package com.ltld.app.legacyvault.enums;

/** C.3: các cách xác thực. Vân/Tài chỉ chọn trong danh sách này; cần thêm cách mới thì thêm vào đây. */
public enum AssetVerificationMethod {
    // EMAIL
    OTP_EMAIL, GOOGLE_LOGIN,
    // E_WALLET
    PAYPAL_LOGIN, SMS_CODE_MOCK,
    // BANK_ACCOUNT
    BANK_NAME_LOOKUP_MOCK, BANK_FORMAT_CHECK, STATEMENT_UPLOAD, MICRO_DEPOSIT_MOCK,
    // EXCHANGE_ACCOUNT
    EXCHANGE_EMAIL_CODE, API_READONLY_MOCK,
    // CRYPTO_WALLET
    ADDRESS_CHECKSUM, BIP39_CHECK, PUBLIC_BALANCE, WALLET_SIGNATURE,
    // SOCIAL_MEDIA
    BIO_CHALLENGE_MOCK, SCREENSHOT_UPLOAD,
    // DOCUMENT
    FILE_HASH, SECOND_PERSON_CONFIRM
}
