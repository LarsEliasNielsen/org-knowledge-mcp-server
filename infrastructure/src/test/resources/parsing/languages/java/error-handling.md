---
id: java-error-handling
title: Error handling in Java
kind: guideline
scope: { frameworks: [spring-boot] }
owner: java-guild
status: active
summary: How we handle, wrap and log exceptions in Java services.
related: [org-code-review]
---
## Exceptions

### JAVA-ERR-001 Wrap checked exceptions at module boundaries
```rule
status: deprecated
superseded-by: JAVA-ERR-003
```
Old advice.

### JAVA-ERR-003 Never swallow exceptions

Log or rethrow. Never both.

```java
try { call(); } catch (IOException e) { throw new UncheckedIOException(e); }
```

## Logging and context

Include the correlation id.
