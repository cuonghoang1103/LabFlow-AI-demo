# Spike — chống đặt trùng (D11, gate G0)

## Câu hỏi
Hai sinh viên bấm "Đặt" **cùng lúc** cho cùng lab, cùng giờ: hệ thống có tạo hai đặt chỗ không?

## Cách sai: kiểm rồi mới ghi
```java
if (repo.existsOverlap(labId, start, end)) throw conflict;   // (1) cả hai request cùng qua đây
repo.save(reservation);                                      // (2) cả hai cùng ghi ⇒ TRÙNG
```

## Cách làm: để PostgreSQL từ chối
```sql
ALTER TABLE reservation_items ADD CONSTRAINT ex_lab_no_overlap
  EXCLUDE USING gist (lab_id WITH =, tstzrange(start_at, end_at, '[)') WITH &&)
  WHERE (active AND lab_id IS NOT NULL);
```
- `'[)'` = nửa mở: 09:00–11:00 và 11:00–13:00 **không** trùng.
- `WHERE active`: đặt chỗ đã huỷ không giữ slot.
- Service vẫn kiểm trước (lỗi thân thiện trong trường hợp thường), nhưng **DB mới là bảo đảm**.
- Khi nhiều request chèn cùng lúc, PostgreSQL có thể trả `23P01 exclusion_violation` **hoặc** `40P01 deadlock`;
  cả hai đều nghĩa là "request này thua" ⇒ service đổi thành `RES_CONFLICT` (409).

## Kết quả đo (Testcontainers, PostgreSQL 16 thật)
`ConcurrentBookingIT` bắn 2, 10, 50 request cùng slot:

| Số request | CONFIRMED | RES_CONFLICT | Dòng active trong DB |
|---|---|---|---|
| 2 | 1 | 1 | 1 |
| 10 | 1 | 9 | 1 |
| 50 | 1 | 49 | 1 |

Chạy lại: `cd backend && mvn verify` (cần Docker).

## Bài học khi làm spike
- Lần đầu 50 request làm lộ lỗi `deadlock detected` chưa được bắt ⇒ đã bắt `PessimisticLockingFailureException`.
- Test đồng thời trên H2 vô nghĩa (H2 không có exclusion constraint) ⇒ test này chỉ chạy trên PostgreSQL.
