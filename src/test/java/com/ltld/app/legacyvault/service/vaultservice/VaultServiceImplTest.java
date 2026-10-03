package com.ltld.app.legacyvault.service.vaultservice;

import com.ltld.app.legacyvault.dto.vaultdto.CreateVaultRequest;
import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.repository.DigitalAssetRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VaultServiceImplTest {

    // 1. Mock (Làm giả) các phụ thuộc (Database, Crypto) để không đụng tới DB thật khi test
    @Mock
    private VaultRepository vaultRepository;
    @Mock
    private DigitalAssetRepository digitalAssetRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CryptoService cryptoService;

    // 2. Bơm các class giả ở trên vào Class thật mà ta đang muốn test
    @InjectMocks
    private VaultServiceImpl vaultService;

    private User mockOwner;
    private UUID ownerId;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        mockOwner = new User();
        mockOwner.setId(ownerId);
    }

    // Kịch bản 1: Tạo Vault và tài sản số thành công (Mô phỏng FR-01)
    @Test
    void createVault_Success() throws Exception {
        // Chuẩn bị dữ liệu gửi lên (Arrange)
        CreateVaultRequest request = new CreateVaultRequest();
        request.setName("My Secret Vault");

        CreateVaultRequest.DigitalAssetDto assetDto = new CreateVaultRequest.DigitalAssetDto();
        assetDto.setAssetType(AssetType.BANK_ACCOUNT);
        assetDto.setSecret("my-password");
        request.setAssets(List.of(assetDto));

        Vault savedVault = new Vault();
        savedVault.setId(UUID.randomUUID());
        savedVault.setName("My Secret Vault");
        savedVault.setStatus(VaultStatus.ACTIVE);
        savedVault.setOwner(mockOwner);

        // Kịch bản giả lập (Khi code gọi hàm A thì trả về B)
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(mockOwner));
        when(vaultRepository.save(any(Vault.class))).thenReturn(savedVault);
        when(cryptoService.encrypt("my-password")).thenReturn("encrypted-password"); // Trọng tâm KT

        // Gọi hàm thực tế (Act)
        Vault result = vaultService.createVault(ownerId, request);

        // Kiểm tra kết quả (Assert)
        assertNotNull(result);
        assertEquals("My Secret Vault", result.getName());
        assertEquals(VaultStatus.ACTIVE, result.getStatus());

        // Đảm bảo hàm save và hàm encrypt đã chạy ngầm đúng 1 lần
        verify(vaultRepository, times(1)).save(any(Vault.class));
        verify(digitalAssetRepository, times(1)).save(any(DigitalAsset.class));
        verify(cryptoService, times(1)).encrypt("my-password");
    }

    // Kịch bản 2: Xóa Vault thành công (Mô phỏng FR-27)
    @Test
    void deleteVault_Success() throws Exception {
        UUID vaultId = UUID.randomUUID();
        Vault vault = new Vault();
        vault.setId(vaultId);
        vault.setOwner(mockOwner);
        vault.setStatus(VaultStatus.ACTIVE);

        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(vault));

        vaultService.deleteVault(vaultId, ownerId);

        // Đảm bảo phải tìm tài sản để xóa trước, rồi mới xóa vault sau
        verify(digitalAssetRepository, times(1)).findByVaultId(vaultId);
        verify(vaultRepository, times(1)).delete(vault);
    }

    // Kịch bản 3: Cố tình xóa Vault của người khác -> Phải văng lỗi (Business Rule)
    @Test
    void deleteVault_NotOwner_ThrowsException() {
        UUID vaultId = UUID.randomUUID();
        Vault vault = new Vault();
        vault.setId(vaultId);

        User otherUser = new User();
        otherUser.setId(UUID.randomUUID());
        vault.setOwner(otherUser); // Vault thuộc về người khác

        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(vault));

        // Bắt lỗi phải là RuntimeException
        Exception exception = assertThrows(RuntimeException.class, () -> {
            vaultService.deleteVault(vaultId, ownerId);
        });

        // Nội dung lỗi phải chuẩn xác
        assertEquals("Unauthorized: You don't own this vault", exception.getMessage());
        // Hàm xóa sẽ KHÔNG BAO GIỜ ĐƯỢC CHẠY
        verify(vaultRepository, never()).delete(any());
    }
}
