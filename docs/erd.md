# ERD v0.1 (D10) — đúng với Flyway V1…V7

```mermaid
erDiagram
    USERS ||--o{ USER_ROLES : has
    ROLES ||--o{ USER_ROLES : "given to"
    USERS ||--o{ USER_TOKENS : owns
    CAMPUSES ||--o{ BUILDINGS : contains
    BUILDINGS ||--o{ LABS : contains
    LABS ||--o{ EQUIPMENT : "located in"
    EQUIPMENT_TYPES ||--o{ EQUIPMENT : "type of"
    LABS ||--o{ OPERATING_HOURS : "override hours"
    LABS ||--o{ BLACKOUTS : "closed during"
    USERS ||--o{ RESERVATIONS : books
    RESERVATIONS ||--|{ RESERVATION_ITEMS : holds
    LABS ||--o{ RESERVATION_ITEMS : "held"
    EQUIPMENT ||--o{ RESERVATION_ITEMS : "held"

    USERS { bigint id PK
        varchar email UK
        varchar password_hash
        varchar full_name
        varchar status "PENDING ACTIVE LOCKED"
        int failed_logins
        timestamptz locked_until }
    ROLES { int id PK
        varchar code UK "STUDENT LECTURER STAFF MANAGER ADMIN" }
    USER_TOKENS { bigint id PK
        varchar purpose "VERIFY RESET REFRESH"
        varchar token_hash UK "SHA-256, never the raw token"
        timestamptz expires_at
        timestamptz used_at "NULL = unused" }
    BUILDINGS { int id PK
        varchar code UK
        varchar status "ACTIVE CLOSED" }
    LABS { int id PK
        varchar code UK
        int floor "0..50"
        int capacity "CHECK > 0, app: 1..200"
        boolean requires_approval "BR-06"
        int version "optimistic lock" }
    EQUIPMENT { bigint id PK
        varchar serial UK
        varchar status "AVAILABLE ON_LOAN MAINTENANCE RETIRED"
        boolean requires_training
        boolean requires_approval }
    OPERATING_HOURS { int id PK
        int lab_id "NULL = default"
        int day_of_week "1=Mon..7=Sun"
        time open_time
        time close_time
        boolean closed }
    BLACKOUTS { bigint id PK
        timestamptz start_at
        timestamptz end_at
        varchar reason }
    HOLIDAYS { date holiday_date PK }
    RESERVATIONS { bigint id PK
        varchar status "PENDING CONFIRMED ..."
        timestamptz start_at
        timestamptz end_at
        varchar idempotency_key UK }
    RESERVATION_ITEMS { bigint id PK
        int lab_id "one of lab/equipment"
        bigint equipment_id
        timestamptz start_at "copied period"
        timestamptz end_at
        boolean active }
    APP_SETTINGS { varchar setting_key PK
        varchar setting_value }
    AUDIT_LOGS { bigint id PK
        bigint actor_id
        varchar entity
        text before_data
        text after_data }
```

## Ràng buộc (invariant)
| Bảng | Ràng buộc | Vì sao |
|---|---|---|
| users | `email` UNIQUE, status ∈ {PENDING, ACTIVE, LOCKED} | BR-01, một email một tài khoản |
| user_tokens | `token_hash` UNIQUE; chỉ lưu hash | lộ DB không lộ token (BR-03) |
| labs | `code` UNIQUE, `capacity > 0`, `floor` 0–50, `@Version` | sửa đồng thời không ghi đè nhau |
| equipment | `serial` UNIQUE; không xoá cứng (RETIRED) | giữ lịch sử mượn/audit |
| operating_hours | (lab_id, day_of_week) UNIQUE | mỗi ngày một dòng mặc định + một dòng riêng mỗi lab |
| blackouts | `start_at < end_at` | khoảng hợp lệ |
| reservations | `idempotency_key` UNIQUE, `start_at < end_at` | bấm hai lần không tạo hai đặt chỗ |
| reservation_items | **EXCLUDE gist (lab_id =, tstzrange [) &&) WHERE active** (PostgreSQL) | chống đặt trùng ngay trong DB |
| audit_logs | chỉ INSERT (entity `@Immutable`) | BR-14 |
