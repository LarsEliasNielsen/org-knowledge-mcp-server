---
id: adr-0001-stateless-services
title: "ADR-0001: Services are stateless"
kind: adr
owner: architecture-board
status: active
summary: Services keep no session state in memory, so any instance can serve any request.
related: [org-security]
last-reviewed: 2026-09-01
---
# ADR-0001: Services are stateless

## Context

Services run as several instances behind a load balancer and are replaced on every deploy.

## Decision

Services keep no per-user or per-session state in memory. State lives in a database or cache that all
instances share.

### ARCH-STATE-001 Keep no session state in service memory
Per-user and per-session state goes to a store all instances share (database or distributed cache). Code that
needs the same instance to handle a follow-up request is a bug.

## Consequences

Any instance can serve any request, so we need no sticky sessions and can scale and deploy freely.
In-memory caches are allowed when losing them only costs performance.
