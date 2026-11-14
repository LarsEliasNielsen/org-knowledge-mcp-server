# Error handling in the payment gateway client

Replaces shared rule JAVA-ERR-002 (unchecked exceptions for domain errors) for this project.

The legacy SOAP client throws checked `GatewayFault` exceptions from every call, and its generated stubs can't be
changed. Inside `gateway/`:

- Keep `GatewayFault` checked and declare it on adapter methods.
- Convert it to the domain's `PaymentFailure` result in `GatewayAdapter`, never deeper in the domain.
- Outside `gateway/`, the shared Java rules apply unchanged.
