package com.o2o.carpooling.audit;

import com.o2o.carpooling.common.domain.AuditLog;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AuditServiceTest {

    private final InMemoryAuditLogStore store = new InMemoryAuditLogStore();
    private final AuditService service = new AuditService(store);

    @Test
    void appendsAuditLogWithTraceIdAndActorFallback() {
        AuditLog log = service.append(new AppendAuditCommand(
            "operator-001",
            "ORDER_TIMEOUT",
            "ORDER",
            "order-001",
            Map.of("source", "rabbit"),
            "trace-001"
        ));

        assertThat(log.auditId()).startsWith("audit-");
        assertThat(log.actorId()).isEqualTo("operator-001");
        assertThat(log.traceId()).isEqualTo("trace-001");
        assertThat(store.saved).hasSize(1);
    }

    @Test
    void queryFiltersAndCapsPageSize() {
        service.append(new AppendAuditCommand("operator-001", "ORDER_TIMEOUT", "ORDER", "order-001", Map.of(), "trace-001"));
        service.append(new AppendAuditCommand("operator-001", "ORDER_PAID", "ORDER", "order-002", Map.of(), "trace-002"));
        service.append(new AppendAuditCommand("operator-002", "FILE_DOWNLOAD_PRESIGNED", "FILE", "file-001", Map.of(), "trace-003"));

        AuditLogPage page = service.query(new AuditQuery(
            Optional.of("ORDER"),
            Optional.empty(),
            Optional.empty(),
            Optional.of("operator-001"),
            0,
            500
        ));

        assertThat(page.size()).isEqualTo(100);
        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).extracting(AuditLog::targetType).containsOnly("ORDER");
        assertThat(page.items()).extracting(AuditLog::actorId).containsOnly("operator-001");
    }

    static class InMemoryAuditLogStore implements AuditLogStore {
        final List<AuditLog> saved = new ArrayList<>();

        @Override
        public AuditLog save(AuditLog log) {
            saved.add(log);
            return log;
        }

        @Override
        public AuditLogPage query(AuditQuery query) {
            List<AuditLog> filtered = saved.stream()
                .filter(log -> query.targetType().map(value -> value.equals(log.targetType())).orElse(true))
                .filter(log -> query.targetId().map(value -> value.equals(log.targetId())).orElse(true))
                .filter(log -> query.action().map(value -> value.equals(log.action())).orElse(true))
                .filter(log -> query.actorId().map(value -> value.equals(log.actorId())).orElse(true))
                .sorted(Comparator.comparing(AuditLog::occurredAt).reversed())
                .toList();
            int from = Math.min(query.page() * query.size(), filtered.size());
            int to = Math.min(from + query.size(), filtered.size());
            return new AuditLogPage(filtered.subList(from, to), query.page(), query.size(), filtered.size());
        }
    }
}
