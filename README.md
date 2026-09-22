# LegacyVault

Backend cho hệ thống **LegacyVault** - nền tảng quản lý di sản số và bàn giao tài sản kỹ thuật số qua cơ chế Dead Man's Switch. Xây dựng bằng Spring Boot (Java) + MySQL.

## 1. Yêu cầu môi trường (Prerequisites)

| Công cụ | Phiên bản | Ghi chú |
|---|---|---|
| JDK | **25** | Bắt buộc - khớp `<java.version>25</java.version>` trong `pom.xml`. Cài từ [Adoptium/Temurin](https://adoptium.net/) hoặc SDKMAN (`sdk install java 25-tem`). |
| Maven | không cần cài | Dự án đã có **Maven Wrapper** (`mvnw` / `mvnw.cmd`), tự tải đúng bản Maven 3.9.16. |
| Docker + Docker Compose | mới nhất | Dùng để chạy MySQL, tránh phải cài MySQL local. |
| Git | mới nhất | Clone/kéo code. |
| IDE | tuỳ chọn | IntelliJ IDEA (project hiện có sẵn `.idea/`). |

Kiểm tra JDK sau khi cài:
```bash
java -version
# phải hiển thị 25.x.x
```

## 2. Clone dự án

```bash
git clone https://github.com/azerc18/legacy-vault.git
cd legacy-vault
```

## 3. Cấu hình biến môi trường

Dự án đọc cấu hình nhạy cảm (DB, JWT secret) từ file `.env` ở thư mục gốc. **Không commit `.env` thật lên Git** - chỉ dùng để chạy local.

```bash
cp .env.example .env
```

Mở `.env` và điền giá trị thật:

| Biến | Ý nghĩa | Ví dụ |
|---|---|---|
| `DB_HOST` | Host MySQL | `localhost` |
| `DB_PORT` | Port MySQL | `3306` |
| `MYSQL_DATABASE` | Tên database | `legacy_vault` |
| `MYSQL_USER` | User kết nối DB | `legacy_vault_user` |
| `MYSQL_PASSWORD` | Mật khẩu user DB | *(tự đặt)* |
| `MYSQL_ROOT_PASSWORD` | Mật khẩu root MySQL (dùng bởi container Docker) | *(tự đặt)* |
| `JWT_SECRET` | Khoá ký JWT - **không có giá trị mặc định**, bắt buộc phải set | chuỗi ngẫu nhiên dài, xem lệnh bên dưới |
| `JWT_ACCESS_TOKEN_TTL_SECONDS` | Thời hạn access token (giây) | `900` (15 phút) |
| `JWT_REFRESH_TOKEN_TTL_SECONDS` | Thời hạn refresh token (giây) | `604800` (7 ngày) |

Sinh nhanh một `JWT_SECRET` an toàn:
```bash
openssl rand -base64 48
```

## 4. Khởi động MySQL bằng Docker Compose

```bash
docker compose up -d
```

Compose sẽ tự đọc `.env` cùng thư mục để tạo container MySQL 8.4 (map cổng `3306:3306`, data lưu ở volume `legacyvault_mysql`). Kiểm tra:

```bash
docker compose ps
docker compose logs -f mysql   # xem log nếu MySQL chưa "healthy"
```

> Nếu máy bạn đã có MySQL chạy sẵn ở cổng 3306, đổi `DB_PORT` trong `.env` (và cổng map bên trái trong `docker-compose.yml`) sang cổng khác, ví dụ `3307:3306`.

### Các điểm cần chú ý khi dùng Docker

- **`.env` phải nằm cùng thư mục với `docker-compose.yml`.** Docker Compose tự động đọc file `.env` ở thư mục hiện hành khi chạy `docker compose up`; nếu bạn chạy lệnh từ thư mục khác (VD: từ trong `src/`), biến sẽ không được nạp và container sẽ dùng các giá trị mặc định hard-code trong `docker-compose.yml` (`legacy_vault`, `legacy_vault_user`, `legacyvault_password`, `root_password`) thay vì giá trị thật của bạn.
- **Dữ liệu được lưu ở Docker volume `legacyvault_mysql`, không phải trong thư mục project.** Nghĩa là:
  - `docker compose down` (không có `-v`) → container bị xoá nhưng **dữ liệu DB vẫn còn** trong volume, lần `up -d` sau sẽ thấy lại data cũ.
  - `docker compose down -v` → xoá luôn volume, **mất sạch dữ liệu**, lần sau MySQL khởi tạo lại từ đầu (dùng khi cần "reset" DB sạch, ví dụ đổi `MYSQL_DATABASE`/`MYSQL_ROOT_PASSWORD`).
  - MySQL chỉ chạy script khởi tạo database/user (đọc từ biến `MYSQL_*`) **trong lần đầu tiên tạo volume**. Nếu bạn đổi `MYSQL_PASSWORD`/`MYSQL_DATABASE` trong `.env` sau khi volume đã tồn tại, container **sẽ không tự áp dụng lại** - phải `docker compose down -v` rồi `up -d` lại.
- **Không có `healthcheck` trong `docker-compose.yml` hiện tại.** Nếu bạn chạy app ngay sau `docker compose up -d`, MySQL có thể chưa sẵn sàng nhận kết nối (thường mất vài giây ở lần khởi tạo đầu). Đợi log hiện `ready for connections` (`docker compose logs -f mysql`) trước khi chạy Spring Boot, đặc biệt ở lần chạy đầu tiên.
- **`MYSQL_ROOT_PASSWORD` chỉ dùng nội bộ cho container**, ứng dụng Spring Boot không dùng tài khoản root để kết nối (dùng `MYSQL_USER`/`MYSQL_PASSWORD`) - không cần và không nên đưa root credentials vào code ứng dụng.
- **Image `mysql:8.4` không bị pin theo digest**, có thể thay đổi patch version theo thời gian. Nếu cần môi trường tái lập chính xác (CI, nhiều máy dev), nên pin theo digest cụ thể.
- **Compose file này chỉ dành cho local/dev**, không có cấu hình backup, replication hay resource limit - không dùng nguyên bản cho production.
- Nếu dùng **Docker Desktop trên Windows/Mac**, lần đầu pull image `mysql:8.4` có thể mất vài phút tuỳ mạng - đừng Ctrl+C giữa chừng khi thấy `docker compose up -d` "đứng im", hãy theo dõi bằng `docker compose logs -f mysql`.

## 5. Chạy ứng dụng Spring Boot

Dự án có 4 profile Spring: `default` (trong `application.yaml`), `local`, `docker`, `prod`. **Dùng profile `local` khi chạy trên máy cá nhân** - profile này đọc đúng theo bộ biến `MYSQL_*` trong `.env` ở bước 3.

### Cách A - dùng Maven Wrapper (khuyến nghị)

Linux/macOS:
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Windows:
```cmd
mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

### Cách B - build jar rồi chạy

```bash
./mvnw clean package -DskipTests
java -jar target/legacy-vault-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

### Cách C - chạy trong IntelliJ IDEA

1. Mở project (File → Open → chọn thư mục `legacy-vault`), để IDE tự resolve Maven.
2. Vào **Run/Debug Configurations** của `LegacyVaultApplication`.
3. Thêm:
   - **Active profiles**: `local`
   - **Environment variables**: dán nội dung `.env` vào (hoặc cài plugin *EnvFile* và trỏ tới file `.env` để IDE tự load).
4. Nhấn Run (▶) hoặc Debug (🐞) như trong ảnh cấu hình dự án.

Ứng dụng mặc định chạy ở cổng **8084** (`server.port: 8084` trong `application.yaml`).

## 6. Kiểm tra ứng dụng đã chạy thành công

```bash
curl http://localhost:8084/actuator/health
```

Kết quả mong đợi:
```json
{"status":"UP"}
```

Log console sẽ hiển thị `Tomcat started on port 8084` và `Started LegacyVaultApplication`.

## 7. Các lệnh thường dùng

| Việc cần làm | Lệnh |
|---|---|
| Chạy test | `./mvnw test` |
| Build không chạy test | `./mvnw clean package -DskipTests` |
| Dừng MySQL container | `docker compose down` |
| Dừng và xoá luôn dữ liệu DB | `docker compose down -v` |
| Xem log MySQL | `docker compose logs -f mysql` |

## 8. Xử lý sự cố thường gặp (Troubleshooting)

- **App không start, báo lỗi thiếu `JWT_SECRET`**: biến này không có giá trị mặc định trong bất kỳ profile nào - bắt buộc phải khai báo trong `.env`.
- **App start nhưng không kết nối được DB / báo sai username-password**: kiểm tra bạn có đang chạy **đúng profile `local`** không. Profile `default` (không truyền `-Dspring-boot.run.profiles`) đọc các biến `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` - khác tên với `.env` hiện tại đang dùng `MYSQL_DATABASE` / `MYSQL_USER` / `MYSQL_PASSWORD`. Nếu không set profile, Spring sẽ rơi về giá trị mặc định cứng trong code, không đọc từ `.env`, dễ gây nhầm lẫn.
- **Lỗi `Port 8084 already in use`**: đổi `server.port` trong `application.yaml` hoặc tắt tiến trình đang chiếm cổng.
- **Lỗi `Port 3306 already in use` khi `docker compose up`**: xem mục 4 - đổi cổng map trong `docker-compose.yml`.
- **Build lỗi vì sai version Java**: chạy `java -version` xác nhận đang dùng JDK 25, không phải JDK 17/21 (bản mặc định trên nhiều máy/IDE).
- **`mvnw: command not found` (Linux/macOS)**: cấp quyền thực thi: `chmod +x mvnw`.

## 9. Cấu trúc thư mục chi tiết

```
legacy-vault/
├── .env                         # biến môi trường thật (không commit)
├── .env.example                 # mẫu biến môi trường (commit)
├── .github/workflows/           # cấu hình GitHub Actions (CI)
├── .mvn/wrapper/                # cấu hình Maven Wrapper
├── mvnw, mvnw.cmd                # script chạy Maven không cần cài Maven
├── docker-compose.yml           # định nghĩa container MySQL cho local/dev
├── pom.xml                      # khai báo dependency, build plugin (Maven)
├── HELP.md                      # tài liệu mặc định do Spring Initializr sinh ra
└── src/
    ├── main/
    │   ├── java/com/ltld/app/legacyvault/
    │   │   ├── LegacyVaultApplication.java   # entry point (@SpringBootApplication)
    │   │   ├── controller/    # REST controller - nhận HTTP request, gọi service, KHÔNG chứa business logic
    │   │   ├── dto/           # Data Transfer Object - request/response body của API, tách biệt với entity để không lộ field nhạy cảm
    │   │   ├── entity/        # JPA entity - map 1-1 với bảng trong MySQL (VD: User.java)
    │   │   ├── enums/         # Hằng số dạng enum dùng chung (VD: KycLevel, UserStatus)
    │   │   ├── exception/     # Exception tự định nghĩa + @ControllerAdvice xử lý lỗi tập trung
    │   │   ├── repository/    # Interface Spring Data JPA - truy vấn DB (extends JpaRepository)
    │   │   ├── security/      # Cấu hình Spring Security, JWT filter, xử lý authentication/authorization
    │   │   ├── service/       # Business logic chính - nơi xử lý nghiệp vụ (Vault, Executor, DMS...)
    │   │   └── utility/       # Hàm tiện ích dùng chung (mã hoá, format ngày giờ, sinh OTP...)
    │   └── resources/
    │       ├── application.yaml          # cấu hình mặc định (profile "default"), luôn được nạp đầu tiên
    │       ├── application-local.yaml     # override khi chạy local (đọc biến MYSQL_* từ .env)
    │       ├── application-docker.yaml    # override khi chính app cũng chạy trong container Docker
    │       └── application-prod.yaml      # override khi deploy production
    └── test/
        └── java/...            # unit test / integration test (hiện đang trống, cần bổ sung)
```

**Ghi chú:** các thư mục `controller/dto/exception/repository/security/service/utility` hiện đang là skeleton (rỗng hoặc gần như rỗng) - đây là kiến trúc layered mặc định cho một service Spring Boot, sẽ được lấp dần khi code từng module (Auth, Vault, Executor...).

## 10. CI

Repo có sẵn GitHub Actions (`.github/workflows/java_ci.yml`) chạy `mvn clean verify` trên JDK 25 cho mỗi push vào `main` và mỗi pull request.
