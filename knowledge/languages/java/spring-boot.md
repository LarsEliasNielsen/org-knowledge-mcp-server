---
id: java-spring-boot
title: Spring Boot services
kind: guideline
scope: { frameworks: [spring-boot] }
owner: java-guild
status: active
summary: Conventions for Spring Boot services written in Java.
related: [java-error-handling]
last-reviewed: 2026-10-20
---
# Spring Boot services

## Dependency injection

### JAVA-SPR-001 Use constructor injection
Inject dependencies through the constructor and keep fields `final`. No field injection with `@Autowired`.

## Configuration

### JAVA-SPR-002 Bind configuration to typed properties
Group settings in `@ConfigurationProperties` records instead of scattering `@Value` annotations.
