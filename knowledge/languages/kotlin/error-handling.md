---
id: kotlin-error-handling
title: Error handling in Kotlin
kind: guideline
owner: kotlin-guild
status: active
summary: How we model and propagate errors in Kotlin code.
related: [kotlin-coroutines]
last-reviewed: 2026-11-05
---
# Error handling in Kotlin

## Modelling errors

### KT-ERR-001 Model expected failures as types
Return a sealed result type for failures the caller is expected to handle. Exceptions are for bugs and
infrastructure failures.

### KT-ERR-002 Do not catch CancellationException
Catching `Throwable` or `Exception` in a coroutine must rethrow `CancellationException`, or cancellation breaks.
