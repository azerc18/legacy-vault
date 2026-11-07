# LegacyVault

**LegacyVault** là nền tảng quản lý di sản số và bàn giao tài sản kỹ thuật số qua cơ chế **Dead Man's Switch (DMS)**: Owner cất tài sản (mật khẩu, ví, tài liệu) vào Vault đã mã hóa; nếu Owner ngừng check-in, DMS kích hoạt, Executor nhận bàn giao và nộp giấy tờ, Legal Verifier duyệt và ký, rồi từng Beneficiary xác thực danh tính để nhận phần tài sản được phân bổ.

Repo gồm cả **backend** (Spring Boot, thư mục gốc) và **frontend** (React, thư mục `frontend/`). Khi đóng gói, frontend được build vào `static/` của backend, chạy chung **một container** cùng origin (cookie JWT là `SameSite=Strict`).

- Bản chạy thật: **https://legacyvault.codes** (Azure App Service + Azure Database for MySQL, file lưu trên Cloudflare R2).
- Môn học: SWP391. Tài liệu yêu cầu: xem [docs/README.md](docs/README.md).

## Mục lục

1. [Công nghệ](#1-công-nghệ)
2. [Yêu cầu môi trường](#2-yêu-cầu-môi-trường)
3. [Cấu hình biến môi trường](#3-cấu-hình-biến-môi-trường)
4. [Khởi động MySQL (và MinIO) bằng Docker Compose](#4-khởi-động-mysql-và-minio-bằng-docker-compose)
5. [Chạy backend](#5-chạy-backend)
6. [Chạy frontend](#6-chạy-frontend)
7. [Chạy cả app trong một container](#7-chạy-cả-app-trong-một-container)
8. [Profile Spring](#8-profile-spring)
9. [Database và Flyway migration](#9-database-và-flyway-migration)
10. [Tác vụ định kỳ (scheduler)](#10-tác-vụ-định-kỳ-scheduler)
11. [Test](#11-test)
12. [CI/CD và deploy](#12-cicd-và-deploy)
13. [Cấu trúc thư mục](#13-cấu-trúc-thư-mục)
14. [Xử lý sự cố thường gặp](#14-xử-lý-sự-cố-thường-gặp)

## 1. Công nghệ

| Lớp | Công nghệ |
|---|---|
| Backend | Spring Boot 4.1, **Java 25**, Maven Wrapper, cổng **8084**, package `com.ltld.app.legacyvault` |
| Database | MySQL 8.4, **Flyway** (`ddl-auto: validate`), migration trong `src/main/resources/db/migration` |
| Frontend | React 19, Vite, react-router 7, axios, i18next (vi/en), Tailwind (CDN trong `frontend/index.html`) |
| Bảo mật | JWT trong cookie, RBAC, 2FA TOTP, đăng nhập Google (tùy chọn), mã hóa AES-256-GCM bằng `CRYPTO_MASTER_KEY` |
| Lưu file | `APP_STORAGE_PROVIDER=local` (thư mục `uploads/`) hoặc `s3` (MinIO / Cloudflare R2 / AWS S3); file luôn được mã hóa trước khi lưu |
| Mail | SMTP Gmail với App Password |
| Thanh toán | VNPay (sandbox) cho gói VIP |
| Trợ lý AI | `AI_PROVIDER=mock` (mặc định) hoặc `gemini` |
| PDF | OpenPDF + font Noto Sans (`src/main/resources/fonts/`) |
| CI/CD | GitHub Actions: `Java CI` (mọi PR và push `main`) → `Deploy` (build image, đẩy GHCR, cập nhật Azure Web App) |

## 2. Yêu cầu môi trường

| Công cụ | Phiên bản | Ghi chú |
|---|---|---|
| JDK | **25** | Khớp `<java.version>25</java.version>` trong `pom.xml`. Cài Temurin 25 (Adoptium) hoặc SDKMAN (`sdk install java 25-tem`). |
| Maven | không cần cài | Dùng Maven Wrapper (`mvnw` / `mvnw.cmd`). |
| Node.js | 22 trở lên (nhóm dùng 24 LTS) | Chạy frontend. `Dockerfile` build FE bằng `node:22-alpine`. |
| Docker Desktop / Docker Compose | mới nhất | Chạy MySQL (và MinIO nếu thử lưu file S3). **Bắt buộc khi chạy test** vì integration test dùng Testcontainers MySQL. |
| Git | mới nhất | |
| IDE | tùy chọn | IntelliJ IDEA. |

Kiểm tra:
```bash
java -version   # phải là 25.x
node -v
docker version
```

> Mới cài Java/Node thì mở lại terminal (và tắt hẳn IntelliJ) để nhận PATH mới.

## 3. Cấu hình biến môi trường

Ứng dụng đọc cấu hình nhạy cảm từ biến môi trường. Khi chạy local, nhóm để chúng trong file `.env` ở thư mục gốc. **Không commit `.env`** (đã có trong `.gitignore`).

```bash
cp .env.example .env
```
PowerShell:
```powershell
Copy-Item .env.example .env
```

Mở `.env` và điền giá trị. Danh sách đầy đủ có chú thích nằm trong [.env.example](.env.example); các biến chính:

| Biến | Bắt buộc | Ý nghĩa |
|---|---|---|
| `DB_HOST`, `DB_PORT` | có | Host/cổng MySQL (local: `localhost`, `3306`) |
| `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD` | có | Database và tài khoản app dùng để kết nối |
| `MYSQL_ROOT_PASSWORD` | chỉ cho Docker | Mật khẩu root của container MySQL, app không dùng |
| `JWT_SECRET` | có | Khóa ký JWT, không có giá trị mặc định |
| `JWT_ACCESS_TOKEN_TTL_SECONDS`, `JWT_REFRESH_TOKEN_TTL_SECONDS` | không | Mặc định 900 (15 phút) và 604800 (7 ngày) |
| `JWT_COOKIE_SECURE` | không | `false` khi chạy HTTP local; profile `prod` luôn bật `true` |
| `CRYPTO_MASTER_KEY` | có | Khóa AES-256 (Base64, 32 byte). **Mất key = mất toàn bộ dữ liệu đã mã hóa.** Mỗi môi trường một key riêng |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | có | Gmail + App Password. Đăng ký tài khoản cần mail xác thực |
| `FRONTEND_BASE_URL` | khi deploy | Dùng tạo link trong email. Local `http://localhost:5173`; production đặt theo domain thật |
| `APP_ADMIN_EMAIL`, `APP_ADMIN_PASSWORD` | không | Tự tạo tài khoản Admin ở lần khởi động đầu nếu chưa có |
| `APP_STORAGE_PROVIDER` | không | `local` (mặc định) hoặc `s3` |
| `APP_STORAGE_UPLOAD_DIR` | không | Thư mục file khi `local`, mặc định `uploads/` |
| `APP_STORAGE_MIGRATE_LOCAL` | không | `true` một lần để đưa file cũ trong `uploads/` lên bucket, rồi tắt |
| `S3_ENDPOINT`, `S3_REGION`, `S3_BUCKET`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `S3_PATH_STYLE` | khi `s3` | Kết nối MinIO / R2 (R2: `S3_REGION=auto`) |
| `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET` | khi dùng thanh toán | Lấy từ cổng merchant VNPay sandbox |
| `VNPAY_RETURN_URL`, `VNPAY_PAY_URL` | khi deploy | Mặc định trỏ `localhost` và URL sandbox; production đặt `VNPAY_RETURN_URL` theo domain thật |
| `AI_PROVIDER`, `GEMINI_API_KEY`, `GEMINI_MODEL` | không | Trợ lý AI; `mock` không cần key |
| `GOOGLE_CLIENT_ID` | không | OAuth Client ID; để trống thì ẩn nút đăng nhập Google |

Sinh `CRYPTO_MASTER_KEY` và `JWT_SECRET`:
```bash
openssl rand -base64 32   # CRYPTO_MASTER_KEY
openssl rand -base64 48   # JWT_SECRET
```
PowerShell:
```powershell
$b = New-Object byte[] 32; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
```

> Giá trị thật của bí mật (mật khẩu DB, key, App Password, VNPay secret, khóa R2) chỉ để trong `.env` trên máy hoặc App Settings trên Azure. Không dán vào code, commit, issue hay nhóm chat.

## 4. Khởi động MySQL (và MinIO) bằng Docker Compose

```bash
docker compose up -d mysql          # chỉ MySQL, đủ cho chạy app với APP_STORAGE_PROVIDER=local
docker compose up -d                # MySQL + MinIO (thử APP_STORAGE_PROVIDER=s3 trên máy)
```

- **MySQL 8.4**: cổng `3306:3306`, dữ liệu ở volume `legacyvault_mysql`.
- **MinIO**: API `http://localhost:9000`, console `http://localhost:9001` (đăng nhập bằng `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD`), dữ liệu ở volume `legacyvault_minio`. Bucket do app tự tạo khi khởi động. Chỉ dùng cho môi trường phát triển.

Kiểm tra:
```bash
docker compose ps
docker compose logs -f mysql   # chờ dòng "ready for connections" trước khi chạy app
```

Lưu ý:
- **Chạy lệnh ở thư mục gốc repo** để Compose đọc được `.env`; nếu không, container dùng giá trị mặc định ghi trong `docker-compose.yml`.
- `docker compose down` giữ dữ liệu; `docker compose down -v` **xóa sạch DB** (chỉ dùng khi cố ý reset).
- MySQL chỉ tạo database/user từ biến `MYSQL_*` ở lần đầu tạo volume. Đổi các biến này sau đó thì phải `down -v` rồi `up -d` lại.
- `docker-compose.yml` không có `healthcheck`: đợi log `ready for connections` rồi mới chạy Spring Boot.
- Máy đã có MySQL ở cổng 3306 thì đổi cổng map (ví dụ `3307:3306`) và `DB_PORT` tương ứng.

## 5. Chạy backend

Spring Boot **không tự đọc file `.env`**, nên phải nạp biến vào shell trước.

PowerShell (Windows):
```powershell
Get-Content .env | ForEach-Object { if ($_ -match '^\s*([^#=\s][^=]*)=(.*)$') { [Environment]::SetEnvironmentVariable($matches[1].Trim(), $matches[2].Trim(), 'Process') } }
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```
(Giữ ngoặc kép quanh `-D...`, nếu không PowerShell sẽ cắt tham số.)

Linux/macOS:
```bash
set -a; . ./.env; set +a
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

IntelliJ: Run configuration của `LegacyVaultApplication` → Active profiles `local`, Environment variables dán nội dung `.env` (hoặc plugin EnvFile).

Kiểm tra:
```bash
curl http://localhost:8084/actuator/health    # {"status":"UP"}
```

Lần khởi động đầu, Flyway tự tạo bảng (mục 9) và `DataInitializer` seed vai trò, quyền và tài khoản Admin (nếu đặt `APP_ADMIN_EMAIL` / `APP_ADMIN_PASSWORD`).

Thử thanh toán VNPay sandbox trên máy: VNPay không gọi IPN về `localhost` được, nên có thể đặt thêm `APP_VNPAY_ACTIVATEONRETURN=true` (chỉ local, **không bao giờ đặt trên Azure**) để gói được kích hoạt khi trình duyệt quay về `vnpay-return`.

## 6. Chạy frontend

Terminal khác:
```powershell
cd frontend
npm install
npm run dev
```
Mở `http://localhost:5173`. Vite proxy `/api` sang `http://localhost:8084` (xem `frontend/vite.config.js`).

Kiểm tra trước khi push: `npm run lint` (oxlint) và `npm run build`.

Lỗi `running scripts is disabled on this system` trên PowerShell: `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned`.

## 7. Chạy cả app trong một container

`Dockerfile` nhiều bước: build FE (`node:22-alpine`) → copy `frontend/dist` vào `static/` → build BE (`eclipse-temurin:25-jdk`) → chạy bằng `eclipse-temurin:25-jre` ở cổng 8084. Backend có SPA fallback nên F5 ở trang con không bị 404.

```bash
docker build -t legacyvault .
docker run --rm -p 8084:8084 --env-file .env -e SPRING_PROFILES_ACTIVE=docker legacyvault
```
Trong container, `localhost` là chính container: đặt `DB_HOST=host.docker.internal` (Docker Desktop) để kết nối MySQL đang chạy trên máy. Mở `http://localhost:8084`.

## 8. Profile Spring

Cấu hình nằm ở `src/main/resources/`:

| File | Khi nào dùng |
|---|---|
| `application.yaml` | Luôn nạp. Cổng, Flyway, mail, JWT, storage, VNPay, Google, AI, CORS. Đọc bộ biến `DB_HOST`, `DB_PORT`, `MYSQL_*` |
| `application-local.yaml` | Chạy trên máy cá nhân. Có giá trị mặc định cho DB, **bật eKYC mock** |
| `application-docker.yaml` | App chạy trong container trên máy, **bật eKYC mock** |
| `application-prod.yaml` | Azure. Kết nối MySQL bắt buộc SSL, `cookie-secure: true`. **eKYC mock tắt** (Beneficiary xác thực bằng OTP) |

Trên Azure đặt `SPRING_PROFILES_ACTIVE=prod`.

## 9. Database và Flyway migration

- Schema do **Flyway** quản lý, Hibernate chỉ `validate`. Không sửa schema bằng tay, không bật lại `ddl-auto: update`.
- Migration nằm trong `src/main/resources/db/migration`, tên theo timestamp: `VyyyyMMdd_HHmm__mo_ta.sql` (ví dụ `V20261104_1500__f4b_legacy_message_delivered_notification.sql`).
- Mỗi thay đổi entity cần migration **trong cùng PR**.
- **Không đánh số lùi ngày**: nhánh merge sau phải đổi tên migration của mình lên lớn hơn bản mới nhất trên `main`, nếu không app báo `FlywayValidateException` và không khởi động.
- Không sửa migration đã merge (checksum thay đổi cũng làm Flyway validate lỗi); muốn đổi thì thêm migration mới.
- DB tạo từ trước khi có Flyway (bằng `ddl-auto`) được baseline ở `20261101.0900` (`baseline-on-migrate: true`).

## 10. Tác vụ định kỳ (scheduler)

Các tác vụ chạy trong chính ứng dụng (`@Scheduled`), nên trên Azure phải bật **Always On** và chỉ chạy **1 instance** (2 instance sẽ chạy trùng, gửi mail trùng).

| Tác vụ | Thuộc tính | Mặc định |
|---|---|---|
| Quét DMS quá hạn để kích hoạt bàn giao | `app.dms.trigger-scan-delay` | `PT20S` (20 giây) |
| Gửi nhắc check-in DMS | `app.dms.reminder-scan-delay` | `PT15M` |
| Chuyển sang Executor dự phòng khi Executor chính không phản hồi | `app.dms.escalate-scan-delay` | `PT5M` |
| Gửi lại thông báo Beneficiary bị lỗi | `app.beneficiary.notify-retry-delay` | `PT1H` |
| Hết hạn nhận tài sản | `app.beneficiary.claim-expiry-scan-delay` | `PT1H` |
| Chuyển phần phân bổ sang người dự phòng | `app.beneficiary.allocation-fallback-scan-delay` | `PT1H` |
| Dọn Vault đã lưu trữ | `app.beneficiary.vault-purge-scan-delay` | `PT6H` |
| Hết hạn yêu cầu xác minh đã duyệt | `app.verifier.approved-expiry-scan-delay` | `PT1H` |
| Gửi lời nhắn di nguyện đến hạn | `app.legacy-message.delivery-scan-delay` | `PT1H` |
| Cảnh báo bảo mật (đăng nhập sai, tải nhiều) | `app.security-alerts.scan-delay` | `PT5M` |
| Kiểm tra gói VIP hết hạn | `app.subscription.expiry-cron` | `0 0 8 * * *` |

Có thể đổi bằng biến môi trường theo relaxed binding của Spring, ví dụ `APP_DMS_TRIGGERSCANDELAY=PT1M`.

## 11. Test

```powershell
.\mvnw.cmd clean verify
```
- Gồm unit test và integration test chạy trên **MySQL thật bằng Testcontainers**: Docker Desktop phải đang chạy. Mất vài phút.
- Trong lúc build: không đổi nhánh, không `cd` vào `target/`, không để máy ngủ.
- Dòng `[ERROR] Surefire is going to kill self fork JVM ...` cuối build là có sẵn, không phải lỗi.
- Frontend: `npm run lint` và `npm run build` trong `frontend/`.

## 12. CI/CD và deploy

| Workflow | Khi nào chạy | Làm gì |
|---|---|---|
| `.github/workflows/java_ci.yml` (**Java CI**) | Mọi pull request, mọi push vào `main` | `mvn clean verify` trên JDK 25 |
| `.github/workflows/deploy.yml` (**Deploy**) | Sau khi Java CI xanh trên push vào `main`, hoặc bấm tay **Run workflow** | Build image, đẩy `ghcr.io/azerc18/legacy-vault:<commit>` và `:latest`, chuyển Azure Web App sang image mới, chờ `https://legacyvault.codes/actuator/health` trả `UP` |

- **Deploy lại hoặc rollback**: GitHub → Actions → Deploy → Run workflow → nhập commit SHA (hoặc nhánh).
- Hạ tầng: resource group `rg-legacyvault`, Web App `legacyvault-luctieulinhdong` (App Service B1 Linux, container, 1 instance, Always On), Azure Database for MySQL Flexible Server (8.4), Cloudflare R2 bucket `legacyvault`, domain `legacyvault.codes` với chứng chỉ managed của Azure.
- Đăng nhập Azure trong workflow bằng OIDC (`vars.AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID` của environment `legacy-vault`), không lưu mật khẩu trong repo.
- App Settings tối thiểu trên Web App: `WEBSITES_PORT=8084`, `SPRING_PROFILES_ACTIVE=prod`, nhóm DB (`DB_HOST`, `DB_PORT`, `MYSQL_*`), `JWT_SECRET`, `CRYPTO_MASTER_KEY`, `MAIL_*`, `FRONTEND_BASE_URL=https://legacyvault.codes`, `APP_STORAGE_PROVIDER=s3` + nhóm `S3_*` của R2, `VNPAY_*` (với `VNPAY_RETURN_URL=https://legacyvault.codes/api/payments/vnpay-return`), `AI_PROVIDER`, `GOOGLE_CLIENT_ID`. Giá trị chỉ đặt trên Azure, không ghi vào repo.
- VNPay: IPN URL đăng ký ở cổng merchant là `https://legacyvault.codes/api/payments/vnpay-ipn`.
- Mọi thay đổi tài nguyên Azure hoặc phát sinh chi phí phải hỏi chủ subscription trước.

## 13. Cấu trúc thư mục

```
legacy-vault/
├── .env.example                 # mẫu biến môi trường (commit); .env thật không commit
├── .github/workflows/           # java_ci.yml (CI), deploy.yml (CD lên Azure)
├── .mvn/wrapper/, mvnw, mvnw.cmd
├── Dockerfile, .dockerignore    # build FE + BE thành 1 image
├── docker-compose.yml           # MySQL 8.4 + MinIO cho local/dev
├── docs/                        # SRS, use case, script SQL cũ (xem docs/README.md)
├── pom.xml
├── frontend/                    # React + Vite
│   ├── index.html               # Tailwind CDN + token màu, favicon
│   ├── public/                  # favicon.ico, brand/ (logo), site.webmanifest
│   └── src/
│       ├── Owner/ Executor/ LegalVerifier/ Beneficiary/ Admin/   # màn hình theo actor
│       ├── LogInLogOut/ Profile/ Assistant/ Common/
│       ├── api/                 # *Api.js, http.js (axios)
│       └── i18n/locales/{vi,en}/
└── src/
    ├── main/
    │   ├── java/com/ltld/app/legacyvault/
    │   │   ├── config/          # SpaWebConfig (SPA fallback), cấu hình chung
    │   │   ├── controller/      # REST API, không chứa nghiệp vụ
    │   │   ├── dto/             # request/response theo nhóm
    │   │   ├── entity/ enums/ event/ repository/
    │   │   ├── exception/       # lỗi nghiệp vụ có errorCode, GlobalExceptionHandler
    │   │   ├── security/        # Spring Security, JWT
    │   │   ├── service/         # nghiệp vụ theo nhóm (dms, executor, beneficiary, payment, ...)
    │   │   └── utility/         # DataInitializer, MockKycVerifier, ...
    │   └── resources/
    │       ├── application.yaml, application-{local,docker,prod}.yaml
    │       ├── db/migration/    # Flyway
    │       ├── fonts/           # Noto Sans cho PDF
    │       └── assistant/       # tài liệu cho trợ lý AI
    └── test/java/...            # unit + integration test (Testcontainers MySQL)
```

## 14. Xử lý sự cố thường gặp

- **App không start, thiếu `JWT_SECRET` / `CRYPTO_MASTER_KEY` / `MAIL_*`**: chưa nạp `.env` vào shell (mục 5) hoặc biến đang rỗng.
- **`FlywayValidateException`**: migration trên nhánh có số nhỏ hơn bản mới nhất của `main`, hoặc migration đã merge bị sửa (mục 9).
- **`Schema-validation: missing column/table`**: entity đổi mà thiếu migration.
- **Không kết nối được DB**: MySQL chưa `ready for connections`, sai profile, hoặc chạy Compose ngoài thư mục gốc nên user/mật khẩu khác `.env`.
- **`Port 8084 already in use`** / **`Port 3306 already in use`**: tắt tiến trình đang chiếm cổng hoặc đổi cổng map (mục 4).
- **Build lỗi vì sai Java**: `java -version` phải là 25, không phải 17/21.
- **Test integration lỗi `Could not find a valid Docker environment`**: Docker Desktop chưa chạy.
- **`mvnw: command not found`** (Linux/macOS): `chmod +x mvnw`.
- **Mail trong email chứa link `localhost` trên bản deploy**: chưa đặt `FRONTEND_BASE_URL`.
