# Org Knowledge MCP Server

An [MCP](https://modelcontextprotocol.io) server that gives coding agents (Claude Code, IDE assistants) the
organization's shared engineering knowledge: guidelines per programming language, software architecture, decisions
(ADRs) and team conventions. The knowledge is plain Markdown in this repo, validated by the build and baked into the
server, so every agent works from the same current rules.

The repo holds two things:

- `knowledge/`, the content: Markdown documents with YAML front matter, owned by guilds and teams.
- The server: a Kotlin + Ktor application exposing that content as read-only MCP tools.

## How it works

Knowledge is organized in layers. A more specific layer wins over a general one:

```
org  →  language  →  team  →  project
```

The folder a document lives in sets its layer (`org/`, `languages/java/`, `teams/payments/`). Binding guidance is
written as rules: H3 headings with a stable id, such as `### JAVA-ERR-003 Never swallow exceptions`. A team rule can
override a language rule, and a deprecated rule points to its successor.

The project layer lives in each project's own repo, not here. A project describes itself in a `.knowledge.yaml`
(team, languages, frameworks) and can extend, replace or waive shared rules with its own docs. The server can't read
the developer's workspace, so the agent passes that manifest to `get_guidelines`. The server resolves the shared
layers and marks the rules the project overrides. The agent applies the project docs last.

See [knowledge/README.md](knowledge/README.md) for the authoring format.

## MCP tools

All tools are read-only.

| Tool | Purpose |
|---|---|
| `get_guidelines` | The rules in effect for a project context, in layer order, with override markers and warnings about overrides that don't apply. |
| `list_docs` | The document catalogue with summaries, optionally filtered by kind and owner. |
| `get_doc` | One document's Markdown, or one H2 section of it. |
| `server_info` | Server name, content version (git SHA) and knowledge base size. |

Each tool returns Markdown for the model plus structured JSON for clients. The server runs over stateless Streamable
HTTP at `/mcp`. `GET /health` serves health checks.

## Repository layout

```
knowledge/        Shared knowledge content (Markdown), the source of truth
domain/           Gradle module: knowledge model and policy
application/      Gradle module: use cases and ports
infrastructure/   Gradle module: parsing and loading adapters
server/           Gradle module: Ktor app and MCP tools
build-logic/      Shared Gradle convention plugins
docs/plan/        Project brief and task plan
```

## Modules

The server follows Clean Architecture with one Gradle module per ring. Dependencies point inward only, so the core
knowledge logic has no dependency on Ktor, MCP or file formats.

| Module | Responsibility | Depends on |
|---|---|---|
| `domain` | Entities and value objects: documents, rules, rule ids, scopes, layers, project contexts and overrides, and the `KnowledgeBase` aggregate. Pure policy such as scope matching and scope narrowing. | Kotlin stdlib only |
| `application` | Use cases: `GetGuidelines` (the layer resolver), `ListDocs`, `GetDoc` and `ValidateKnowledgeBase` (all checks across documents). Defines the ports `KnowledgeSource` and `KnowledgeRepository`. | `domain` |
| `infrastructure` | Adapters: the Markdown parser (commonmark-java plus kaml for front matter), filesystem and classpath knowledge sources, and the in-memory repository the server reads from. | `application` |
| `server` | The Ktor application: configuration, startup loading and validation, `/health`, the MCP endpoint and tool definitions that map use-case results to MCP results. Composition root in `Application.kt`. Bundles `knowledge/` into the JAR. | all of the above |

## Getting started

### Prerequisites

- JDK 21. The build uses a Gradle toolchain and finds an installed JDK 21 on its own. Gradle itself runs on any
  JDK 17 or newer.
- Git, used to stamp the build with the content version.
- Node.js (optional), to run MCP Inspector.

No Gradle installation is needed; use the wrapper.

### Build and test

```sh
./gradlew build
```

This compiles every module and runs all unit and integration tests. It also validates `knowledge/`: any invalid
document fails the build with every problem listed, each with file and line.

### Run the server

Build the fat JAR and run it:

```sh
./gradlew :server:buildFatJar
java -jar server/build/libs/knowledge-mcp-server.jar
```

Or run it straight from Gradle:

```sh
./gradlew :server:run
```

The server listens on `http://localhost:8080`. Check it with:

```sh
curl http://localhost:8080/health
```

### Configuration

Settings live in `server/src/main/resources/application.yaml`. Override them with environment variables:

| Variable | Default | Purpose |
|---|---|---|
| `PORT` | `8080` | HTTP port. |
| `ALLOWED_HOSTS` | `localhost,127.0.0.1,[::1]` | Comma-separated hostnames accepted in the `Host` header of `/mcp` requests (DNS-rebinding protection). Set this to the deployed hostname. |
| `KNOWLEDGE_DIR` | unset | Load knowledge from this directory instead of the copy bundled in the JAR. Relative paths resolve against the working directory, which is `server/` under `./gradlew :server:run`. |
| `INCLUDE_DRAFTS` | `false` | Also serve documents with `status: draft`. For local previews only. |

The server validates the knowledge base at startup and refuses to start if it is invalid.

### Try the tools

With the server running, open MCP Inspector and connect to `http://localhost:8080/mcp` with the Streamable HTTP
transport:

```sh
npx @modelcontextprotocol/inspector
```

Or call a tool from the command line:

```sh
npx @modelcontextprotocol/inspector --cli http://localhost:8080/mcp --transport http \
  --method tools/call --tool-name get_guidelines \
  --tool-arg team=payments --tool-arg 'languages=["java"]'
```

## Working on knowledge

1. Add or edit Markdown under `knowledge/`, following [knowledge/README.md](knowledge/README.md).
2. Preview your change against a running server without rebuilding:
   `KNOWLEDGE_DIR=../knowledge INCLUDE_DRAFTS=true ./gradlew :server:run`.
3. Run `./gradlew build` before opening a pull request. CI runs the same validation, so an invalid knowledge base
   never reaches deployment.

## Further reading

- [docs/plan/brief.md](docs/plan/brief.md): the design decisions and the full knowledge model.
- [docs/plan/TASKS.md](docs/plan/TASKS.md): the task plan and backlog.
