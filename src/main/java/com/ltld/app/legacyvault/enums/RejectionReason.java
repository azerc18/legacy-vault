package com.ltld.app.legacyvault.enums;

/** FR-12: lý do từ chối hồ sơ xác minh (Verifier chọn trong dropdown). */
public enum RejectionReason {
    DOCUMENT_UNREADABLE("The document is blurry or unreadable"),
    DOCUMENT_INVALID("The document is not a valid death certificate"),
    INFO_MISMATCH("The information does not match the account holder"),
    SUSPECTED_FORGERY("The document is suspected to be forged"),
    DOCUMENT_INCOMPLETE("The document is missing pages or a certification stamp"),
    OTHER("Other reason");

    private final String label;

    RejectionReason(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
