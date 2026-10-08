package com.ltld.app.legacyvault.service.documentservice;

import com.ltld.app.legacyvault.entity.LegalDocument;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.DocumentType;
import com.ltld.app.legacyvault.repository.LegalDocumentRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceImplTest {

    @Mock private LegalDocumentRepository legalDocumentRepository;
    @Mock private VaultRepository vaultRepository;
    @Mock private CryptoService cryptoService;
    @Mock private FileStorageService fileStorageService;

    @InjectMocks private DocumentServiceImpl documentService;

    private User mockOwner;
    private Vault mockVault;
    private UUID ownerId;
    private UUID vaultId;
    private MockMultipartFile mockFile; // Dùng cái này để giả lập file do user ném lên

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        vaultId = UUID.randomUUID();

        mockOwner = new User();
        mockOwner.setId(ownerId);

        mockVault = new Vault();
        mockVault.setId(vaultId);
        mockVault.setOwner(mockOwner);

        // Giả lập một file tên là dichuc.pdf
        mockFile = new MockMultipartFile(
                "file", "dichuc.pdf", "application/pdf", "Noi dung text".getBytes()
        );
    }

    @Test
    void uploadDocument_Success() throws Exception {
        byte[] encryptedBytes = "EncryptedBytes".getBytes();
        String savedPath = "uploads/abcd_dichuc.pdf";

        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(mockVault));
        when(cryptoService.encryptBytes(any(byte[].class))).thenReturn(encryptedBytes);
        when(fileStorageService.storeFile(any(), any(byte[].class))).thenReturn(savedPath);

        // Mẹo: Trả về chính cái object bị nhét vào hàm save
        when(legalDocumentRepository.save(any(LegalDocument.class))).thenAnswer(i -> i.getArgument(0));

        LegalDocument result = documentService.uploadDocument(ownerId, vaultId, DocumentType.WILL, mockFile);

        assertThat(result).isNotNull();
        assertThat(result.getFileName()).isEqualTo("dichuc.pdf");
        assertThat(result.getFileUrlEncrypted()).isEqualTo(savedPath);
        assertThat(result.getDocumentType()).isEqualTo(DocumentType.WILL);

        // Đảm bảo file được đem đi mã hóa đúng 1 lần
        verify(cryptoService, times(1)).encryptBytes(any(byte[].class));
    }

    @Test
    void uploadDocument_NotOwner_ThrowsException() throws Exception {
        User fakeOwner = new User();
        fakeOwner.setId(UUID.randomUUID());
        mockVault.setOwner(fakeOwner); // Cố tình đổi chủ

        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(mockVault));

        // Phải văng lỗi
        assertThatThrownBy(() -> documentService.uploadDocument(ownerId, vaultId, DocumentType.WILL, mockFile))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Unauthorized");

        // Và đương nhiên file không bao giờ được phép đem đi mã hóa hay lưu
        verify(cryptoService, never()).encryptBytes(any());
    }
}
