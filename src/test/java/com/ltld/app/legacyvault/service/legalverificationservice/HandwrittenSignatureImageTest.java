package com.ltld.app.legacyvault.service.legalverificationservice;

import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HandwrittenSignatureImageTest {

    @Test
    void validDataUrl_returnsPngBytes() {
        byte[] png = SignaturePngFixture.signed(600, 200);

        assertThat(HandwrittenSignatureImage.decodeAndValidate(SignaturePngFixture.dataUrl(png))).isEqualTo(png);
    }

    @Test
    void plainBase64WithoutPrefix_isAccepted() {
        byte[] png = SignaturePngFixture.signed(600, 200);

        assertThat(HandwrittenSignatureImage.decodeAndValidate(Base64.getEncoder().encodeToString(png))).isEqualTo(png);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void missing_rejected(String input) {
        assertInvalid(input, "draw your signature");
    }

    @Test
    void blankCanvas_rejected() {
        assertInvalid(SignaturePngFixture.dataUrl(SignaturePngFixture.blank(600, 200)), "empty");
    }

    @Test
    void notBase64_rejected() {
        assertInvalid("data:image/png;base64,@@@not-base64@@@", "Base64");
    }

    @Test
    void jpegBytesWithPngPrefix_rejected() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0};
        assertInvalid(SignaturePngFixture.dataUrl(jpeg), "PNG");
    }

    @Test
    void pngHeaderButCorruptBody_rejected() {
        byte[] broken = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1, 2, 3, 4};
        assertInvalid(SignaturePngFixture.dataUrl(broken), "could not be read");
    }

    @Test
    void tooSmallImage_rejected() {
        assertInvalid(SignaturePngFixture.dataUrl(SignaturePngFixture.signed(150, 60)), "size");
    }

    @Test
    void tooManyBytes_rejected() {
        byte[] big = new byte[HandwrittenSignatureImage.MAX_BYTES + 1];
        System.arraycopy(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'}, 0, big, 0, 8);
        assertInvalid(SignaturePngFixture.dataUrl(big), "too large");
    }

    private static void assertInvalid(String input, String messagePart) {
        assertThatThrownBy(() -> HandwrittenSignatureImage.decodeAndValidate(input))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(messagePart)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_SIGNATURE_IMAGE));
    }
}
