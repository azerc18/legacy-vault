package com.ltld.app.legacyvault.service.assetverificationservice;

import com.ltld.app.legacyvault.entity.LegalDocument;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.repository.LegalDocumentRepository;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("VaultFileHasher – băm SHA-256 tệp trong vault")
class VaultFileHasherTest {

    @Mock private LegalDocumentRepository legalDocumentRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private CryptoService cryptoService;
    @InjectMocks private VaultFileHasher hasher;

    private LegalDocument document(UUID docId, UUID vaultId) {
        return LegalDocument.builder().id(docId).vault(Vault.builder().id(vaultId).build())
                .fileName("a.pdf").fileUrlEncrypted("enc://a.pdf").build();
    }

    @Test
    @DisplayName("tài liệu không thuộc vault thì trả rỗng")
    void notInVault_empty() {
        UUID vaultId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        when(legalDocumentRepository.findByIdAndVaultId(docId, vaultId)).thenReturn(Optional.empty());

        assertThat(hasher.hash(vaultId, docId)).isEmpty();
    }

    @Test
    @DisplayName("tài liệu hợp lệ thì trả SHA-256 của nội dung đã giải mã")
    void hashesDecryptedContent() throws Exception {
        UUID vaultId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        when(legalDocumentRepository.findByIdAndVaultId(docId, vaultId))
                .thenReturn(Optional.of(document(docId, vaultId)));
        byte[] cipher = {1, 2};
        byte[] plain = "statement".getBytes(StandardCharsets.UTF_8);
        when(fileStorageService.readFile("enc://a.pdf")).thenReturn(cipher);
        when(cryptoService.decryptBytes(cipher)).thenReturn(plain);

        Optional<VaultFileHasher.HashedFile> result = hasher.hash(vaultId, docId);

        assertThat(result).isPresent();
        assertThat(result.get().documentId()).isEqualTo(docId);
        assertThat(result.get().sha256())
                .isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(plain)));
    }

    @Test
    @DisplayName("không đọc được tệp thì ném IllegalStateException")
    void readFailure_throws() throws Exception {
        UUID vaultId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        when(legalDocumentRepository.findByIdAndVaultId(docId, vaultId))
                .thenReturn(Optional.of(document(docId, vaultId)));
        when(fileStorageService.readFile("enc://a.pdf")).thenThrow(new RuntimeException("io"));

        assertThatThrownBy(() -> hasher.hash(vaultId, docId)).isInstanceOf(IllegalStateException.class);
    }
}
