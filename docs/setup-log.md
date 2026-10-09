# Setup log — kiểm toolchain (D1)

Máy dựng dự án (macOS). Mỗi thành viên chạy lại đúng các lệnh này và so kết quả.

```text
$ java -version
openjdk version "21.0.9" 2025-10-21

$ mvn -v
Apache Maven 3.9.11
Java version: 21.0.9          <-- PHẢI là 21. Nếu ra 25: đặt JAVA_HOME về JDK 21

$ docker version
Client 29.8.0 / Server 29.8.0

$ docker compose version
Docker Compose version v5.5.1

$ git --version
git version 2.51.1
```

## Lỗi đã gặp khi dựng
| Lỗi | Nguyên nhân | Cách sửa |
|---|---|---|
| `mvn -v` báo Java 25 dù đã cài JDK 21 | Maven dùng JDK mặc định của máy | `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` (macOS) hoặc chỉnh biến môi trường JAVA_HOME (Windows) |
| Testcontainers: `Could not find a valid Docker environment … Status 400` | Docker Engine 29 không nhận API version cũ của Testcontainers 1.21 | `backend/src/test/resources/docker-java.properties`: `api.version=1.44` (đã có trong repo) |
| Cổng 5432 đã bị chiếm | Máy đã cài PostgreSQL | docker-compose map ra cổng **5433** |
