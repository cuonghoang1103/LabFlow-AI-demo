# ADR-002 — Dữ liệu thời gian (D23, D27)

- Trạng thái: **Accepted** · Người đề xuất: C2 · Người duyệt: C1

## Quyết định
1. Mọi thời điểm (đặt chỗ, blackout, token, audit) là `java.time.Instant`, lưu cột `TIMESTAMP WITH TIME ZONE` (UTC).
2. Giờ mở cửa là **giờ địa phương Việt Nam** (`LocalTime` + `app.zone = Asia/Ho_Chi_Minh`). So sánh giờ mở cửa luôn đổi
   `Instant` sang giờ Việt Nam trước (`instant.atZone(businessZone)`).
3. Khoảng thời gian là **nửa mở** `[start, end)`. Hai khoảng trùng khi `s1 < e2 AND s2 < e1`.
4. Bắt đầu đúng giờ mở cửa và kết thúc đúng giờ đóng cửa là **hợp lệ** (biên đóng hai đầu của khung giờ mở cửa).
5. "Bây giờ" lấy từ bean `Clock` ⇒ test truyền `Clock.fixed(...)`.
6. **Không** đặt `hibernate.jdbc.time_zone`: nó dịch cả cột `TIME` theo múi giờ JVM (đã gặp lỗi: giờ mở cửa 07:00 bị đọc
   thành 00:00 trên PostgreSQL ⇒ mọi đặt chỗ báo "ngoài giờ mở cửa").

## Hệ quả
- API nhận/trả ISO-8601 UTC, ví dụ `2026-10-13T02:00:00Z` = 09:00 giờ Việt Nam.
- Client hiển thị theo giờ Việt Nam.
