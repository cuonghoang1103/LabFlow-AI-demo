# Threat review — Authentication (D20, STRIDE ngắn)

| STRIDE | Mối nguy | Cách chặn trong code | Ở đâu |
|---|---|---|---|
| Spoofing | Đoán mật khẩu | BR-02: sai 5 lần liên tiếp ⇒ khoá 15 phút; rate limit 20 request/phút/IP cho login, register, forgot/reset | `AuthService.login`, `RateLimitFilter` |
| Spoofing | Dò email có tài khoản | Login trả cùng một lỗi cho "không có email" và "sai mật khẩu"; forgot-password luôn trả cùng thông báo | `AuthService` |
| Tampering | Sửa JWT để đổi role | JWT ký HS256, secret ≥ 32 byte từ env `JWT_SECRET`; token sửa một ký tự ⇒ 401 | `JwtConfig` |
| Repudiation | "Tôi không làm" | Audit log append-only cho đăng ký, đăng nhập, khoá, đổi mật khẩu, mọi thay đổi quản trị | `AuditService` |
| Information disclosure | Lộ DB ⇒ lộ token | Chỉ lưu SHA-256 của token verify/reset/refresh; mật khẩu BCrypt | `SecureTokens`, `PasswordEncoder` |
| Information disclosure | Lộ stack trace | `server.error.include-message: never`; lỗi lạ ⇒ `INTERNAL_ERROR` chung | `GlobalExceptionHandler` |
| Denial of service | Spam đăng ký / quên mật khẩu | Rate limit theo IP | `RateLimitFilter` |
| Elevation of privilege | STUDENT gọi API ADMIN | Route rule + `@PreAuthorize`; ma trận ở `authorization-matrix.md` | controllers |
| Elevation of privilege | Refresh token bị đánh cắp | Xoay vòng: dùng lại token đã dùng ⇒ thu hồi **mọi** refresh token của user; đổi/reset mật khẩu, bị khoá ⇒ thu hồi | `AuthService.refresh` |

## Secret
- Không commit secret thật. `application.yml` chỉ có secret **dev** và đọc env `JWT_SECRET`, `DB_PASSWORD`, `MAIL_PASSWORD`.

## Còn mở (ghi vào RAID)
- Rate limit nằm trong bộ nhớ ⇒ chạy nhiều instance cần Redis/bucket chung.
- Access token chưa thu hồi được trước hạn (15 phút) — chấp nhận, vì ngắn.
