# Tasks

Each task is a vertical slice that ends green (`./gradlew build` passes) and runnable. Decisions and terminology come from
[brief.md](brief.md). Tasks 1-9 are detailed; the backlog gets refined once task 9 is done.

---

## 1. Project setup

**Goal:** an empty multi-module Gradle build that compiles and runs tests.

**Scope**
- `settings.gradle.kts` with modules `domain`, `application`, `infrastructure`, `server`; root project name `knowledge-mcp-server`.
- `gradle/libs.versions.toml` with Kotlin, Ktor BOM, MCP SDK (server + client), commonmark-java, kaml, kotlinx.serialization,
  JUnit 5, Kotest assertions, Logback. Check current stable versions when writing the catalog.
- Shared build logic (convention plugin in `buildSrc` or `build-logic`) for Kotlin JVM, JDK 21 toolchain, JUnit Platform,
  group `cloud.larn.knowledge`.
- Module dependencies wired inward-only as in the brief's architecture table.
- Gradle wrapper, `.gitignore` (Gradle, IntelliJ, build output, OS files).
- One placeholder test per module to prove the test setup.

**Acceptance criteria**
- `./gradlew build` passes on a clean checkout and runs the tests of all four modules.
- `domain` has no dependencies beyond the Kotlin stdlib (and test dependencies).

**Out of scope:** any Ktor or MCP code.

---

## 2. Walking skeleton

**Goal:** a deployable server with one MCP tool, proven by integration tests and MCP Inspector.

**Scope**
- `server`: Ktor + Netty, `application.yaml` with `port`, `allowedHosts`, env-var overrides.
- `GET /health` returning 200 with the version.
- Stateless `/mcp` via `mcpStatelessStreamableHttp`, `Implementation(name, version)`, one tool `server_info` (read-only)
  returning name and version.
- Version = git SHA injected at build time (generated resource or JAR manifest), falling back to `dev`.
- Fat JAR via the Ktor Gradle plugin.
- Integration tests with `testApplication` + `kotlin-sdk-client`: `/health`, `tools/list`, `server_info` call.

**Acceptance criteria**
- `./gradlew :server:buildFatJar` produces a JAR; `java -jar` starts the server.
- MCP Inspector connects to `http://localhost:<port>/mcp`, lists and calls `server_info`.
- A request with a `Host` header outside `allowedHosts` is rejected (integration test).

**Out of scope:** knowledge content, auth.

---

## 3. Domain model

**Goal:** the knowledge model as pure Kotlin, with scope matching.

**Scope**
- `domain`: `DocId`, `RuleId` (validated format), `Layer` (ordered: org, language, team), `Kind`, `Status`, `Scope`
  (languages, teams, frameworks), `KnowledgeDoc` (front matter, H2 sections with slugs, rules), `Rule` (id, title, body,
  status, `supersededBy`, `overrides`), `ProjectContext`, `ProjectOverride` (rule, action, doc, reason), `OverrideAction`.
- Scope matching per the brief (omitted dimension = any, OR within, AND across).
- Scope narrowing check: a front-matter scope is valid only if it narrows the folder default.
- Slug function for H2 headings (GitHub style).

**Acceptance criteria**
- Unit tests cover scope matching (each dimension, combinations, empty context), narrowing vs widening, `RuleId` format
  accept/reject cases, slug generation (punctuation, case, unicode, numbers).

**Out of scope:** parsing, I/O.

---

## 4. Markdown parsing

**Goal:** turn one Markdown file plus its path into a `KnowledgeDoc`, or a list of errors.

**Scope**
- `infrastructure`: split front matter, decode with kaml in strict mode, parse the body with commonmark-java.
- Derive layer and default scope from the relative path (`org/`, `languages/<lang>/`, `teams/<team>/`).
- Extract H2 sections (slug, raw Markdown) and rules (H3 with rule id, body to next H2/H3, optional `rule` block).
- Errors carry file path and, where possible, line number. Parsing collects all errors in a file rather than stopping at the first.

**Acceptance criteria**
- Unit tests on fixture files cover: a valid doc in each layer, missing/unknown front-matter keys, bad enum values,
  malformed YAML, rule with and without `rule` block, H3 without rule id (plain content), body boundaries, files outside
  known layer folders.
- The section and rule Markdown returned matches the source text exactly (no re-rendering).

**Out of scope:** cross-document validation.

---

## 5. Loading, validation and seed content

**Goal:** load a whole knowledge base, validate it, and gate the build on the real `knowledge/` folder.

**Scope**
- `application`: `KnowledgeRepository` port, `ValidateKnowledgeBase` use case with all cross-document checks from the brief.
- `infrastructure`: filesystem loader and classpath loader (build step copies `knowledge/` into `server` resources and
  generates an index file listing the paths, since classpath directories can't be listed inside a JAR); in-memory repository.
- `server`: load at startup from classpath or `knowledgeDir`; on errors log all of them and exit non-zero; store the index in an
  `AtomicReference`; `includeDrafts` config flag.
- Seed content in `knowledge/`: org docs, `languages/java` and `languages/kotlin`, one team (`teams/payments`), including at
  least one team rule that overrides a language rule, one deprecated rule with `superseded-by`, one draft doc, and an ADR.
- A test that loads the real `knowledge/` folder and fails listing every error.

**Acceptance criteria**
- Unit tests for each validation rule (one passing and one failing case each).
- The real-content test passes; breaking a seed doc makes `./gradlew build` fail with a readable error list.
- Server starts from the fat JAR with the bundled docs, and from `knowledgeDir` locally.
- An integration test proves startup fails on an invalid fixture knowledge base.

**Out of scope:** tools beyond `server_info`.

---

## 6. Layer resolver

**Goal:** the `GetGuidelines` use case, fully specified by tests.

**Scope**
- `application`: input = `ProjectContext` (team, languages, frameworks, overrides); output = ordered effective rules (layer,
  source doc, rule, markers) plus warnings.
- Steps: select active guideline docs whose scope matches the context (drafts per `includeDrafts`), collect rules, drop
  deprecated rules, apply team-layer `overrides`, apply project overrides (`waive`, `replace` stub, `extend` marker),
  order per the brief.
- Warnings for project overrides that target unknown or deprecated rules (naming `superseded-by`).

**Acceptance criteria**
- Unit tests cover: each layer in isolation, layer ordering, language order following the request, framework scoping,
  team override replacing a language rule only for that team, all three project actions, overrides on team-overridden rules,
  deprecated exclusion, unknown/deprecated override warnings, empty context (org only), deterministic order.

**Out of scope:** MCP wiring.

---

## 7. `list_docs` and `get_doc` tools

**Goal:** the first knowledge tools, end to end.

**Scope**
- `application`: `ListDocs(kind?, owner?)` and `GetDoc(id, section?)` use cases, with typed errors for unknown doc and unknown section
  (carrying valid slugs).
- `server`: tool registration with input schemas, `readOnlyHint = true`, Markdown text content plus `structuredContent`,
  MCP error results for unknown doc or section. Tool handlers stay thin and map use-case results only.

**Acceptance criteria**
- Unit tests for both use cases and for result mapping.
- Integration tests against the fixture knowledge base: filtering, full doc, single section, unknown doc, unknown section listing
  slugs, drafts hidden.
- Manual check in MCP Inspector against the bundled content.

**Out of scope:** search, resource template.

---

## 8. `get_guidelines` tool

**Goal:** expose the resolver over MCP.

**Scope**
- `server`: tool with input schema mirroring `.knowledge.yaml` (team, languages, frameworks, overrides[] with rule, action, doc,
  reason), Markdown rendering grouped by layer with override markers, `structuredContent` with rules and warnings.
- Input validation errors (bad action, malformed rule id) returned as MCP tool errors.

**Acceptance criteria**
- Unit tests for input parsing and Markdown rendering.
- Integration tests for representative contexts: org only, one language, two languages plus team, all three project actions,
  warnings present.

**Out of scope:** reading `.knowledge.yaml` on the server side (the agent passes it).

---

## 9. Agent integration

**Goal:** Claude Code uses the server from a real project directory.

**Scope**
- Server `instructions` stating the precedence rule and the expected call flow.
- `examples/sample-project/`: `.knowledge.yaml`, `docs/knowledge/*.md` for one `replace` and one `extend` override, a `CLAUDE.md`
  snippet (read `.knowledge.yaml`, call `get_guidelines` with it, project docs take precedence).
- README: build, run, configure, connect Claude Code (`claude mcp add --transport http …`), author a doc, run validation.
- A test that parses the sample `.knowledge.yaml` and resolves it against the real knowledge base without warnings, so the example
  stays in sync with content.

**Acceptance criteria**
- Manual end-to-end check: Claude Code in `examples/sample-project` calls `get_guidelines` with the manifest and applies
  the project overrides when asked to write code for that project.

**Out of scope:** deployment.

---

## Backlog (refine after task 9)

- `search_docs` (in-memory Lucene/BM25, section-level hits with snippet).
- `kb://docs/{id}` resource template (text/markdown).
- Bearer token auth (manual transport wiring inside an auth route, per the SDK sample).
- Jenkins pipeline (build, test, fat JAR artifact); Bitbucket Pipelines later.
- Elastic Beanstalk deploy (Java SE platform, `allowedHosts` and port via env).
- Project-repo lint CLI as a `lint` delivery module; decide how the rule catalogue is published (MCP tool vs static JSON).
- Telemetry on overridden rules and zero-hit searches.
- Prompts (e.g. `review-against-guidelines`).
- JSON schema for front matter (editor autocompletion).
- CODEOWNERS per layer.
