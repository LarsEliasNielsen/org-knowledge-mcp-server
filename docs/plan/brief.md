# Org Knowledge MCP Server - Project Brief

Overview board: https://claude.ai/artifact/QjMWb2G6WTXbYFy7endsRx
Task breakdown: [TASKS.md](TASKS.md)

## Goal

An MCP server that gives coding agents (Claude Code, IDE assistants) always-current organization knowledge:
general guidelines per programming language, shared software architecture, and team conventions.
Shared knowledge lives centrally in this repo as Markdown. Project-specific knowledge lives in each
project's own repo and extends or overrides the shared knowledge.

## Decisions

### Stack and build

- Kotlin on JDK 21 (Gradle toolchain), Gradle Kotlin DSL, version catalog in `gradle/libs.versions.toml`.
- Ktor 3.6.x via the Ktor BOM, Netty engine added explicitly (the MCP SDK does not pull one in).
- MCP: official Kotlin SDK `io.modelcontextprotocol:kotlin-sdk-server` (0.16.0 at time of writing; check the latest
  release and its samples before task 2, the API moves between pre-1.0 releases).
- Markdown: commonmark-java. Front matter is split off and decoded with kaml + kotlinx.serialization in strict mode
  (unknown keys fail). The Kotlin data classes are the schema; there is no separate JSON schema for now.
- Packaging: fat JAR via the Ktor Gradle plugin (`buildFatJar`). No Docker unless a need appears.
- Maven group and base package: `cloud.larn.knowledge`.
- One repo for content and server. The loader takes the knowledge location as config, so content can move to its own
  repo later without code changes.

### Architecture (Clean Architecture, one Gradle module per ring)

Dependencies point inward only.

| Module | Contains | Depends on |
|---|---|---|
| `domain` | Entities and value objects (`KnowledgeDoc`, `Rule`, `RuleId`, `Scope`, `Layer`, `ProjectContext`, `Override`), pure policy (scope matching, precedence) | Kotlin stdlib only |
| `application` | Use cases (`ListDocs`, `GetDoc`, `GetGuidelines`, `ValidateKnowledgeBase`, later `SearchDocs`), ports (`KnowledgeRepository`) | `domain` |
| `infrastructure` | Adapters: commonmark + kaml parser, classpath/filesystem loader, in-memory repository | `application` |
| `server` | Ktor + MCP SDK: tool registration, mapping use-case results to MCP results, composition root in `Application.kt`, config | all of the above |

A later `lint` CLI becomes a second delivery module next to `server`, reusing `application` and `infrastructure`.

### Transport and runtime

- Stateless Streamable HTTP via `Application.mcpStatelessStreamableHttp(path = "/mcp", allowedHosts = …) { … }`.
  The factory block runs per request, so the parsed index sits in a shared `AtomicReference`.
- DNS-rebinding protection defaults to localhost-only hosts, so `allowedHosts` comes from config.
- `GET /mcp` is not a health check; a separate `/health` route exists.
- Docs are copied into the fat JAR as classpath resources at build time. An optional `knowledgeDir` setting loads from
  the filesystem instead, for local editing. The index loads once at startup; no hot reload in v1.
- Content version = git SHA, surfaced as `Implementation.version`.
- Config in Ktor `application.yaml` (port, `allowedHosts`, `includeDrafts`, `knowledgeDir`) with env-var overrides.

### Quality gates

- Unit and integration tests cover all logic. TDD is not required.
- Unit tests weigh most on `domain` (scope matching, resolver, precedence) and `infrastructure` (parser, validation).
- Integration tests use Ktor `testApplication` + `kotlin-sdk-client` against the real `/mcp` endpoint, with a fixture
  knowledge base under `server/src/test/resources`.
- Test stack: JUnit 5, kotlin.test, Kotest assertions (no Kotest runner).
- A test loads the real `knowledge/` folder and fails on any validation error, reporting all errors at once. Contributors
  and codeowners run tests before merging server or content changes; CI runs them, so an invalid knowledge base never
  reaches the deploy stage.
- The server also fails fast at startup on an invalid knowledge base (logs every error, exits non-zero). Both paths use the
  same `ValidateKnowledgeBase` use case.

### Deploy and auth (backlog)

AWS Elastic Beanstalk (Java SE platform) running the fat JAR; CI on Jenkins, Bitbucket Pipelines later.
Initial auth: internal network + bearer token. The SDK's Ktor helpers can't be wrapped in `authenticate {}`; the SDK sample
`samples/simple-streamable-server` shows wiring the transport manually inside an auth route. OAuth later if needed.

## Knowledge model

### Layers (more specific wins)

`org` → `language` → `team` → `project`

```
knowledge/
  org/                       # applies to everyone
  languages/java/ kotlin/ go/
  teams/<team>/
```

The folder sets the layer and the default scope (`languages/java/` → `languages: [java]`). Front matter can narrow the scope,
never widen it (validation error).

### Front matter

```yaml
---
id: java-error-handling
title: Error handling in Java
kind: guideline            # guideline | architecture | adr | library | service
scope: { languages: [java], frameworks: [spring-boot] }   # omitted dimension = any
owner: java-guild
status: active             # draft | active | deprecated
summary: How we handle, wrap and log exceptions in Java services.
related: [adr-0012-idempotency-keys]
last-reviewed: 2026-11-01
---
```

Scope matching: three dimensions (`languages`, `teams`, `frameworks`). An omitted dimension matches anything, values within a
dimension are ORed, dimensions are ANDed.

### Rules

A rule is an H3 whose heading starts with a rule id matching `^[A-Z]+(-[A-Z]+)*-\d{3} `. Its body runs until the next H2 or H3.
Ids are unique across the knowledge base and never reused. An optional fenced `rule` block directly under the heading carries
rule-level metadata:

````markdown
### JAVA-ERR-001 Wrap checked exceptions at module boundaries
```rule
status: deprecated
superseded-by: JAVA-ERR-007
```
````

````markdown
### PAY-ERR-001 Map gateway errors to domain errors
```rule
overrides: [JAVA-ERR-003]
```
````

`overrides` lives at rule level. A rule may override only rules in a lower layer; for contexts where the overriding rule
applies, `get_guidelines` returns it marked `overrides JAVA-ERR-003` and drops the original.

Rules are the unit `get_guidelines` serves, whatever the document `kind`. A binding constraint in an architecture doc or ADR
is written as a rule (`### ARCH-STATE-001 Keep no session state in service memory`) and served like a coding guideline;
the surrounding context and rationale stay prose, reachable through `get_doc`. `kind` drives `list_docs` filtering and tells
the agent what kind of source a rule comes from. A superseded ADR gets `status: deprecated`, which removes its rules.

H2 sections are addressed by GitHub-style slugs (`## Logging and context` → `logging-and-context`). Duplicate slugs within a doc
fail validation.

### Validation

Duplicate doc or rule ids, unresolvable `related`, `overrides` or `superseded-by`, overrides targeting the same or a higher layer,
scope widening beyond the folder default, duplicate section slugs, and any front-matter schema violation.

### Visibility

- `draft` docs load and validate but are hidden from all tools, unless `includeDrafts` is on (local preview only).
- Deprecated rules, and all rules of deprecated docs, are excluded from `get_guidelines`; `get_doc` still returns them
  with their status.

### Project side (lives in each project repo, read locally by the agent)

```yaml
# .knowledge.yaml
team: payments
languages: [java, kotlin]
frameworks: [spring-boot]
overrides:
  - rule: JAVA-ERR-003
    action: replace          # extend | replace | waive
    doc: docs/knowledge/error-handling.md
    reason: Legacy SOAP client forces checked-exception wrapping
```

Plus `docs/knowledge/*.md` for project docs, and a line in `CLAUDE.md` / `AGENTS.md`:
read `.knowledge.yaml`, call `get_guidelines` with it, project docs take precedence.

### Who merges what

The server is remote and cannot read the developer's workspace, so context is passed explicitly.

- Server (deterministic): resolves shared layers for the given context and applies project overrides by action:
  - `waive`: the rule is dropped.
  - `replace`: only a stub is returned (rule id, title, pointer to the project doc, reason).
  - `extend`: the full rule is returned plus a marker `extendedBy: <doc>`; the agent appends the project text.
- Agent: reads the manifest and project docs locally and applies them last.

A project override that targets an unknown rule id, or a deprecated one, returns a warning (naming `superseded-by` when set).

## MCP surface (first version)

Tools (all `readOnlyHint = true`), each returning Markdown text content plus typed `structuredContent`:

- `list_docs(kind?, owner?)` → catalogue with summaries.
- `get_doc(id, section?)` → Markdown of one doc or one H2 section. An unknown section returns the list of valid slugs.
- `get_guidelines(team?, languages[], frameworks[], overrides[])` → effective rules with layer, source doc, override markers,
  and a `warnings` list.

Rule order in `get_guidelines`: layer (org → language → team), then doc `id`, then position in the doc. Within the language layer,
languages follow the order of the request's `languages` list.

Server `instructions` state the precedence rule. `search_docs`, the `kb://docs/{id}` resource template, and prompts are backlog.

## Repo layout

```
knowledge/                 # content, owned via CODEOWNERS per layer
domain/                    # cloud.larn.knowledge.domain
application/               # cloud.larn.knowledge.application
infrastructure/            # cloud.larn.knowledge.infrastructure
server/                    # cloud.larn.knowledge.server (Ktor app, Application.kt)
examples/sample-project/   # .knowledge.yaml + docs/knowledge + CLAUDE.md snippet
docs/plan/                 # this brief and TASKS.md
gradle/libs.versions.toml
settings.gradle.kts
```

## Open questions (deferred to backlog)

- Where the rule catalogue is published for project-repo linting (MCP tool vs static JSON).
- Whether the content moves to its own repo once doc authorship grows.
