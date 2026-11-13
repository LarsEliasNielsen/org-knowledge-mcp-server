---
id: org-security
title: Security baseline
kind: guideline
owner: security-team
status: active
summary: Security rules every service and library follows.
last-reviewed: 2026-10-15
---
# Security baseline

## Secrets

### ORG-SEC-001 Never commit secrets
Credentials, tokens and keys live in the secret store and reach services as environment variables.
A secret that reached a repository is rotated, even if the commit was reverted.

## Dependencies

### ORG-SEC-002 Keep dependencies patched
Apply security updates for direct dependencies within two weeks of release.

### ORG-SEC-003 Pin dependency versions
Builds use exact versions so the same commit always produces the same artifact.
