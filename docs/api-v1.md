# API v1 — hợp đồng đã khoá cho v0.1-lab2 (D27)

- Base URL: `http://localhost:8080/api/v1` · Swagger UI: `http://localhost:8080/swagger-ui.html` · OpenAPI JSON: `/v3/api-docs`
- Vỏ response: `{ "success": true, "data": … }` hoặc `{ "success": false, "error": { "code", "message", "fields"? } }`
- Thời gian: ISO-8601 UTC (`2026-10-13T02:00:00Z` = 09:00 giờ Việt Nam) — ADR-002
- Danh sách: `page` (từ 0), `size` (≤ 100), `sort=field,asc|desc`, cộng bộ lọc riêng
- Mã HTTP: 200/201 thành công · 400 dữ liệu sai · 401 chưa đăng nhập/hết hạn · 403 sai vai · 404 không có · 409 xung đột · 423 tài khoản bị khoá · 429 quá nhiều request

| Method | Path | Ai | Service method | Lỗi nghiệp vụ chính |
|---|---|---|---|---|
| GET | `/ping` | mọi người | — | — |
| POST | `/auth/register` | Guest | `AuthService.register` | AUTH_EMAIL_INVALID, AUTH_EMAIL_DOMAIN_NOT_ALLOWED, AUTH_FULL_NAME_INVALID, AUTH_PASSWORD_WEAK, AUTH_EMAIL_TAKEN |
| POST | `/auth/verify-email` | Guest | `AuthService.verifyEmail` | AUTH_TOKEN_INVALID/USED/EXPIRED |
| POST | `/auth/login` | mọi người | `AuthService.login` | AUTH_INVALID_CREDENTIALS, AUTH_ACCOUNT_LOCKED, AUTH_ACCOUNT_TEMP_LOCKED, AUTH_EMAIL_NOT_VERIFIED |
| POST | `/auth/refresh` | mọi người | `AuthService.refresh` | AUTH_TOKEN_INVALID/USED/EXPIRED, AUTH_ACCOUNT_LOCKED |
| POST | `/auth/logout` | mọi người | `AuthService.logout` | — (idempotent) |
| POST | `/auth/change-password` | đã đăng nhập | `AuthService.changePassword` | AUTH_WRONG_PASSWORD, AUTH_PASSWORD_WEAK, AUTH_PASSWORD_SAME_AS_OLD |
| POST | `/auth/forgot-password` | Guest | `AuthService.forgotPassword` | — (luôn cùng thông báo) |
| POST | `/auth/reset-password` | Guest | `AuthService.resetPassword` | AUTH_PASSWORD_WEAK, AUTH_TOKEN_* |
| GET/PUT | `/me` | đã đăng nhập | `UserService.getProfile/updateProfile` | AUTH_FULL_NAME_INVALID, USER_PHONE_INVALID, USER_AVATAR_URL_INVALID |
| GET | `/users?q&role&status&page&size&sort` | ADMIN | `UserService.searchUsers` | — |
| GET/POST | `/users`, `/users/{id}` | ADMIN | `UserService.getUser/createUser` | AUTH_EMAIL_*, AUTH_PASSWORD_WEAK, USER_ROLES_REQUIRED |
| PUT | `/users/{id}` | ADMIN | `UserService.updateUser` | USER_CANNOT_CHANGE_SELF, USER_LAST_ADMIN |
| PATCH | `/users/{id}/status` | ADMIN | `UserService.changeStatus` | USER_CANNOT_CHANGE_SELF, USER_STATUS_UNCHANGED, USER_LAST_ADMIN |
| GET / PUT | `/settings`, `/settings/{key}` | MANAGER, ADMIN / ADMIN | `SettingsService.updateSetting` | SETTING_NOT_FOUND, SETTING_VALUE_INVALID |
| GET/POST | `/buildings` | đã đăng nhập / MANAGER, ADMIN | `LabService.createBuilding` | BUILDING_CODE_INVALID, BUILDING_CODE_TAKEN |
| GET | `/labs?q&buildingId&status&minCapacity&page&size&sort`, `/labs/{id}` | đã đăng nhập | `LabService.searchLabs/getLab` | LAB_NOT_FOUND |
| POST | `/labs` | MANAGER, ADMIN | `LabService.createLab` | BUILDING_NOT_FOUND/CLOSED, LAB_CODE_INVALID/TAKEN, LAB_NAME_INVALID, LAB_FLOOR_INVALID, LAB_CAPACITY_INVALID |
| PUT | `/labs/{id}` | MANAGER, ADMIN | `LabService.updateLab` | như create + CONCURRENT_UPDATE |
| PATCH | `/labs/{id}/status` | MANAGER, ADMIN | `LabService.changeLabStatus` | LAB_STATUS_UNCHANGED |
| GET | `/equipment-types`, `/equipment?q&labId&typeId&status…`, `/equipment/{id}` | đã đăng nhập | `EquipmentService.searchEquipment/getEquipment` | EQUIPMENT_NOT_FOUND |
| POST / PUT | `/equipment`, `/equipment/{id}` | MANAGER, ADMIN | `EquipmentService.createEquipment/updateEquipment` | EQUIPMENT_SERIAL_INVALID/TAKEN, EQUIPMENT_NAME_INVALID, EQUIPMENT_TYPE_NOT_FOUND, LAB_INACTIVE, EQUIPMENT_RETIRED |
| PATCH | `/equipment/{id}/status` | STAFF, MANAGER, ADMIN | `EquipmentService.changeStatus` | EQUIPMENT_RETIRED, EQUIPMENT_INVALID_TRANSITION, EQUIPMENT_REASON_REQUIRED |
| PATCH | `/equipment/status` (hàng loạt) | STAFF, MANAGER, ADMIN | `EquipmentService.bulkChangeStatus` | EQUIPMENT_BULK_INVALID + lỗi đơn lẻ |
| GET | `/labs/{id}/hours`, `/labs/{id}/blackouts` | đã đăng nhập | `CalendarService.hoursFor/upcomingBlackouts` | — |
| PUT | `/operating-hours` | MANAGER, ADMIN | `CalendarService.setOperatingHours` | CALENDAR_DAY_INVALID, CALENDAR_HOURS_INVALID, LAB_NOT_FOUND |
| POST | `/blackouts` | MANAGER, ADMIN | `CalendarService.addBlackout` | BLACKOUT_RANGE_INVALID, BLACKOUT_REASON_REQUIRED, BLACKOUT_OVERLAP |
| GET | `/availability?from&to&capacity&buildingId` | đã đăng nhập | `AvailabilityService.findAvailableLabs` | TIME_* , VALIDATION_ERROR |
| GET | `/availability/equipment?from&to&typeId` | đã đăng nhập | `AvailabilityService.findAvailableEquipment` | TIME_*, EQUIPMENT_TYPE_NOT_FOUND |
| POST | `/reservations` + header `Idempotency-Key` | đã đăng nhập | `ReservationService.createReservation` | RES_IDEMPOTENCY_KEY_REQUIRED, RES_PURPOSE_REQUIRED, LAB_*, TIME_*, RES_CONFLICT (409) |
| GET | `/reservations/mine` | đã đăng nhập | `ReservationService.myReservations` | — |

## Index đã có (vì sao)
| Index | Phục vụ truy vấn |
|---|---|
| `idx_items_lab_time (lab_id, start_at, end_at)` + exclusion gist | kiểm trùng giờ, availability |
| `idx_blackouts_lab (lab_id, start_at)` | blackout giao khoảng |
| `idx_equipment_lab (lab_id, status)` | lọc thiết bị theo lab/trạng thái |
| `idx_user_tokens_user (user_id, purpose)` | thu hồi token theo user |
| `idx_audit_entity (entity, entity_id)` | xem lịch sử một đối tượng |
