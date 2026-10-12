package com.ltld.app.legacyvault.service.fileservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileStorageServiceImplTest {

    @TempDir
    Path uploadDir;

    private FileStorageServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FileStorageServiceImpl();
        ReflectionTestUtils.setField(service, "uploadDir", uploadDir.toString());
    }

    @Test
    void readFile_returnsExactlyWhatWasStored() throws Exception {
        byte[] encrypted = {9, 8, 7, 6};
        String path = service.storeFile(
                new MockMultipartFile("file", "di-chuc.pdf", "application/pdf", new byte[]{1}), encrypted);

        assertThat(service.readFile(path)).isEqualTo(encrypted);
    }

    @Test
    void readFile_pathOutsideUploadDir_isRejected(@TempDir Path elsewhere) throws Exception {
        Path outside = Files.writeString(elsewhere.resolve("secret.txt"), "x");

        assertThatThrownBy(() -> service.readFile(outside.toString()))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void readFile_pathTraversal_isRejected() {
        String sneaky = uploadDir.resolve("..").resolve("etc").resolve("passwd").toString();

        assertThatThrownBy(() -> service.readFile(sneaky)).isInstanceOf(SecurityException.class);
    }

    @Test
    void readFile_missingFile_throwsNotFound() {
        String missing = uploadDir.resolve("khong-co.bin").toString();

        assertThatThrownBy(() -> service.readFile(missing)).isInstanceOf(FileNotFoundException.class);
    }

    @Test
    void readFile_emptyPath_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.readFile("  ")).isInstanceOf(IllegalArgumentException.class);
    }
}