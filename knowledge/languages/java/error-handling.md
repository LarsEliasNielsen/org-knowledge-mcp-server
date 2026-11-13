---
id: java-error-handling
title: Error handling in Java
kind: guideline
owner: java-guild
status: active
summary: How we handle, wrap and log exceptions in Java services.
related: [org-code-review]
last-reviewed: 2026-11-01
---
# Error handling in Java

## Exceptions

### JAVA-ERR-001 Wrap checked exceptions at module boundaries
```rule
status: deprecated
superseded-by: JAVA-ERR-003
```
Replaced by JAVA-ERR-003, which covers wrapping as one way to avoid losing an exception.

### JAVA-ERR-002 Use unchecked exceptions for domain errors
Domain errors extend a shared `DomainException`. Checked exceptions are for recoverable I/O at the edges.

### JAVA-ERR-003 Never swallow exceptions
Every caught exception is either rethrown (wrapped with context) or logged and handled. An empty `catch`
block fails review.

```java
try {
    gateway.charge(payment);
} catch (IOException e) {
    throw new PaymentFailedException(payment.id(), e);
}
```

## Logging

### JAVA-ERR-004 Log an exception once
Log where the exception is handled, not where it is rethrown, so each failure appears once in the logs.
