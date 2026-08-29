# Contributing

Thanks for taking an interest. This is a portfolio project, but it is held to the standards of
a real service: the build is green, the tests are real, and the README documents only what
actually works.

## Ground rules

1. **The build must stay green.** `./mvnw -B clean verify` has to pass before anything is
   merged. Do not skip tests to get there.
2. **Document only what exists.** If the README says an endpoint exists, it must be reachable
   in code. If it shows a command, that command must run.
3. **Money is `BigDecimal`.** Never `double`, never `float`, anywhere near a price, a rate or a
   total. Round through `Money.round(...)` so every amount shares one policy.
4. **Every org-scoped endpoint calls `TenantAccessGuard`.** Reading the organization id from the
   path without checking it against the caller's token is a cross-tenant data leak.

## Getting set up

```bash
git clone https://github.com/vivek/subscription-billing-platform.git
cd subscription-billing-platform

# Run the whole suite. Needs nothing external: H2 in memory, Kafka in process.
./mvnw -B clean verify

# Run the service against in-memory H2.
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Requirements: JDK 17. Maven comes from the wrapper (`./mvnw`), so a system Maven is optional.
Docker is only needed for the full `docker compose` stack, not for building or testing.

## Tests

| Kind | Where | Needs |
| --- | --- | --- |
| Unit | `src/test/java/**/service`, `**/security` | nothing |
| Web layer | `src/test/java/**/api` | nothing (`@WebMvcTest` + mocked JWT) |
| Integration | `BillingLifecycleIntegrationTest` | H2 in memory |
| Messaging | `UsageEventKafkaIntegrationTest` | in-process broker via `@EmbeddedKafka` |

No test requires Docker, a real Kafka broker, or a running Keycloak. Keep it that way — a test
suite that only runs on one machine is not a test suite.

New behaviour needs a test that fails without the change. Bug fixes need a test that reproduces
the bug.

## Database changes

The schema is owned by Flyway, not Hibernate (`ddl-auto` is `none` on every profile).

- Add a new versioned migration under `src/main/resources/db/migration/common/`, e.g.
  `V2__add_payment_methods.sql`. Never edit an applied migration.
- Write portable SQL: the same file runs on H2 (dev, tests) and PostgreSQL (prod).
- Update the matching JPA entity in the same commit.

## Style

- Match the surrounding code. Constructor injection, no field injection, no Lombok.
- SLF4J for logging — never `System.out`.
- Throw a typed exception from `com.vivek.platform.subscription.exception` so
  `GlobalExceptionHandler` can map it to the right status. Never a bare `RuntimeException`.
- Controllers return DTOs, not JPA entities.
- Annotate new endpoints with springdoc `@Operation` / `@ApiResponses`.

## Commits and pull requests

Conventional commits: `feat:`, `fix:`, `test:`, `docs:`, `refactor:`, `build:`, `ci:`.

A pull request should say what changed and why, note any migration or configuration change, and
show that `./mvnw -B clean verify` passes.

## Reporting a security issue

Multi-tenant isolation bugs — anything letting one organization read or write another's data —
should be raised privately with the maintainer rather than in a public issue.
