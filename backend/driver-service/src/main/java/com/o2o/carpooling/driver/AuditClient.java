package com.o2o.carpooling.driver;

import java.util.Map;

interface AuditClient {
    void append(String actorId, String action, String targetType, String targetId, Map<String, String> metadata);
}
