# Sửa CI: 3 failures + 30 errors

Có **hai nhóm lỗi độc lập**:

| Nhóm | Số lỗi | Nguyên nhân | Sửa ở đâu |
|---|---|---|---|
| A | 30 errors | Môi trường test: Spring dùng lại context đã cache, nhưng container MySQL của context đó đã bị tắt | Code test (Testcontainers) |
| B | 3 failures | Test unit chưa cập nhật theo code mới | 2 file test |

---

## A. Lỗi môi trường: "Connection refused" / "Failed to load ApplicationContext"

### Chuyện gì xảy ra

Bạn có 15 class integration test, mỗi class tự khai báo **một container riêng**:

```java
@Container
@ServiceConnection
static MySQLContainer mysql = new MySQLContainer("mysql:8.4");
```

1. `@Container static` có nghĩa là container **bật khi class bắt đầu và tắt khi class chạy xong**.
2. Spring Test lại **cache ApplicationContext** và dùng lại cho class khác có cấu hình giống. Ví dụ ba class `...RepositoryBeneficiaryCheck`, `...LiveByExecutor`, `VaultRepository...EmailCheck` có cùng bộ properties.
3. Class thứ hai nhận lại context cũ. Connection pool của context đó vẫn trỏ vào port của container thứ nhất, mà container đó đã tắt.
   - Kết quả: `Communications link failure` / `Connection refused`.
4. Nếu context hỏng ngay lúc khởi động thì Spring đánh dấu "failure threshold (1) exceeded". Mọi class dùng chung cấu hình đó báo `Failed to load ApplicationContext`.
   - Các field `@Autowired` (`dmsConfigRepository`…) vì vậy còn `null`, nên `@AfterEach` ném NullPointerException.
5. Dòng `alter table role_authorities drop foreign key … Table 'test.role_authorities' doesn't exist` **chỉ là cảnh báo**: `ddl-auto=create` thử drop trên DB trống. Lỗi này không làm hỏng build.

Trên máy local thường không gặp, vì thứ tự chạy test khác, hoặc bạn chạy từng class riêng.

### Cách sửa: một container MySQL dùng chung cho cả bộ test (singleton)

**Bước 1. Tạo file mới** `src/test/java/com/ltld/app/legacyvault/support/MySqlContainerSupport.java`:

```java
package com.ltld.app.legacyvault.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Một container MySQL cho TOÀN BỘ integration test, bật một lần, sống đến hết JVM (Ryuk tự dọn).
 * Không dùng @Container: annotation đó tắt container sau mỗi class, trong khi Spring vẫn cache
 * ApplicationContext trỏ vào container đã tắt -> Connection refused.
 */
public abstract class MySqlContainerSupport {

    @ServiceConnection
    protected static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    static {
        MYSQL.start();
    }
}
```

**Bước 2. Sửa 15 class integration test.** Mỗi class làm 3 việc giống nhau:

1. Thêm `extends MySqlContainerSupport` sau tên class.
2. Xoá 3 dòng khai báo container cũ:

   ```java
   @Container
   @ServiceConnection
   static MySQLContainer mysql = new MySQLContainer("mysql:8.4");
   ```

3. Xoá các import không còn dùng (`Container`, `ServiceConnection`, `MySQLContainer`), rồi thêm:

   ```java
   import com.ltld.app.legacyvault.support.MySqlContainerSupport;
   ```

Giữ nguyên `@SpringBootTest(...)` và `@Testcontainers(disabledWithoutDocker = true)`.

Ví dụ trước và sau khi sửa:

```java
@Testcontainers(disabledWithoutDocker = true)
class OwnerActivityLogIntegrationTest {
    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4");
```

thành

```java
@Testcontainers(disabledWithoutDocker = true)
class OwnerActivityLogIntegrationTest extends MySqlContainerSupport {
```

**Danh sách 15 file cần sửa:**

| Thư mục | File |
|---|---|
| `integration/` | `AssetAccessRecordIntegrationTest`, `BeneficiaryDmsGuardIntegrationTest`, `OtpConcurrencyIntegrationTest`, `OwnerActivityLogIntegrationTest`, `VaultActivityLogIntegrationTest`, `VaultQueryIntegrationTest` |
| `repository/` | `ExecutorAssetRepositoryIntegrationTest`, `ExecutorAssignmentRepositoryBeneficiaryCheckIntegrationTest`, `ExecutorAssignmentRepositoryLiveByExecutorIntegrationTest`, `ExecutorHandoverRepositoryIntegrationTest`, `VaultRepositoryBeneficiaryEmailCheckIntegrationTest` |
| `service/dmsservice/` | `DmsConfigIntegrationTest`, `DmsEscalationIntegrationTest` |
| `service/executorservice/` | `ExecutorManagementIntegrationTest`, `ExecutorVerificationIntegrationTest`, `VerificationExchangeIntegrationTest` |

> **Vì sao cách này đúng:** container giờ sống suốt JVM, nên mọi context đã cache luôn trỏ vào một DB còn chạy. CI cũng nhanh hơn nhiều, vì chỉ khởi động MySQL **một lần** thay vì 15 lần.
>
> Dữ liệu giữa các class không giẫm lên nhau: mỗi class đã tự dọn dữ liệu của mình trong `@AfterEach`, và `ddl-auto=create`/`create-drop` dựng lại schema khi tạo context mới.

---

## B. 3 test unit sai so với code mới

### B1. `OwnerActivityLogServiceImplTest.ownerHiddenActions_areAdminOnlyActions_andUnmodifiable`

Code đã thêm `EXECUTOR_ASSIGNED_BY_ADMIN` vào `AuditDescriptionBuilder.OWNER_HIDDEN_ACTIONS` (Admin cứu hộ không hiện trong nhật ký của Owner), nhưng test chưa cập nhật. Sửa dòng 113–114:

```java
assertThat(hidden).containsExactlyInAnyOrder(
        AuditAction.ADMIN_VAULT_VIEWED, AuditAction.DMS_SUSPENDED, AuditAction.DMS_RESUMED,
        AuditAction.EXECUTOR_ASSIGNED_BY_ADMIN);
```

### B2 và B3. `VaultServiceImplTest.deleteVault_*`

Code `deleteVault` đã đổi:

- Cũ: `findByVaultId` + `deleteAllInBatch`.
- Mới: `findFileUrlsByVaultId` + `deleteByVaultId`, để sửa lỗi `TransientPropertyValueException`.

Test vẫn kiểm tra theo cách cũ. Ngoài ra `MockitoExtension` chạy chế độ strict, nên các stub `findByVaultId` không còn được gọi tới cũng sẽ làm test lỗi.

**`deleteVault_Success`:** thay 2 dòng stub

```java
when(digitalAssetRepository.findByVaultId(vaultId)).thenReturn(Collections.emptyList());
when(legalDocumentRepository.findByVaultId(vaultId)).thenReturn(Collections.emptyList());
```

bằng

```java
when(legalDocumentRepository.findFileUrlsByVaultId(vaultId)).thenReturn(Collections.emptyList());
```

và thay khối verify cuối bằng

```java
// Lấy đường dẫn file trước, rồi bulk delete con, cuối cùng mới xóa vault
verify(legalDocumentRepository).findFileUrlsByVaultId(vaultId);
verify(legalDocumentRepository).deleteByVaultId(vaultId);
verify(digitalAssetRepository).deleteByVaultId(vaultId);
verify(vaultRepository).delete(vault);
verifyNoInteractions(fileStorageService);
```

**`deleteVault_Success_WithDocumentsAndAssets`:** xoá phần tạo `DigitalAsset asset` và `LegalDocument doc`, cùng 2 stub `findByVaultId`. Thay bằng

```java
when(legalDocumentRepository.findFileUrlsByVaultId(vaultId)).thenReturn(List.of("uploads/test-file.pdf"));
```

và thay 2 dòng `verify(...deleteAllInBatch(...))` bằng

```java
verify(legalDocumentRepository).deleteByVaultId(vaultId);
verify(digitalAssetRepository).deleteByVaultId(vaultId);
```

Các dòng verify phía sau (`vaultRepository.delete`, `fileStorageService.deleteFile("uploads/test-file.pdf")`, `auditLogService.log(...)`) giữ nguyên.

Cần thêm import nếu file chưa có:

```java
import static org.mockito.Mockito.verifyNoInteractions;
```

Nếu `DigitalAsset` không còn được dùng ở chỗ nào khác trong file thì xoá luôn import của nó.

---

## Kiểm tra lại trên máy trước khi push

```bash
mvn -q -Dtest=OwnerActivityLogServiceImplTest,VaultServiceImplTest test   # nhóm B, không cần Docker
mvn clean verify                                                           # cả bộ, cần Docker Desktop đang chạy
```

Kết quả mong đợi là `Failures: 0, Errors: 0`.
