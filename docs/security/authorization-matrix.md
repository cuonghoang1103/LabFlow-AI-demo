# Ma trận phân quyền role × endpoint (D17)

Hai lớp: (1) route rule trong `SecurityConfig` — công khai hay phải đăng nhập; (2) `@PreAuthorize` trên từng method controller.
Gọi sai quyền ⇒ **403 `FORBIDDEN`** từ backend (ẩn nút trên giao diện chỉ là UX). Không có token/hết hạn ⇒ **401 `UNAUTHORIZED`**.

| Endpoint | Guest | STUDENT | LECTURER | STAFF | MANAGER | ADMIN |
|---|---|---|---|---|---|---|
| `POST /auth/register, /login, /refresh, /logout, /verify-email, /forgot-password, /reset-password` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `GET /ping` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `POST /auth/change-password`, `GET/PUT /me` | ❌ 401 | ✅ | ✅ | ✅ | ✅ | ✅ |
| `GET /buildings, /labs, /labs/{id}, /labs/{id}/hours, /labs/{id}/blackouts, /equipment-types, /equipment, /equipment/{id}` | ❌ 401 | ✅ | ✅ | ✅ | ✅ | ✅ |
| `POST /buildings, /labs` · `PUT /labs/{id}` · `PATCH /labs/{id}/status` | ❌ | ❌ 403 | ❌ 403 | ❌ 403 | ✅ | ✅ |
| `POST /equipment` · `PUT /equipment/{id}` | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ |
| `PATCH /equipment/{id}/status` · `PATCH /equipment/status` (hàng loạt) | ❌ | ❌ | ❌ | ✅ | ✅ | ✅ |
| `PUT /operating-hours` · `POST /blackouts` | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ |
| `GET /availability, /availability/equipment` | ❌ 401 | ✅ | ✅ | ✅ | ✅ | ✅ |
| `POST /reservations` · `GET /reservations/mine` | ❌ 401 | ✅ | ✅ | ✅ | ✅ | ✅ |
| `GET /settings` | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ |
| `PUT /settings/{key}` | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| `GET/POST /users`, `PUT /users/{id}`, `PATCH /users/{id}/status` | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |

Kiểm nhanh bằng curl: gọi `POST /api/v1/labs` với token STUDENT phải ra `403`.
Đây là đầu vào cho **integration test Lab 3** (AuthorizationIT — mỗi ô của bảng là một test case).
