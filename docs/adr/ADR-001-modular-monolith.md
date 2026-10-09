# ADR-001 — Modular monolith, package-by-feature, một vỏ API

- Trạng thái: **Accepted** (D6, Sprint 1) · Người quyết định: C1 Leader
- Bối cảnh: 5 vai, 1 học kỳ, cần transaction bao "tạo reservation + audit + outbox" trong một lần commit.

## Quyết định
1. **Một** ứng dụng Spring Boot (modular monolith), **không** microservice.
2. **Package-by-feature**: `auth`, `user`, `catalog`, `availability`, `reservation`, `audit`, `notification`, `settings`,
   `security`, `common`. Mọi thứ của một tính năng (controller, service, repository, entity, dto) nằm chung một package.
3. **Luật một chiều giữa module**: `reservation` → `catalog` được; `catalog` → `reservation` KHÔNG.
   Module khác muốn đọc dữ liệu reservation phải qua `ReservationQueryService` (không đọc thẳng bảng).
4. **Một vỏ response**: `{ "success": true, "data": … }` / `{ "success": false, "error": { "code", "message", "fields" } }`.
5. **Một kiểu lỗi nghiệp vụ**: `BusinessException(ErrorCode)`; `ErrorCode` mang mã + HTTP status + message.

## Hệ quả
- (+) Một transaction, một lần deploy, dễ demo, dễ test.
- (+) Đường cắt module đã có sẵn nếu sau này cần tách.
- (−) Phải tự giữ kỷ luật phụ thuộc (review kiểm "có import ngược chiều không").

## Phương án đã loại
- Microservice: chi phí vận hành, saga — không đáng cho 5 người/1 kỳ.
- Package-by-layer (`controllers/`, `services/`…): sửa một tính năng phải lục 3–4 thư mục.
