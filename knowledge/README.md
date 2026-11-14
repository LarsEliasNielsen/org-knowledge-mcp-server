# Knowledge base

Shared organization knowledge served by the knowledge MCP server. This README is not served.

## Layout

The folder sets a document's layer and default scope. More specific layers win.

| Folder | Layer | Default scope |
|---|---|---|
| `org/` | org | everyone |
| `languages/<language>/` | language | `languages: [<language>]` |
| `teams/<team>/` | team | `teams: [<team>]` |

Subfolders (e.g. `org/adr/`) are fine. Front matter can narrow the scope, never widen it.

## Front matter

```yaml
---
id: java-error-handling          # unique, lowercase kebab-case
title: Error handling in Java
kind: guideline                  # guideline | architecture | adr | library | service
scope: { frameworks: [spring-boot] }   # optional; omitted dimensions inherit the folder scope
owner: java-guild
status: active                   # draft | active | deprecated
summary: How we handle, wrap and log exceptions in Java services.
related: [org-code-review]       # optional doc ids
last-reviewed: 2026-11-01        # optional, YYYY-MM-DD
---
```

## Rules

A rule is an H3 starting with its id: `### JAVA-ERR-003 Never swallow exceptions`. Ids are unique across the
knowledge base and never reused. Optional metadata goes in a `rule` block directly under the heading:

````markdown
### JAVA-ERR-001 Wrap checked exceptions at module boundaries
```rule
status: deprecated
superseded-by: JAVA-ERR-003
```
````

A rule in a higher layer can replace lower-layer rules with `overrides: [JAVA-ERR-003]`.

Rules are what `get_guidelines` serves, whatever the document kind. Write every binding constraint as a rule,
including those in architecture docs and ADRs (`### ARCH-STATE-001 Keep no session state in service memory`).
Context, rationale and other prose stay in the document; agents read it with `get_doc`. When an ADR is
superseded, set its `status: deprecated` so its rules stop applying.

## Checking your changes

Run `./gradlew build`. The build fails and lists every problem if the knowledge base is invalid.
