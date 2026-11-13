---
id: payments-errors
title: Payments error handling
kind: guideline
scope:
  languages: [java]
owner: payments-team
status: draft
summary: Payment-specific error handling.
---
## Gateway errors

### PAY-ERR-001 Map gateway errors to domain errors
```rule
overrides: [JAVA-ERR-003]
```
Gateway exceptions never leak into the domain.
