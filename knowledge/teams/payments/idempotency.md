---
id: payments-idempotency
title: Idempotent payment requests
kind: guideline
owner: payments-team
status: draft
summary: Draft rules for idempotency keys on payment APIs.
related: [adr-0001-stateless-services]
---
# Idempotent payment requests

## Idempotency keys

### PAY-IDEM-001 Require an idempotency key on every mutating request
Clients send an `Idempotency-Key` header. The server stores the first response per key for 24 hours and
replays it for retries.
