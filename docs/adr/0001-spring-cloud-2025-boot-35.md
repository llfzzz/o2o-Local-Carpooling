# ADR 0001: Spring Cloud 2025.0.x With Spring Boot 3.5.x

## Status

Accepted

## Context

项目需要 JDK 21、Spring Boot 3.5.x、Spring Cloud 2025.0.x、Spring Cloud Alibaba 2025.0.x 的保守企业级组合。首期目标是可运行 MVP 和清晰扩展边界，不追逐 Boot 4。

## Decision

- Backend parent uses Spring Boot `3.5.15`.
- Spring Cloud BOM uses `2025.0.3`.
- Spring Cloud Alibaba BOM uses `2025.0.0.0`.
- Java release is fixed to `21`.
- Boot 4 and Spring Cloud 2025.1 are deferred to a later ADR.

## Consequences

- 版本线与当前计划兼容，降低首期集成风险。
- 后续升级必须通过 CI、集成测试、Gateway 契约测试和运维回滚方案。
- 新增服务必须继承根 POM，不允许单独漂移 Spring 版本。
