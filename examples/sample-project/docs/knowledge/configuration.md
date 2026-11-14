# Configuration

Extends shared rule JAVA-SPR-002 (typed configuration properties) for this project.

On top of binding settings to `@ConfigurationProperties` records:

- Gateway credentials are never in `application.yaml`. They come from the payments vault through the
  `PAYMENTS_GATEWAY_*` environment variables and bind to `GatewayCredentials`.
- Validate `GatewayCredentials` with `@Validated` so the service fails at startup when a credential is missing.
