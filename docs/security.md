# Security

The server has no authentication. It is built to run on localhost or inside an internal network, where network
access is the only control: anyone who can reach `/mcp` can read every published document. The tools are read-only,
so the risk is exposure of internal knowledge, not changes to it. `ALLOWED_HOSTS` guards against DNS rebinding and is
not an access control.

## OAuth

The server does not implement MCP's OAuth flow. Choosing Authenticate for it in Claude Code's `/mcp` menu fails with
`Dynamic Client Registration rejected (HTTP 404)`, because the server serves no OAuth discovery or registration
endpoints. Use Reconnect instead.

## Before exposing the server

Before exposing the server beyond trusted machines, add one of:

- An API key or static bearer token checked by the server. Clients send it as a configured header, for example
  `claude mcp add --transport http knowledge <url> --header "Authorization: Bearer $TOKEN"`, so no OAuth flow is
  involved.
- A gateway or reverse proxy (API gateway, VPN, SSO-aware proxy) that authenticates requests before they reach the
  server, which stays unchanged.

OAuth is only worth its cost when per-user identity matters, for example for audit logs or per-team visibility.

For a token checked by the server: the SDK's Ktor helpers, such as `mcpStatelessStreamableHttp`, can't be wrapped in
Ktor's `authenticate {}`. The SDK sample `samples/simple-streamable-server` shows how to wire the transport by hand
inside an authenticated route.
