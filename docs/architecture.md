# Architecture

## Modules

The server follows Clean Architecture with one Gradle module per ring. Dependencies point inward only, so the core
knowledge logic has no dependency on Ktor, MCP or file formats.

```
server  →  infrastructure  →  application  →  domain
```

| Module | Responsibility | Depends on |
|---|---|---|
| `domain` | Entities and value objects: documents, rules, rule ids, scopes, layers, project contexts and overrides, and the `KnowledgeBase` aggregate. Pure policy such as scope matching and scope narrowing. | Kotlin stdlib only |
| `application` | Use cases: `GetGuidelines` (the layer resolver), `ListDocs`, `GetDoc` and `ValidateKnowledgeBase` (all checks across documents). Defines the ports `KnowledgeSource` and `KnowledgeRepository`. | `domain` |
| `infrastructure` | Adapters: the Markdown parser (commonmark-java plus kaml for front matter), the `.knowledge.yaml` manifest parser, filesystem and classpath knowledge sources, and the in-memory repository the server reads from. | `application` |
| `server` | The Ktor application: configuration, startup loading and validation, `/health`, the MCP endpoint and tool definitions that map use-case results to MCP results. Composition root in `Application.kt`. Bundles `knowledge/` into the JAR. | all of the above |

A new delivery mechanism, such as a lint CLI for project repos, becomes a module next to `server` and reuses
`application` and `infrastructure`.

Content and server share one repo. The loader takes the knowledge location as configuration, so the content can move to
its own repo later without code changes.

## Request handling

`/mcp` runs the SDK's stateless Streamable HTTP transport (`mcpStatelessStreamableHttp`), which builds a fresh MCP
server for every request. Building one must stay cheap, so the knowledge base is parsed and validated once at startup
and shared through an `AtomicReference`; there is no hot reload. With no session state, any instance can serve any
request and the server scales horizontally.

The build bundles `knowledge/` as classpath resources with a generated index file, since classpath directories can't
be listed inside a JAR.
