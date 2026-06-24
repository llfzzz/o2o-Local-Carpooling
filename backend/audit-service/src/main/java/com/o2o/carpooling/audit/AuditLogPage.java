package com.o2o.carpooling.audit;

import com.o2o.carpooling.common.domain.AuditLog;

import java.util.List;

record AuditLogPage(List<AuditLog> items, int page, int size, long total) {
}
