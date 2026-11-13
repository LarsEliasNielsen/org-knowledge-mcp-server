---
id: kotlin-coroutines
title: Coroutines in Kotlin
kind: guideline
owner: kotlin-guild
status: active
summary: Structured concurrency rules for Kotlin services.
last-reviewed: 2026-11-05
---
# Coroutines in Kotlin

## Scopes

### KT-COR-001 Never use GlobalScope
Launch coroutines in a scope tied to a lifecycle (a request, a component) so cancellation propagates.

### KT-COR-002 Inject dispatchers
Pass `CoroutineDispatcher`s in through the constructor instead of hard-coding `Dispatchers.IO`, so tests can
replace them.

## Blocking code

### KT-COR-003 Wrap blocking calls
Call blocking APIs inside `withContext(ioDispatcher)` and never from a dispatcher meant for CPU work.
