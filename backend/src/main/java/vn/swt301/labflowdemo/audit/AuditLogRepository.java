package vn.swt301.labflowdemo.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByEntityAndEntityIdOrderByIdAsc(String entity, String entityId);
}
