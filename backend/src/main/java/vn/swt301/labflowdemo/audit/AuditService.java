package vn.swt301.labflowdemo.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Writes one audit row (who, what, before, after). Runs inside the caller's transaction,
 * so "change + audit" commit or roll back together.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public void record(Long actorId, String entity, Object entityId, String action, Object before, Object after) {
        AuditLog row = new AuditLog();
        row.setActorId(actorId);
        row.setEntity(entity);
        row.setEntityId(String.valueOf(entityId));
        row.setAction(action);
        row.setBeforeData(toJson(before));
        row.setAfterData(toJson(after));
        row.setAt(Instant.now(clock));
        repository.save(row);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }
}
