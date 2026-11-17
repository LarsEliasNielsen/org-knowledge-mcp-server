# Development

## Build

- Kotlin on a JDK 21 toolchain, set once in the `knowledge.kotlin-jvm` convention plugin under `build-logic/`. Every
  module applies it. Compiler warnings fail the build.
- Gradle Kotlin DSL through the wrapper, with all versions in `gradle/libs.versions.toml`.
- `./gradlew :server:buildFatJar` packages the server with the Ktor Gradle plugin. There is no Docker image.
- The build stamps the git SHA as the content version, which `server_info`, `/health` and the MCP
  `Implementation.version` report. A working tree with local changes adds a `-dirty` suffix.
- Maven group and base package: `cloud.larn.knowledge`.

## Libraries

- Ktor 3 through the Ktor BOM. The Netty engine is declared explicitly, since the MCP SDK does not pull one in.
- The official MCP Kotlin SDK (`io.modelcontextprotocol:kotlin-sdk-server`, plus `kotlin-sdk-client` for tests). It is
  pre-1.0 and its API moves between releases, so check its changelog and samples when upgrading.
- commonmark-java for Markdown. Front matter is split off and decoded with kaml and kotlinx.serialization in strict
  mode, so unknown keys fail. The Kotlin data classes are the schema.

## Tests

- JUnit 5 with kotlin.test and Kotest assertions (no Kotest runner).
- Unit tests weigh most on `domain` (scope matching, resolver, precedence) and `infrastructure` (parsing, validation).
- Integration tests run Ktor's `testApplication` with `kotlin-sdk-client` against the real `/mcp` endpoint and a
  fixture knowledge base under `server/src/test/resources`.
- A test loads the real `knowledge/` folder through `ValidateKnowledgeBase` and fails listing every error, so
  `./gradlew build` and CI keep an invalid knowledge base from reaching deployment.
  [knowledge-model.md](knowledge-model.md) lists the checks.
- A test resolves the `.knowledge.yaml` of `examples/sample-project` against `knowledge/` and fails on any warning, so
  the sample stays in sync with the content.
