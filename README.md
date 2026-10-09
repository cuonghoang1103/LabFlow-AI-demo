# LabFlow AI — demo (SWT301)

> **Dự án demo do AI dựng cho môn SWT301, KHÔNG phải đồ án LabFlow; không chép sang đồ án.**

Hệ thống quản lý phòng lab & thiết bị — **Spring Boot 3.5 · Java 21 · PostgreSQL 16 · Flyway · JWT/RBAC** — dựng theo đúng
kế hoạch đồ án LabFlow AI v2 (mã Req S01…S47, BR-01…14, gate, vai C1–C5) để nhóm SWT301 có một dự án thật làm đối tượng
kiểm thử: **Lab 2 unit test (5.1) → Lab 3 integration (5.2) → Lab 4/5 system test (5.3)**.

Bản hiện tại: **v0.1-lab2** = backend tuần 1–4 của kế hoạch (identity + RBAC, campus/lab, thiết bị, lịch mở cửa,
availability, spike chống đặt trùng). Chưa có frontend (CR-2).

## 1. Cần cài
| Công cụ | Phiên bản | Kiểm |
|---|---|---|
| JDK | **21** (Temurin) | `java -version` |
| Maven | 3.9+ | `mvn -v` → dòng *Java version* phải là **21** |
| Docker Desktop | tuỳ chọn (Postgres + integration test) | `docker version` |
| IDE | IntelliJ IDEA / VS Code + Extension Pack for Java (+ REST Client) | |

> `mvn -v` báo Java 25/17? Đặt `JAVA_HOME` về JDK 21 — macOS: `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`;
> Windows: System Properties → Environment Variables → `JAVA_HOME` = thư mục JDK 21.

## 2. Lấy code
```bash
git clone <link repo nhóm trưởng gửi>   # repo private, cần được mời làm collaborator
cd LabFlow-AI-demo
```

## 3. Chạy test
```bash
cd backend
mvn test        # unit test + context-load trên H2 (không cần Docker) — phải BUILD SUCCESS
mvn verify      # + integration test ConcurrentBookingIT trên PostgreSQL thật (cần Docker), + JaCoCo
mvn -Dtest=AuthServiceLoginTest test                                        # một file
mvn -Dtest=AuthServiceLoginTest#utcid04_fifthFailure_locksFifteenMinutes test # một test
```
Báo cáo coverage: `backend/target/site/jacoco/index.html` (sau `mvn verify`).

## 4. Chạy ứng dụng
**Cách A — không cần Docker (H2 trong bộ nhớ, mất dữ liệu khi tắt):**
```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=h2
```
**Cách B — PostgreSQL bằng Docker:**
```bash
docker compose up -d            # ở thư mục gốc repo; Postgres ở cổng 5433
cd backend && mvn spring-boot:run
```
Kiểm: `curl http://localhost:8080/api/v1/ping` · Swagger UI: http://localhost:8080/swagger-ui.html · H2 console (cách A): http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:labflow`, user `sa`).

## 5. Tài khoản demo (mật khẩu đều là `Labflow123`)
| Email | Vai |
|---|---|
| admin@fpt.edu.vn | ADMIN |
| manager@fpt.edu.vn | MANAGER (Lab Manager) |
| staff@fpt.edu.vn | STAFF (Lab Staff) |
| lecturer@fe.edu.vn | LECTURER |
| student1@fpt.edu.vn, student2@fpt.edu.vn | STUDENT |
| pending@fpt.edu.vn | STUDENT chưa xác minh email |

Dữ liệu mẫu (Flyway V7): 2 toà, 4 lab (AL-301 đúng 30 chỗ — biên BR-06; BE-201 60 chỗ cần duyệt; BE-105 đã đóng),
8 thiết bị (có cái MAINTENANCE, RETIRED), giờ mở cửa T2–T7 07:00–21:00, CN đóng, BE-201 thứ Bảy đóng 12:00, ngày lễ.

## 6. Gọi API
Mở `http/labflow-demo.http` trong VS Code (extension **REST Client**) — đủ mọi endpoint, có sẵn bước đăng nhập lấy token.
Email xác minh / đặt lại mật khẩu được **in ra log** của app (vì `MAIL_ENABLED=false`); bật MailTrap bằng biến môi trường
`MAIL_ENABLED=true MAIL_USER=… MAIL_PASSWORD=…`.

## 7. Đọc code ở đâu
```
backend/src/main/java/vn/swt301/labflowdemo/
├── common/        ApiResponse, ErrorCode (mọi mã lỗi), BusinessException, GlobalExceptionHandler, Clock
├── security/      JWT, SecurityConfig (ai được gọi gì), rate limit
├── auth/          register, verify email, login, refresh, logout, đổi/quên/đặt lại mật khẩu
├── user/          user, role, token; quản trị user; hồ sơ của tôi
├── settings/      các con số BR (cấu hình được)
├── catalog/       toà, lab, thiết bị, giờ mở cửa, blackout, luật khoảng đặt
├── availability/  lab/thiết bị còn trống
├── reservation/   đặt lab (spike chống đặt trùng)
├── audit/         nhật ký chỉ ghi thêm
└── notification/  gửi email
```
Luồng một request: **Controller → Service → Repository → DB**. Mọi luật nằm ở **Service** — đó là chỗ để unit test.
Tài liệu: [kiến trúc](docs/adr/ADR-001-modular-monolith.md) · [quy ước code](docs/conventions.md) · [ERD](docs/erd.md) ·
[API v1](docs/api-v1.md) · [phân quyền](docs/security/authorization-matrix.md) · [spike chống trùng](docs/spikes/concurrency.md) ·
[**danh sách hàm để test (Lab 2)**](docs/lab2/function-list.md) · [charter + DoD](docs/charter.md).

## 8. Đo LOC (cho sheet 5.1)
```bash
python3 tools/count_loc.py          # public methods của mọi @Service
python3 tools/count_loc.py --all    # cả hàm private
```

## 9. Quy ước nhóm
Nhánh `feature/lfd-<số>-<tên>`, commit `LFD-<số> <việc>` (CT Work tự gắn commit vào thẻ), trưởng nhóm merge vào `main`.
Lỗi tìm được khi test: tạo thẻ **Bug** trên CT Work project **LFD** (tiêu đề `[UT] <hàm> UTCIDxx: …`), lấy khoá thẻ làm Defect ID.
