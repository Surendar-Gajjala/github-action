# 3. When and why Spring Boot is used as the integration layer

> **Verified.** Statements about Spring Boot features refer to what this project uses and tests.

## Why not call SAP directly from the CAD application?

| Problem with a direct JCo call from every CAD workstation | What the middleware solves |
|---|---|
| Every workstation needs the JCo jar, the native library, and SAP network access | Only the server talks RFC; CAD apps use HTTPS |
| SAP credentials would be spread across many clients | One technical RFC user, held in server-side secrets |
| Every client reimplements BAPI and commit logic | Implemented and tested once |
| No central view of failures | Transaction monitor, health endpoint, correlated logs |
| SAP upgrades touch every client | Only the service changes |

## Why Spring Boot specifically

| Need | Spring Boot feature used here |
|---|---|
| REST API with JSON | `spring-boot-starter-web`, Jackson records |
| Input validation | `spring-boot-starter-validation` (`@Valid`, `@Pattern`, `@Size`) |
| Uniform error format | `ProblemDetail` (RFC 9457), `ResponseEntityExceptionHandler` |
| Externalised, type-safe configuration | `@ConfigurationProperties` records, validated at startup |
| Switching mock ↔ real SAP | Spring **profiles** (`@Profile("jco")`, `application-jco.yml`) |
| Secrets from the environment | `${SAP_PASSWORD}` placeholders |
| Bounded concurrency for SAP calls | `ThreadPoolTaskExecutor` bean |
| Health monitoring | Actuator plus our `SapHealthIndicator` |
| Integration tests | `@SpringBootTest` + `MockMvc` |
| Packaging | An executable jar (or the ZIP layout for JCo, see chapter 4) |

## When Spring Boot is *not* the right choice

- **Very high volume or asynchronous processing.** If thousands of documents arrive in bursts, put a queue in front of the service (JMS/Kafka) and let workers consume it. The gateway and service code stay the same.
- **SAP pushing to us.** That needs a JCo *server* or an SAP-side integration (IDoc, SAP Integration Suite). It is out of scope here.
- **The customer already runs an integration platform.** If they mandate SAP Integration Suite or another ESB, the BAPI logic described here moves into that platform.

## Next

[Chapter 4](04-jco-setup.md) covers the project structure and JCo setup.
