# Calling the server over HTTP

The MCP endpoint is plain JSON-RPC over HTTP, so any HTTP client can call the tools without an MCP client. This is
useful for checking a change by hand or debugging a deployed server.

## How requests work

`/mcp` runs the stateless Streamable HTTP transport:

- Each request stands alone. No `initialize` handshake or session id is needed; send `tools/list` or `tools/call`
  directly.
- Send the request as a `POST` with `Content-Type: application/json`.
- If the request has an `Accept` header, it must list both `application/json` and `text/event-stream`. Otherwise the
  server answers `406 Not Acceptable`.
- Responses are plain JSON, not an event stream.
- The `Host` header must match `ALLOWED_HOSTS` (see [configuration.md](configuration.md)). The defaults cover
  `localhost`.

## curl

With the server running on the default port, list the tools:

```sh
curl http://localhost:8080/mcp \
  -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'
```

Call a tool:

```sh
curl http://localhost:8080/mcp \
  -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"get_guidelines","arguments":{"team":"payments","languages":["java"]}}}'
```

The quoting is for POSIX shells. On Windows, run these in Git Bash: PowerShell mangles the JSON quotes when it passes
them to `curl.exe`, and in Windows PowerShell 5.1 `curl` is an alias for `Invoke-WebRequest`.

## HTTP Client file

[mcp.http](mcp.http) has a ready-made request for `tools/list` and for each tool. Open it in IntelliJ IDEA's HTTP Client
or VS Code's REST Client and run the requests from the editor. Change `@host` at the top to target another server.
