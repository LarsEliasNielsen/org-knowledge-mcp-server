---
id: org-code-review
title: Code review
kind: guideline
owner: engineering-leads
status: active
summary: How we open, review and merge pull requests across all repositories.
related: [org-security]
last-reviewed: 2026-11-01
---
# Code review

Every change to a shared branch goes through a pull request.

## Opening pull requests

### ORG-REV-001 Keep pull requests small
Aim for changes a reviewer can understand in fifteen minutes. Split refactorings from behaviour changes.

### ORG-REV-002 Describe the why
The description states the problem, the chosen approach and how the change was tested. Link the ticket.

## Reviewing

### ORG-REV-003 Review within one working day
Open reviews block delivery, so they take priority over starting new work.

### ORG-REV-004 Separate blocking from optional comments
Prefix optional suggestions with `nit:` so the author knows what must change before merging.
