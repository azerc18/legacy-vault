# Review – PR #0 `fix/crypto-aes-gcm`: AES-256-GCM + master key từ biến môi trường

**Nguồn:** source đang có trên máy, `D:\Project\legacy-vault`, nhánh `fix/crypto-aes-gcm`. Đọc trực tiếp các file `CryptoServiceImpl`, `CryptoService`, `VaultServiceImpl`, `application*.yaml`, `.env.example`, `.gitignore`, `pom.xml`, `java_ci.yml`, 2 integration test.

> Không đọc file `.env` (có secret thật). Mình cũng không chạy được `git` trên máy bạn, nên phần "diff so với `main`" ở mục 4 cần bạn tự kiểm tra.

---

## 1. Kết luận

**Request changes (nhỏ).** Phần lõi `CryptoServiceImpl` đã đúng thiết kế, không phát hiện lỗi. Còn **2 điểm bắt buộc trước khi tạo PR**:
1. **B1:** 2 integration test (`@SpringBootTest`) chưa có `app.security.crypto.master-key`. Trên CI có Docker nên 2 test này sẽ thực sự chạy, context không khởi động được, và **CI đỏ**.
2. **B2:** chưa có `CryptoServiceImplTest`. Đây là PR bảo mật quan trọng nhất của dự án nên phải có test chứng minh các tính chất: IV ngẫu nhiên, phát hiện dữ liệu bị sửa, sai key thì không giải mã được, key sai định dạng thì app dừng ngay.

---

## 2. Đã làm đúng

| Hạng mục | Trạng thái |
|---|---|
| AES/GCM/NoPadding, IV 12 byte ngẫu nhiên cho mỗi lần mã hóa, tag 128 bit | ✅ |
| Định dạng `[version][IV][ciphertext+tag]`, kiểm tra version + độ dài tối thiểu trước khi giải mã | ✅ |
| Key Base64 32 byte, **không có giá trị mặc định**, sai định dạng hoặc sai độ dài thì app không khởi động, kèm message rõ ràng | ✅ |
| `StandardCharsets.UTF_8` tường minh (bản cũ dùng charset mặc định của hệ điều hành) | ✅ |
| `application.yaml`: `master-key: ${CRYPTO_MASTER_KEY}`; các profile local/docker/prod kế thừa, không ghi đè | ✅ |
| `VaultServiceImpl.createVault` ghi `encryptionKeyRef = "v1"` | ✅ |
| `.env` có trong `.gitignore` | ✅ |
| `pom.xml` đã có `spring-boot-testcontainers`, `testcontainers-junit-jupiter`, `testcontainers-mysql` (đóng luôn điểm N1 của review FR-19 v2) | ✅ |
| Interface `CryptoService` giữ nguyên, nên FR-01/04/17/18/19 không phải sửa | ✅ |

---

## 3. Cần sửa

### B1 – [Bắt buộc] Integration test thiếu master key

`AssetAccessRecordIntegrationTest` và `OtpConcurrencyIntegrationTest` đều khai báo properties riêng (`security.jwt.secret`, `spring.mail.*`…) nhưng chưa có key mã hóa. CI không có biến `CRYPTO_MASTER_KEY`, nên `${CRYPTO_MASTER_KEY}` không resolve được. `CryptoServiceImpl` không tạo được bean, và cả 2 test đều fail ở bước khởi động context.

**Cách nhanh nhất, đồng bộ với cách đang cấp JWT secret:** thêm 1 dòng vào `properties` của **cả 2** class:
```java
@SpringBootTest(properties = {
        "security.jwt.secret=integration-test-secret-key-at-least-32-bytes!!",
        "app.security.crypto.master-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",   // key chỉ dùng cho test
        "spring.mail.username=test@example.com",
        "spring.mail.password=test",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
```
(Về lâu dài, nên gom các property dùng chung vào `src/test/resources/application-test.yaml` + `@ActiveProfiles("test")`, để test mới không bị quên. Không đặt tên file là `application.yaml`.)

Kiểm tra ở máy: bật Docker, chạy `./mvnw clean verify`. Cả 2 integration test phải **chạy** (không bị skip) và xanh.

### B2 – [Bắt buộc] Thêm `CryptoServiceImplTest`

Đặt tại `src/test/java/com/ltld/app/legacyvault/service/cryptoservice/CryptoServiceImplTest.java`. Đây là unit test thuần, không cần Spring:
```java
package com.ltld.app.legacyvault.service.cryptoservice;

import org.junit.jupiter.api.Test;

import javax.crypto.AEADBadTagException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CryptoServiceImplTest {

    private static final String KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final String OTHER_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private final CryptoServiceImpl crypto = new CryptoServiceImpl(KEY);

    @Test
    void roundTrip_vietnameseText() throws Exception {
        String plain = "Mật khẩu Vietcombank: Tài khoản 0123 – ghi chú có dấu";
        assertThat(crypto.decrypt(crypto.encrypt(plain))).isEqualTo(plain);
    }

    @Test
    void roundTrip_bytes_andEmptyString() throws Exception {
        byte[] data = "%PDF-1.7 ...".getBytes(StandardCharsets.UTF_8);
        assertThat(crypto.decryptBytes(crypto.encryptBytes(data))).isEqualTo(data);
        assertThat(crypto.decrypt(crypto.encrypt(""))).isEmpty();
    }

    @Test
    void samePlaintext_producesDifferentCiphertext() throws Exception {
        assertThat(crypto.encrypt("same")).isNotEqualTo(crypto.encrypt("same"));
    }

    @Test
    void ciphertext_startsWithVersionByte() throws Exception {
        assertThat(crypto.encryptBytes(new byte[]{1, 2, 3})[0]).isEqualTo((byte) 1);
    }

    @Test
    void tamperedCiphertext_isRejected() throws Exception {
        byte[] ct = crypto.encryptBytes("secret".getBytes(StandardCharsets.UTF_8));
        ct[ct.length - 1] ^= 1;
        assertThatThrownBy(() -> crypto.decryptBytes(ct)).isInstanceOf(AEADBadTagException.class);
    }

    @Test
    void wrongKey_cannotDecrypt() throws Exception {
        String ct = crypto.encrypt("secret");
        assertThatThrownBy(() -> new CryptoServiceImpl(OTHER_KEY).decrypt(ct))
                .isInstanceOf(AEADBadTagException.class);
    }

    @Test
    void keyNot32Bytes_failsFast() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new CryptoServiceImpl(shortKey))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("32 byte");
    }

    @Test
    void keyNotBase64_failsFast() {
        assertThatThrownBy(() -> new CryptoServiceImpl("1"))      // đúng giá trị đang có trong .env.example
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Base64");
    }

    @Test
    void nullInput_returnsNull() throws Exception {
        assertThat(crypto.encrypt(null)).isNull();
        assertThat(crypto.decrypt(null)).isNull();
        assertThat(crypto.encryptBytes(null)).isNull();
        assertThat(crypto.decryptBytes(null)).isNull();
    }

    @Test
    void garbageOrWrongVersion_isRejected() {
        assertThatThrownBy(() -> crypto.decryptBytes(new byte[]{9, 9, 9}))
                .isInstanceOf(GeneralSecurityException.class);
        byte[] fake = new byte[40];
        fake[0] = 2;                                              // version không hỗ trợ
        assertThatThrownBy(() -> crypto.decryptBytes(fake)).isInstanceOf(GeneralSecurityException.class);
    }
}
```

---

## 4. Minor / Nit (không chặn)

| # | Vị trí | Vấn đề | Gợi ý |
|---|---|---|---|
| M1 | `.env.example` | `CRYPTO_MASTER_KEY=1` không theo kiểu placeholder của các dòng khác, và không hướng dẫn cách sinh key. Người mới copy sang `.env` sẽ nhận lỗi "phải là chuỗi Base64" mà không biết làm gì tiếp | Đổi thành:<br>`# AES-256 key, Base64 32 byte. Sinh bằng: openssl rand -base64 32`<br>`CRYPTO_MASTER_KEY=YOUR_BASE64_32_BYTE_KEY` |
| M2 | `README.md` | Chưa cập nhật (bảng biến môi trường ở mục 3 chưa có `CRYPTO_MASTER_KEY`) | Thêm 1 dòng vào bảng + lệnh `openssl rand -base64 32` |
| M3 | `VaultServiceImpl` | `import ...CryptoServiceImpl` chỉ để lấy `KEY_REF`, làm service phụ thuộc vào lớp cài đặt cụ thể | Chuyển hằng số lên interface: `String CURRENT_KEY_REF = "v1";` trong `CryptoService`, rồi dùng `CryptoService.CURRENT_KEY_REF` |
| M4 | Phạm vi nhánh | Thư mục làm việc đang có code FR-17/18/19 (`AssetAccessRecord`, `/close`…). Nếu FR-19 chưa merge vào `main` thì PR này sẽ kéo theo cả FR-19 | Chạy `git diff --stat main...fix/crypto-aes-gcm`: chỉ nên thấy `CryptoServiceImpl`, `VaultServiceImpl`, `application.yaml`, `.env.example`, `README.md`, 2 integration test và `CryptoServiceImplTest` |

---

## 5. Mô tả PR đề xuất

```
fix(security): AES-256-GCM, master key lấy từ biến môi trường (#0)

- Bỏ key mặc định trong code; thiếu hoặc sai key thì app không khởi động
- AES/ECB -> AES/GCM: IV ngẫu nhiên cho mỗi lần mã hóa, có kiểm tra toàn vẹn (tag)
- Định dạng bản mã: version + IV + ciphertext/tag (sẵn cho việc xoay khóa sau này)
- encryptionKeyRef = "v1"
- Thêm CRYPTO_MASTER_KEY vào .env.example, README, integration test
- Thêm CryptoServiceImplTest

BREAKING
- Mỗi thành viên phải thêm CRYPTO_MASTER_KEY vào .env (openssl rand -base64 32)
- Dữ liệu mã hóa cũ (ECB) không đọc được: chạy `docker compose down -v && docker compose up -d`, xóa thư mục uploads/ nếu có

Thứ tự merge: PR này vào main TRƯỚC; sau đó rebase FR-04, FR-12–15 lên main.
Đáp ứng SRS §5: AES-256, khóa không lưu cùng vị trí với dữ liệu.
```

## 6. Checklist trước khi push

- [ ] B1: thêm key vào 2 integration test
- [ ] B2: thêm `CryptoServiceImplTest`
- [ ] `./mvnw clean verify` xanh khi **bật Docker** (integration test chạy thật, không skip)
- [ ] Xóa `CRYPTO_MASTER_KEY` trong `.env` rồi chạy app: phải dừng ngay với lỗi rõ ràng
- [ ] Tạo 2 vault cùng secret qua Postman: `encrypted_secret` khác nhau, `encryption_key_ref = 'v1'`
- [ ] `git grep "1234567890123456"` không còn kết quả
- [ ] `git diff --stat main...HEAD` chỉ chứa thay đổi của PR này (M4)
