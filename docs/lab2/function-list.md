# Danh sách hàm để test — SWT301 Lab 2 (Report 5.1)

> Dự án **LabFlow AI demo (LFD)** — do AI dựng cho SWT301, KHÔNG phải đồ án.
> LOC đo bằng `python3 tools/count_loc.py --all` (từ dòng khai báo tới `}` đóng, bỏ dòng trống và dòng chỉ có chú thích).
> **TC tối thiểu của một hàm = ⌈LOC/10⌉** (chuẩn 100 TC/KLOC); cột TC tối thiểu = tổng theo từng hàm. Mục tiêu nhóm: 6–10 UTCID mỗi hàm.
> Cột "+ helper riêng" = LOC các hàm `private` mà hàm đó gọi (không tính vào LOC của hàm, nhưng test của hàm đó đi qua chúng).
> Exception = `BusinessException` với **mã** trong cột Ngoại lệ (assert bằng `getCode()`); Log message = thông điệp của mã đó
> (`ErrorCode.defaultMessage`, cũng là dòng `WARN [MÃ] message` trong log) hoặc dòng `INFO` thành công ghi trong Javadoc.
> Hàm phụ thuộc thời gian: truyền `Clock.fixed(...)`; hàm gọi repository: mock bằng Mockito (xem `AuthServiceLoginTest`).

## Chia 5 người (5 · 5 · 5 · 5 · 4 hàm)

| Người | Mảng | Hàm | LOC hàm | (+ helper riêng) | TC tối thiểu |
|---|---|---|---|---|---|
| TV1 | Auth A | login, register, verifyEmail, changePassword, logout | 113 | +31 | 14 |
| TV2 | Auth B + chính sách | refresh, forgotPassword, resetPassword, SettingsService.updateSetting, BookingPolicy.checkPeriod | 104 | +12 | 12 |
| TV3 | Người dùng | createUser, updateUser, UserService.changeStatus, updateProfile, LabService.createBuilding | 120 | +6 | 15 |
| TV4 | Lab & thiết bị | createLab, updateLab, createEquipment, EquipmentService.changeStatus, bulkChangeStatus | 69 | +79 | 9 (≥ 15 nếu tính helper) |
| TV5 | Lịch, đặt chỗ | setOperatingHours, addBlackout, checkLabOpen, createReservation | 134 | +14 | 15 |
| **Tổng** | | **24 hàm** | **540** | | **≥ 65 TC** (mục tiêu 150–200) |

Hàm dự phòng (ai xong sớm / cô yêu cầu thêm): `LabService.changeLabStatus` (11), `EquipmentService.updateEquipment` (21),
`AvailabilityService.findAvailableLabs` (12), `AvailabilityService.findAvailableEquipment` (11).

---

## TV1 — Auth A (`auth/AuthService.java`)

| # | Method | Mô tả | Đầu vào | Quy tắc / giá trị biên | Ngoại lệ | LOC |
|---|---|---|---|---|---|---|
| 1 | `login(LoginRequest)` | Đăng nhập, cấp access + refresh token; BR-02 khoá tạm | email, password; user trong DB (mock) | email trim + không phân biệt hoa thường · sai lần thứ **5** liên tiếp ⇒ khoá **15** phút (`failedLogins` 3→4 chưa khoá, 4→khoá) · `lockedUntil` = đúng "bây giờ" ⇒ được đăng nhập · đúng mật khẩu ⇒ `failedLogins` về 0 | AUTH_INVALID_CREDENTIALS, AUTH_ACCOUNT_LOCKED, AUTH_ACCOUNT_TEMP_LOCKED, AUTH_EMAIL_NOT_VERIFIED | 35 |
| 2 | `register(RegisterRequest)` | Đăng ký sinh viên PENDING, gửi link xác minh | email, password, fullName | email ≤ 100, đúng định dạng · tên miền ∈ `fpt.edu.vn, fe.edu.vn` (BR-01) · họ tên 1–100 · mật khẩu 8–64, có chữ **và** số · email trùng không phân biệt hoa thường | AUTH_EMAIL_INVALID, AUTH_EMAIL_DOMAIN_NOT_ALLOWED, AUTH_FULL_NAME_INVALID, AUTH_PASSWORD_WEAK, AUTH_EMAIL_TAKEN | 35 |
| 3 | `verifyEmail(String token)` | Kích hoạt tài khoản bằng token một lần | token | token null/rỗng · không tồn tại · đã dùng · hết hạn (`expiresAt` = bây giờ ⇒ hết hạn) · user đã ACTIVE ⇒ vẫn OK | AUTH_TOKEN_INVALID, AUTH_TOKEN_USED, AUTH_TOKEN_EXPIRED | 12 |
| 4 | `changePassword(userId, ChangePasswordRequest)` | Đổi mật khẩu, thu hồi mọi phiên | userId, current, new | current sai/null · new theo luật 8–64 · new = current ⇒ lỗi · thành công ⇒ `invalidateAll(REFRESH)` được gọi | USER_NOT_FOUND, AUTH_WRONG_PASSWORD, AUTH_PASSWORD_WEAK, AUTH_PASSWORD_SAME_AS_OLD | 19 |
| 5 | `logout(String refreshToken)` | Thu hồi refresh token (idempotent) | token | null/rỗng ⇒ vẫn trả "Logged out" · token lạ ⇒ không lỗi · token đã dùng ⇒ không đổi `usedAt` | — | 12 |

## TV2 — Auth B + chính sách

| # | Method | Mô tả | Đầu vào | Quy tắc / giá trị biên | Ngoại lệ | LOC |
|---|---|---|---|---|---|---|
| 6 | `AuthService.refresh(String token)` | Đổi refresh token lấy cặp mới (xoay vòng) | token | dùng lại token đã dùng ⇒ thu hồi **mọi** refresh token của user · hết hạn đúng bằng bây giờ ⇒ hết hạn · user không ACTIVE | AUTH_TOKEN_INVALID, AUTH_TOKEN_USED, AUTH_TOKEN_EXPIRED, AUTH_ACCOUNT_LOCKED | 20 |
| 7 | `AuthService.forgotPassword(String email)` | Gửi link đặt lại (BR-03) | email | email lạ / PENDING / LOCKED ⇒ cùng thông báo, **không** gửi mail · email có ⇒ vô hiệu link cũ + token 30 phút | — (luôn trả cùng message) | 16 |
| 8 | `AuthService.resetPassword(ResetPasswordRequest)` | Đặt mật khẩu mới bằng token một lần | token, newPassword | mật khẩu yếu ⇒ lỗi **trước** khi dùng token · token đã dùng / hết hạn · thành công ⇒ xoá khoá BR-02, thu hồi phiên | AUTH_PASSWORD_WEAK, AUTH_TOKEN_INVALID, AUTH_TOKEN_USED, AUTH_TOKEN_EXPIRED | 15 |
| 9 | `SettingsService.updateSetting(key, value, actorId)` | Sửa con số BR | key, value | key lạ · value rỗng/> 255 · INT: không phải số, < min, > max (vd. `booking.max-advance-days` 1–60: 0, **1**, **60**, 61) · BOOL chỉ true/false · STRING tuỳ ý | SETTING_NOT_FOUND, SETTING_VALUE_INVALID | 37 |
| 10 | `BookingPolicy.checkPeriod(start, end)` | Luật khoảng đặt (BR-04, BR-05) | start, end (Instant) | null · start ≥ end · không trên lưới 30 phút (giờ VN) · start ≤ bây giờ · start > bây giờ + **14** ngày (đúng 14 ngày ⇒ hợp lệ) | TIME_RANGE_INVALID, TIME_SLOT_MISALIGNED, TIME_IN_PAST, TIME_TOO_FAR_AHEAD | 16 |

## TV3 — Người dùng (`user/UserService.java`) + toà nhà

| # | Method | Mô tả | Đầu vào | Quy tắc / giá trị biên | Ngoại lệ | LOC |
|---|---|---|---|---|---|---|
| 11 | `createUser(CreateUserRequest, actorId)` | Admin tạo tài khoản ACTIVE | email, fullName, password, roles | email định dạng (mọi tên miền) · họ tên 1–100 · mật khẩu 8–64 chữ+số · roles rỗng/null · email trùng | AUTH_EMAIL_INVALID, AUTH_FULL_NAME_INVALID, AUTH_PASSWORD_WEAK, USER_ROLES_REQUIRED, AUTH_EMAIL_TAKEN | 28 |
| 12 | `updateUser(id, UpdateUserRequest, actorId)` | Sửa tên + role | id, fullName, roles | tự bỏ ADMIN của mình · bỏ ADMIN khi chỉ còn **1** admin ACTIVE (`countActiveAdmins` = 1 ⇒ lỗi, = 2 ⇒ được) | USER_NOT_FOUND, AUTH_FULL_NAME_INVALID, USER_ROLES_REQUIRED, USER_CANNOT_CHANGE_SELF, USER_LAST_ADMIN | 21 |
| 13 | `UserService.changeStatus(id, status, actorId)` | Khoá / mở khoá | id, ACTIVE/LOCKED | PENDING ⇒ lỗi · tự khoá mình · trùng trạng thái · khoá admin cuối · khoá ⇒ thu hồi phiên · mở ⇒ xoá bộ đếm BR-02 | VALIDATION_ERROR, USER_NOT_FOUND, USER_CANNOT_CHANGE_SELF, USER_STATUS_UNCHANGED, USER_LAST_ADMIN | 29 |
| 14 | `updateProfile(userId, ProfileRequest)` | Sửa hồ sơ của mình | fullName, phone, avatarUrl | phone `^0\d{9}$` (9 số, **10 số**, 11 số, có chữ) · để trống ⇒ xoá · avatar http(s), ≤ **500** ký tự | USER_NOT_FOUND, AUTH_FULL_NAME_INVALID, USER_PHONE_INVALID, USER_AVATAR_URL_INVALID | 21 |
| 15 | `LabService.createBuilding(BuildingRequest, actorId)` | Tạo toà nhà | code, name | code trim + viết hoa, `^[A-Z0-9-]{2,20}$` (1, **2**, **20**, 21 ký tự) · tên 1–100 · mã trùng | BUILDING_CODE_INVALID, LAB_NAME_INVALID, BUILDING_CODE_TAKEN | 21 |

## TV4 — Lab & thiết bị (`catalog/LabService.java`, `catalog/EquipmentService.java`)

| # | Method | Mô tả | Đầu vào | Quy tắc / giá trị biên | Ngoại lệ | LOC |
|---|---|---|---|---|---|---|
| 16 | `LabService.createLab(LabRequest, actorId)` | Tạo lab; BR-06 | buildingId, code, name, floor, capacity | toà tồn tại + ACTIVE · code như trên · tầng **0–50** · sức chứa **1–200** · **BR-06: capacity > 30 ⇒ requiresApproval** (30 ⇒ false, 31 ⇒ true) · mã trùng | BUILDING_NOT_FOUND, BUILDING_CLOSED, LAB_CODE_INVALID, LAB_NAME_INVALID, LAB_FLOOR_INVALID, LAB_CAPACITY_INVALID, LAB_CODE_TAKEN | 15 (+31) |
| 17 | `LabService.updateLab(id, LabRequest, actorId)` | Sửa lab (optimistic lock) | id + như trên + version | version null/khác ⇒ lỗi · đổi toà ⇒ toà mới phải ACTIVE · mã trùng **lab khác** (trùng chính nó ⇒ OK) | LAB_NOT_FOUND, CONCURRENT_UPDATE + lỗi của create | 20 |
| 18 | `EquipmentService.createEquipment(EquipmentRequest, actorId)` | Tạo thiết bị AVAILABLE | labId, typeId, serial, name, cờ | serial trim + viết hoa `^[A-Z0-9_-]{3,50}$` (2, **3**, **50**, 51) · loại tồn tại · lab tồn tại + ACTIVE · serial trùng | EQUIPMENT_SERIAL_INVALID, EQUIPMENT_NAME_INVALID, EQUIPMENT_TYPE_NOT_FOUND, LAB_NOT_FOUND, LAB_INACTIVE, EQUIPMENT_SERIAL_TAKEN | 14 (+29) |
| 19 | `EquipmentService.changeStatus(id, status, reason, actorId)` | Đổi trạng thái theo bảng chuyển BR-11 | id, status, reason | 4×4 ô chuyển trạng thái · RETIRED là cuối · ON_LOAN không đổi tay · MAINTENANCE/RETIRED cần lý do 1–**255** (256 ⇒ lỗi) | EQUIPMENT_NOT_FOUND, EQUIPMENT_RETIRED, EQUIPMENT_INVALID_TRANSITION, EQUIPMENT_REASON_REQUIRED | 5 (+19) |
| 20 | `EquipmentService.bulkChangeStatus(ids, status, reason, actorId)` | Đổi hàng loạt, tất cả hoặc không | ids, status, reason | ids null/rỗng · **1** · **50** · 51 · trùng id · chứa null · một id không có · một cái RETIRED ⇒ cả lô lỗi | EQUIPMENT_BULK_INVALID, EQUIPMENT_NOT_FOUND + lỗi của #19 | 15 |

## TV5 — Lịch mở cửa, đặt chỗ (`catalog/CalendarService.java`, `reservation/ReservationService.java`)

| # | Method | Mô tả | Đầu vào | Quy tắc / giá trị biên | Ngoại lệ | LOC |
|---|---|---|---|---|---|---|
| 21 | `CalendarService.setOperatingHours(OperatingHoursRequest, actorId)` | Đặt giờ mở cửa một thứ (mặc định hoặc riêng một lab) | labId?, dayOfWeek, open, close, closed | thứ **1–7** (0, 8 ⇒ lỗi) · closed=true ⇒ bỏ qua giờ · open < close · phút 00/30 (07:15 ⇒ lỗi) · lab không tồn tại · đã có dòng ⇒ cập nhật | CALENDAR_DAY_INVALID, CALENDAR_HOURS_INVALID, LAB_NOT_FOUND | 30 |
| 22 | `CalendarService.addBlackout(BlackoutRequest, actorId)` | Thêm khoảng lab không dùng được | labId, startAt, endAt, reason | start < end · end > bây giờ · lý do 1–255 · chồng blackout khác (nửa mở: chạm mép ⇒ **không** chồng) | LAB_NOT_FOUND, BLACKOUT_RANGE_INVALID, BLACKOUT_REASON_REQUIRED, BLACKOUT_OVERLAP | 27 |
| 23 | `CalendarService.checkLabOpen(labId, start, end)` | Lab có mở suốt khoảng này? (BR-04) | labId, start, end | đổi sang giờ VN · phải cùng một ngày · ngày lễ ⇒ đóng · lab ghi đè giờ mặc định · thứ đóng cửa · bắt đầu **đúng 07:00** và kết thúc **đúng 21:00** hợp lệ; 06:30 / 21:30 không · có blackout | TIME_RANGE_INVALID, TIME_OUTSIDE_OPENING_HOURS, TIME_BLACKOUT | 24 |
| 24 | `ReservationService.createReservation(userId, request, idempotencyKey)` | Đặt lab; chống trùng; idempotent | userId, labId, startAt, endAt, purpose, key | key 8–64 (7, **8**, **64**, 65) · cùng key cùng user ⇒ trả kết quả cũ (`replayed=true`), key của user khác ⇒ lỗi · mục đích 1–255 · lab ACTIVE · luật khoảng + giờ mở cửa · trùng giờ (nửa mở) · lab cần duyệt ⇒ PENDING, không ⇒ CONFIRMED · DB từ chối (`DataIntegrityViolationException`) ⇒ RES_CONFLICT | RES_IDEMPOTENCY_KEY_REQUIRED, RES_PURPOSE_REQUIRED, LAB_NOT_FOUND, LAB_INACTIVE, TIME_*, RES_CONFLICT | 53 |

---

### Mẹo theo từng hàm
- Hàm đọc "bây giờ": dựng service bằng tay với `Clock.fixed(Instant.parse("2026-10-12T02:00:00Z"), ZoneOffset.UTC)` (= 09:00 thứ Hai giờ VN).
- `CalendarService`, `BookingPolicy` cần `ZoneId` ⇒ truyền `ZoneId.of("Asia/Ho_Chi_Minh")`.
- `SettingsService` là mock: `when(settings.getInt("booking.slot-minutes")).thenReturn(30)`.
- Kiểm "đã ghi audit": `verify(auditService).record(eq(9L), eq("LAB"), any(), eq("CREATE"), isNull(), any())`.
- Kiểm "không ghi gì khi lỗi": `verify(labRepository, never()).save(any())`.
- `createReservation` nhánh DB từ chối: `when(itemRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("23P01"))`.
