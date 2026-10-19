package com.ltld.app.legacyvault.service.fileservice;

import com.ltld.app.legacyvault.exception.InvalidVerificationDocumentException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UploadedDocumentValidatorTest {

    private static final byte[] PDF = "%PDF-1.4 content".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2};
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2};

    private final UploadedDocumentValidator validator = new UploadedDocumentValidator();

    private static MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, "application/octet-stream", content);
    }

    @Test
    @DisplayName("PDF, PNG and JPG whose extension matches the content are accepted and returned unchanged")
    void validFiles_areAccepted() {
        assertArrayEquals(PDF, validator.readAndValidate(file("a.pdf", PDF)));
        assertArrayEquals(PNG, validator.readAndValidate(file("a.png", PNG)));
        assertArrayEquals(JPG, validator.readAndValidate(file("a.jpg", JPG)));
        assertArrayEquals(JPG, validator.readAndValidate(file("a.JPEG", JPG)));
    }

    @Test
    @DisplayName("Missing or empty file is rejected")
    void missingOrEmptyFile_isRejected() {
        assertThrows(InvalidVerificationDocumentException.class, () -> validator.readAndValidate(null));
        assertThrows(InvalidVerificationDocumentException.class,
                () -> validator.readAndValidate(file("a.pdf", new byte[0])));
    }

    @Test
    @DisplayName("File larger than 10MB is rejected")
    void tooLargeFile_isRejected() {
        byte[] big = new byte[(int) (UploadedDocumentValidator.MAX_FILE_SIZE + 1)];
        System.arraycopy(PDF, 0, big, 0, PDF.length);

        assertThrows(InvalidVerificationDocumentException.class, () -> validator.readAndValidate(file("big.pdf", big)));
    }

    @Test
    @DisplayName("Content that is not PDF, PNG or JPG is rejected whatever the extension says")
    void unsupportedContent_isRejected() {
        assertThrows(InvalidVerificationDocumentException.class,
                () -> validator.readAndValidate(file("a.pdf", new byte[]{1, 2, 3, 4, 5, 6, 7, 8})));
        assertThrows(InvalidVerificationDocumentException.class,
                () -> validator.readAndValidate(file("a.pdf", "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    @DisplayName("Extension that disagrees with the detected content is rejected")
    void extensionMismatch_isRejected() {
        assertThrows(InvalidVerificationDocumentException.class, () -> validator.readAndValidate(file("fake.pdf", PNG)));
        assertThrows(InvalidVerificationDocumentException.class, () -> validator.readAndValidate(file("fake.png", PDF)));
        assertThrows(InvalidVerificationDocumentException.class, () -> validator.readAndValidate(file(null, PDF)));
    }

    @Test
    @DisplayName("detectContentType maps PDF, PNG and JPG by magic bytes and anything else to octet-stream")
    void detectContentType_usesSameTableAsValidation() {
        assertEquals("application/pdf", UploadedDocumentValidator.detectContentType(PDF));
        assertEquals("image/png", UploadedDocumentValidator.detectContentType(PNG));
        assertEquals("image/jpeg", UploadedDocumentValidator.detectContentType(JPG));
        assertEquals("application/octet-stream", UploadedDocumentValidator.detectContentType(new byte[0]));
        assertEquals("application/octet-stream",
                UploadedDocumentValidator.detectContentType("<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8)));
        // PNG chỉ có 4 byte đầu thì chưa đủ chữ ký 8 byte -> không nhận
        assertEquals("application/octet-stream",
                UploadedDocumentValidator.detectContentType(new byte[]{(byte) 0x89, 'P', 'N', 'G'}));
    }
}
