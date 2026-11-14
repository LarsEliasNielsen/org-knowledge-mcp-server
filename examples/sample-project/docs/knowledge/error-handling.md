# Error handling in the payment gateway client

Replaces shared rule JAVA-ERR-002 (unchecked exceptions for domain errors) for this project.

The legacy SOAP client throws checked `GatewayFault` exceptions from every call, and its generated stubs can't be
changed. Inside `gateway/`:

- Keep `GatewayFault` checked and declare it only on private methods of `GatewayAdapter` that call the client.
- Public `GatewayAdapter` methods catch `GatewayFault` and return the domain's `PaymentFailure` result, so the
  exception never leaves the adapter (as team rule PAY-ERR-001 requires).
- Outside `gateway/`, the shared Java rules apply unchanged.
