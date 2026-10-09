# Quy ước code (D6)

## Luồng một request
`Controller` (HTTP ↔ Java, `@Valid`, `@PreAuthorize`) → `Service` (luật nghiệp vụ, `@Transactional`, audit, log)
→ `Repository` (Spring Data JPA) → PostgreSQL. **Controller không chứa luật nghiệp vụ.**

## Service
- Mỗi method public làm **một việc**, kiểm đầu vào rõ ràng ở đầu hàm, rồi mới ghi.
- Vi phạm luật ⇒ `throw new BusinessException(ErrorCode.XXX)`; không trả `null`/`false` để báo lỗi.
- "Bây giờ" luôn là `Instant.now(clock)` — **không** `Instant.now()` trần (để test cố định được thời gian).
- Con số nghiệp vụ (BR) đọc từ `SettingsService`, không viết cứng.
- Javadoc tiếng Anh ngắn: làm gì, `@throws` liệt kê mã lỗi. Chú thích tiếng Việt ở chỗ dễ hiểu sai.

## Log
- Thành công: `log.info("<Việc> <kết quả>: id={}, …, by={}")` — ví dụ `Lab created: id=5, code=AL-401, …`.
- Lỗi nghiệp vụ: `GlobalExceptionHandler` ghi **một** dòng `WARN [ERROR_CODE] message`.
- Không bao giờ log mật khẩu, token, secret.

## Validation hai lớp
- Lớp 1 (controller): Bean Validation trên DTO — bắt buộc, độ dài tối đa, định dạng ⇒ 400 `VALIDATION_ERROR`.
- Lớp 2 (service): luật nghiệp vụ (trùng, trạng thái, biên giá trị) ⇒ mã lỗi riêng. Service vẫn tự kiểm null/blank
  vì có thể được gọi từ nơi khác controller (và unit test gọi thẳng service).

## Git
- Nhánh `feature/lfd-<số>-<tên-ngắn>`; commit `LFD-<số> <động từ> <việc>` (CT Work tự gắn commit vào thẻ).
- Chỉ C1 (maintainer) merge vào `main`, `--no-ff`, sau khi review. Không `push --force` lên main.

## Test
- Unit test: `src/test/java/.../<module>/<Class><Method>Test.java`, một `@Test` = một UTCID,
  tên `utcidNN_<tình huống>_<kết quả>`, mẫu AAA, mock mọi phụ thuộc (xem `AuthServiceLoginTest`).
- Integration test chạy PostgreSQL thật: tên `*IT.java`, chạy bằng `mvn verify`.
