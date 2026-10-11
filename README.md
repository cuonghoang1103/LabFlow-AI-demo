# LabFlow AI — demo (SWT301)

> **Dự án demo do AI dựng cho môn SWT301, KHÔNG phải đồ án LabFlow; không chép sang đồ án.**

Hệ thống quản lý phòng lab & thiết bị — **Spring Boot 3.5 · Java 21 · PostgreSQL 16 · Flyway · JWT/RBAC**. Dùng làm
dự án thật để nhóm SWT301 **viết unit test (Lab 2 — Báo cáo 5.1)**. Mỗi người viết test cho **vài hàm được giao** (không
phải cả project). Bản hiện tại **v0.1-lab2** = backend; chưa có frontend.

**Bạn chỉ cần làm được 1 việc:** mở project → chạy `mvnw test` ra **BUILD SUCCESS** → viết test cho hàm của mình → chạy lại.

---

## ⚡ CÁCH NHANH NHẤT — nhờ AI Code tự cài + hướng dẫn (khuyên dùng)

Ai cũng đã có **app desktop CuongThai** (để dùng CT Work) → trong đó có **AI Code**. Không cần tự mò cài đặt:

1. **Mở thư mục repo này trong AI Code** (app desktop) — hoặc OpenCode CLI, hoặc terminal trong IntelliJ.
2. **Dán đúng câu này** cho AI Code (đổi `<tên hàm của tôi>` theo phần được giao):
   ```
   Đọc file README.md, làm theo mục "CHO AI CODE". Cài đặt máy mình (Windows) để
   chạy được: cd backend && .\mvnw.cmd test  → ra BUILD SUCCESS.
   Sau đó đọc docs/lab2/function-list.md, tìm hàm <tên hàm của tôi>, và hướng dẫn
   tôi viết unit test cho nó theo mẫu AuthServiceLoginTest (mỗi UTCID = 1 @Test).
   ```
3. AI Code sẽ: kiểm JDK 21 → **tự cài nếu thiếu** → chạy test → báo xanh → rồi hướng dẫn bạn viết phần của mình.

> Không biết mình làm hàm nào? Mở **CT Work → Board → thẻ "Thành viên" của mình**, hoặc hỏi nhóm trưởng.

---

## 🤖 CHO AI CODE (trợ lý cài đặt) — làm ĐÚNG các bước sau

> Agent đọc mục này rồi thực thi tuần tự trên máy người dùng. Dừng và báo người dùng nếu một bước thất bại 2 lần.

**Bước 0 — Xác định HĐH** (`Windows` hay `macOS`). Các lệnh dưới đây có cả hai.

**Bước 1 — JDK 21.** Chạy `java -version`.
- Nếu KHÔNG ra `openjdk version "21...`:
  - **Windows:** `winget install EclipseAdoptium.Temurin.21.JDK` (không có winget → tải `.msi` ở https://adoptium.net/temurin/releases/?version=21, khi cài bật *Set JAVA_HOME variable*). Mở terminal MỚI.
  - **macOS:** `brew install --cask temurin@21`
- Vẫn sai sau khi cài → đặt `JAVA_HOME`:
  - **Windows (PowerShell):** `Get-ChildItem "C:\Program Files\Eclipse Adoptium"` xem tên thư mục, rồi `[Environment]::SetEnvironmentVariable("JAVA_HOME","C:\Program Files\Eclipse Adoptium\<thư mục jdk-21...>","User")` → mở terminal mới.
  - **macOS:** `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`

**Bước 2 — Git.** Chạy `git --version`. Thiếu → Windows `winget install Git.Git` · macOS `brew install git`.

**Bước 3 — Chạy test** (thư mục `backend`, KHÔNG cần cài Maven — repo có sẵn `mvnw`):
- **Windows:** `cd backend` rồi `.\mvnw.cmd test`
- **macOS:** `cd backend` rồi `./mvnw test`
- Lần đầu tải thư viện mất vài phút. **Phải ra `BUILD SUCCESS` và `Tests run: 5`**.
- Lỗi `JAVA_HOME ... invalid` / `release version 21 not supported` → quay lại Bước 1 đặt JAVA_HOME về JDK 21.
- Lỗi mạng `Could not transfer artifact` → mạng chặn Maven Central, thử lại (hoặc phát 4G).

**Bước 4 — Báo người dùng:** "Đã chạy được test, N/5 xanh." Chụp/ghi lại kết quả.

**Bước 5 — Hướng dẫn phần của người dùng:**
- Đọc `docs/lab2/function-list.md` → tìm **hàm được giao** (người dùng cho biết, hoặc hỏi).
- Mẫu để viết theo: `backend/src/test/java/vn/swt301/labflowdemo/auth/AuthServiceLoginTest.java` (đã có UTCID01–04, xanh).
- **Quy tắc:** mỗi UTCID = một `@Test`, tên `utcidNN_<tình huống>_<kết quả>`, mẫu **Arrange–Act–Assert**; mock mọi phụ thuộc bằng `@Mock`; nếu service có `Clock` thì dựng service bằng tay với `Clock.fixed(...)`, không thì dùng `@InjectMocks`.
- **TUYỆT ĐỐI không sửa code trong `src/main/`** — chỉ THÊM file trong `src/test/`.
- File test đặt **cùng gói** với class được test, tên `<Class><Method>Test.java`.

**Bước 6 — Chạy lại test của người dùng** (`mvnw test -Dtest=<Lớp>`) và báo Passed/Failed. Failed vì assert sai = test TỐT (tìm ra lỗi), giữ nguyên; Failed vì thiếu mock/biên dịch = sửa test.

---

## 🖐️ Setup THỦ CÔNG từ số 0 — Windows (máy mới tinh)

Dành cho ai muốn tự làm. Làm lần lượt, mỗi bước có cách **tự kiểm**.

### 1. Cài 3 phần mềm
Mở **PowerShell** (Start → gõ `powershell` → Enter), chạy từng dòng:
```powershell
winget install EclipseAdoptium.Temurin.21.JDK
winget install Git.Git
winget install JetBrains.IntelliJIDEA.Community
```
Không có `winget`? Tải tay: [JDK 21](https://adoptium.net/temurin/releases/?version=21) (Windows x64 `.msi`, cài nhớ bật **Set JAVA_HOME variable**) · [Git](https://git-scm.com/download/win) · [IntelliJ Community](https://www.jetbrains.com/idea/download/).

**Đóng PowerShell, mở cửa sổ MỚI**, kiểm:
```powershell
java -version        # phải có: openjdk version "21...
git --version
```

### 2. Lấy code (repo CÔNG KHAI — không cần đăng nhập)
```powershell
mkdir C:\code
cd C:\code
git clone https://github.com/cuonghoang1103/LabFlow-AI-demo.git
```
> ⚠️ Để ở `C:\code` — **đường dẫn KHÔNG dấu tiếng Việt, KHÔNG dấu cách** (tránh lỗi build).

### 3. Chạy test lần đầu
```powershell
cd C:\code\LabFlow-AI-demo\backend
.\mvnw.cmd test
```
Lần đầu tải Maven + thư viện (vài phút). **Tự kiểm:** cuối màn hình có `Tests run: 5, Failures: 0` và `BUILD SUCCESS`. **Chụp màn hình gửi nhóm.**

> `mvnw : is not recognized` → thiếu `.\` ở đầu, gõ `.\mvnw.cmd test`.
> `JAVA_HOME is set to an invalid directory` → JAVA_HOME trỏ JDK cũ; đặt lại về JDK 21 (xem mục AI Code Bước 1), mở terminal mới.

### 4. Mở trong IntelliJ
1. **File → Open** → chọn thư mục **`C:\code\LabFlow-AI-demo\backend`** (chỗ có `pom.xml`) → **Open as Project** → **Trust Project**.
2. Chờ thanh dưới chạy xong (*Indexing / Resolving Maven dependencies*).
3. Báo "Project JDK is not defined" → **File → Project Structure → SDK → chọn 21**.
4. Mở `src/test/java/vn/swt301/labflowdemo/auth/AuthServiceLoginTest.java` → bấm **▶** cạnh tên class → **Run** → thấy 4 test xanh ✓.

**Bảng lỗi Windows hay gặp + cách sửa:** xem đầy đủ ở CT Work → Docs → *"Cài đặt & chạy dự án"* (`cuongthai.com/work/swt301/LAB/docs/8`).

---

## 🍎 Setup — macOS (nhóm trưởng)
Cần [Homebrew](https://brew.sh).
```bash
brew install --cask temurin@21 && brew install git && brew install --cask intellij-idea-ce
mkdir -p ~/code && cd ~/code
git clone https://github.com/cuonghoang1103/LabFlow-AI-demo.git
cd LabFlow-AI-demo/backend
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # nếu java -version không ra 21
./mvnw test                                        # -> BUILD SUCCESS, Tests run: 5
```

---

## Chạy test (các kiểu)
```bash
# Windows thay ./mvnw bằng .\mvnw.cmd và bọc -D... trong ngoặc kép
./mvnw test                                   # TẤT CẢ test (unit + context-load H2) — BUILD SUCCESS
./mvnw test -Dtest=AuthServiceLoginTest       # một class (của mình — nhanh)
./mvnw test -Dtest=AuthServiceLoginTest#utcid04_fifthFailure_locksFifteenMinutes   # một test
./mvnw verify                                 # + integration test (cần Docker) + coverage JaCoCo
```
Coverage: `backend/target/site/jacoco/index.html` (sau `verify`). **Lab 2 chỉ cần `test` (H2, không cần Docker).**

## Chạy ứng dụng (chỉ để hiểu nghiệp vụ — unit test KHÔNG cần)
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2   # H2 trong bộ nhớ, không cần Docker
```
Kiểm: http://localhost:8080/api/v1/ping · Swagger: http://localhost:8080/swagger-ui.html · H2 console: http://localhost:8080/h2-console (JDBC `jdbc:h2:mem:labflow`, user `sa`). Dừng: **Ctrl+C**.

## Tài khoản demo (mật khẩu đều `Labflow123`)
| Email | Vai |
|---|---|
| admin@fpt.edu.vn | ADMIN |
| manager@fpt.edu.vn | MANAGER |
| staff@fpt.edu.vn | STAFF |
| lecturer@fe.edu.vn | LECTURER |
| student1@fpt.edu.vn, student2@fpt.edu.vn | STUDENT |
| pending@fpt.edu.vn | STUDENT chưa xác minh email |

Dữ liệu mẫu (Flyway V7): 2 toà, 4 lab (AL-301 đúng 30 chỗ — biên BR-06; BE-201 60 chỗ cần duyệt; BE-105 đã đóng),
8 thiết bị, giờ mở T2–T7 07:00–21:00, CN đóng, ngày lễ.

## Gọi API
Mở `http/labflow-demo.http` trong VS Code (extension **REST Client**). Email xác minh/đặt lại mật khẩu được **in ra log** (vì `MAIL_ENABLED=false`).

## Đọc code ở đâu
```
backend/src/main/java/vn/swt301/labflowdemo/
├── common/        ErrorCode (mọi mã lỗi), BusinessException, Clock
├── auth/          AuthService: login, register, verify, refresh, logout, đổi/quên/đặt lại mật khẩu
├── user/          UserService: tạo/sửa/khoá user, hồ sơ
├── settings/      SettingsService: các con số BR (cấu hình)
├── catalog/       LabService, EquipmentService, CalendarService: toà, lab, thiết bị, giờ mở, blackout
├── reservation/   ReservationService: đặt lab (chống đặt trùng)
└── availability/ · audit/ · notification/ · security/
```
**Luồng:** Controller → **Service** → Repository → DB. **Mọi luật ở Service — đó là chỗ unit test.**
Tài liệu: [kiến trúc](docs/adr/ADR-001-modular-monolith.md) · [quy ước](docs/conventions.md) · [ERD](docs/erd.md) · [API v1](docs/api-v1.md) · [phân quyền](docs/security/authorization-matrix.md) · [**danh sách hàm để test (Lab 2)**](docs/lab2/function-list.md).

## Đo LOC (cho sheet 5.1)
```bash
python3 tools/count_loc.py          # public methods của mọi @Service
python3 tools/count_loc.py --all    # cả hàm private
```

## Viết test của mình + nộp
1. **Thiết kế** ma trận test trên **CT Work → Tests → Unit tests (5.1)** (hàm của mình).
2. **Viết** file JUnit theo mẫu `AuthServiceLoginTest` (mỗi UTCID = 1 `@Test`) — **chỉ thêm trong `src/test/`**.
3. **Chạy** `./mvnw test -Dtest=<Lớp>` → điền Passed/Failed vào CT Work.
4. **Nộp:** Pull Request từ bản **Fork** của mình vào nhánh `lab2-unit-test` (hướng dẫn: CT Work Docs /8), hoặc gửi file `.java` cho nhóm trưởng.

Hướng dẫn đầy đủ (kiến thức + ví dụ): **CT Work → Docs** (`cuongthai.com/work/swt301/LAB`) — đọc **/11 (bắt đầu)**, **/2 (hiểu)**, **/4 (ví dụ login)**, **/12 (quy trình 5 bước)**.

## Quy ước nhóm
Nhánh `test/<tên>` từ `lab2-unit-test`; commit `Lab2 TV<n>: <việc>`. Lỗi tìm được: tạo thẻ **Bug** trên CT Work (tiêu đề `[UT] <hàm> UTCIDxx: …`), ghi mã thẻ vào ô Defect ID.
