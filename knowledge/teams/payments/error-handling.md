---
id: payments-error-handling
title: Payments error handling
kind: guideline
scope: { languages: [java] }
owner: payments-team
status: active
summary: How the payments team handles payment gateway errors in Java services.
related: [java-error-handling]
last-reviewed: 2026-11-10
---
# Payments error handling

## Gateway errors

### PAY-ERR-001 Translate gateway exceptions at the adapter
```rule
overrides: [JAVA-ERR-003]
```
Gateway adapters catch every gateway exception and translate it into a `PaymentFailure` result with the
gateway's error code. Nothing from the gateway SDK crosses the adapter boundary, and the original exception
is logged once with the payment id.
