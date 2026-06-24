package com.o2o.carpooling.audit;

import com.o2o.carpooling.common.domain.AuditLog;

interface AuditLogStore {
    AuditLog save(AuditLog log);

    AuditLogPage query(AuditQuery query);
}
