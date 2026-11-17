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

The folder a document lives in sets its layer (`org/`, `languages/java/`, `teams/payments/`). A team rule can override
a language rule, and a deprecated rule points to its successor.

The project layer lives in each project's own repo, not here. A project describes itself in a `.knowledge.yaml`
(team, languages, frameworks) and can extend, replace or waive shared rules with its own docs.

See [knowledge/README.md](knowledge/README.md) for the authoring format and
[docs/knowledge-model.md](docs/knowledge-model.md) for how rules are resolved.

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
examples/         A sample project repo wired to the server
build-logic/      Shared Gradle convention plugins
docs/             Architecture, knowledge model and operating guides
```

The modules follow Clean Architecture with dependencies pointing inward only. See
[docs/architecture.md](docs/architecture.md).

## Getting started

### Prerequisites

- JDK 21. The build uses a Gradle toolchain and finds an installed JDK 21 on its own.
- Git, used to stamp the build with the content version.

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

To call the tools by hand with curl or an editor's HTTP client, see
[docs/calling-the-server.md](docs/calling-the-server.md).

## Connecting Claude Code

Register the server with Claude Code, in the project repo where you want to use it:

```sh
claude mcp add --transport http --scope project knowledge http://localhost:8080/mcp
```

`--scope project` writes a `.mcp.json` that the team can commit. Use `--scope user` to make the server available in
every project instead. Then wire the project up:

1. Add a `.knowledge.yaml` at the repo root with the project's team, languages, frameworks and overrides.
2. Put project docs that extend or replace shared rules under `docs/knowledge/`.
3. Add to `CLAUDE.md` (or `AGENTS.md`): read `.knowledge.yaml`, call `get_guidelines` with it, and give project docs
   precedence over shared rules.

The server also sends this call flow to clients as MCP instructions on connect.

[examples/sample-project](examples/sample-project) has all of this ready. To try it, start the server, then run
`claude` inside `examples/sample-project`, approve the `knowledge` server, and ask for something the guidelines cover,
for example "Which rules apply to error handling in this project?". The build checks that the sample's manifest resolves
against `knowledge/` without warnings.

## Configuration

Port, allowed hosts and the knowledge source are set by environment variables; see
[docs/configuration.md](docs/configuration.md).

## Security

The server has no authentication: run it on localhost or inside an internal network only. In Claude Code's `/mcp`
menu, use Reconnect, not Authenticate. See [docs/security.md](docs/security.md) for the details and for what to add
before exposing it further.

## Working on knowledge

1. Add or edit Markdown under `knowledge/`, following [knowledge/README.md](knowledge/README.md).
2. Preview your change against a running server without rebuilding:
   `KNOWLEDGE_DIR=../knowledge INCLUDE_DRAFTS=true ./gradlew :server:run`.
3. Run `./gradlew build` before opening a pull request. CI runs the same validation, so an invalid knowledge base
   never reaches deployment.

## Further reading

- [docs/architecture.md](docs/architecture.md): modules and request handling.
- [docs/development.md](docs/development.md): build setup, libraries and tests.
- [docs/knowledge-model.md](docs/knowledge-model.md): layers, scope, overrides, visibility and the project layer.
- [docs/configuration.md](docs/configuration.md): settings and environment variables.
- [docs/calling-the-server.md](docs/calling-the-server.md): calling the MCP tools with curl or an HTTP client.
- [docs/security.md](docs/security.md): the security model and what to add before exposing the server.
