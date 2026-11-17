# Configuration

Settings live in `server/src/main/resources/application.yaml`. Override them with environment variables:

| Variable | Default | Purpose |
|---|---|---|
| `PORT` | `8080` | HTTP port. |
| `ALLOWED_HOSTS` | `localhost,127.0.0.1,[::1]` | Comma-separated hostnames accepted in the `Host` header of `/mcp` requests (DNS-rebinding protection). Set this to the deployed hostname. |
| `KNOWLEDGE_DIR` | unset | Load knowledge from this directory instead of the copy bundled in the JAR. Relative paths resolve against the working directory, which is `server/` under `./gradlew :server:run`. |
| `INCLUDE_DRAFTS` | `false` | Also serve documents with `status: draft`. For local previews only. |

The server validates the knowledge base at startup and refuses to start if it is invalid.

`ALLOWED_HOSTS` is not an access control. See [security.md](security.md).
