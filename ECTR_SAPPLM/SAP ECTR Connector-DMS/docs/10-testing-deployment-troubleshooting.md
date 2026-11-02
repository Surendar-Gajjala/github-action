# 10. Testing locally with mock SAP responses, deployment and troubleshooting

> **Verified.** All 38 tests pass with `mvnw verify`, and the curl smoke test below was run against the packaged jar. The steps for a real SAP system are a checklist (check).

## Mock SAP backend

`MockRfcClient` is active whenever the `jco` profile is off. It imitates exactly the behaviour the design depends on:

| Behaviour | Mock |
|---|---|
| Documents persist only after `BAPI_TRANSACTION_COMMIT` in the same session | ✔ staged until commit, discarded at session end |
| Unknown document type → RETURN `E` | ✔ `mock.sap.document-types` (default DRW, CAD, DES) |
| Duplicate key → RETURN `E` | ✔ |
| Internal numbering, default part `000` / version `00` | ✔ numbers from 10000001 |
| Slow SAP | ✔ `mock.sap.latency` |
| SAP down | ✔ `mock.sap.fail-communication=true` |

Its messages are invented, prefixed with `Mock:`.

## Tests

| Class | Type | Covers |
|---|---|---|
| `DocumentBapiMapperTest` | unit | parameter and table mapping, blank fields omitted, commit `WAIT='X'` |
| `BapiReturnTest` | unit | E/A as errors; empty structures; table parsing |
| `SapDocumentGatewayTest` | unit + mock | call sequence CREATE2→COMMIT; error→ROLLBACK; no commit = nothing saved; SAP down |
| `DocumentServiceTest` | unit + mock | states and history; 422/503 kinds; **timeout then late COMMITTED**; idempotency |
| `FileHandlingTest` | unit | PDF/DXF detection, extension check, SHA-256 vector, path traversal, name cleaning |
| `DocumentApiIntegrationTest` | `@SpringBootTest` + MockMvc | 201/400/404/415/422, Location, correlation id, replay, upload and download, health |

Run them with `.\mvnw.cmd verify`.

## Manual smoke test

```bash
java -jar target/ectr-dms-connector-1.0.0-SNAPSHOT.jar
# then run examples/requests.http, or:
curl -s localhost:8080/actuator/health
curl -s -X POST localhost:8080/api/v1/documents -H 'Content-Type: application/json' \
     -d '{"documentType":"DRW","description":"Brake caliper drawing"}'
curl -s -F "file=@caliper.pdf" localhost:8080/api/v1/documents/DRW/10000001/000/00/originals
```

To simulate failures: `--mock.sap.latency=40s --app.sap.call-timeout=2s` (timeout), or `--mock.sap.fail-communication=true` (SAP down).

## Testing against a real SAP sandbox (checklist)

1. Install JCo (chapter 4) and run `mvnw -Pjco package`.
2. Set the `SAP_*` variables for the **sandbox/QA** system. Never use production for tests.
3. Start with `--spring.profiles.active=jco`, using `-Dloader.path` and `-Djava.library.path`.
4. Check that `/actuator/health` shows `sap: UP`.
5. Create a document with a valid type, then check it in transaction **CV03N**.
6. Create one with an invalid type. Expect 422 with real SAP messages.
7. Compare every `BapiNames` constant with SE37 if anything behaves unexpectedly.

## Deployment

| Item | Recommendation |
|---|---|
| Runtime | Java 17. For JCo, use the matching native library for the OS and CPU. |
| Process | `java -Dloader.path=/opt/sapjco -Djava.library.path=/opt/sapjco -jar app.jar --spring.profiles.active=jco` |
| Container | Copy `sapjco3.jar` and `libsapjco3.so` into the image under their **original names**. Pass secrets as env vars from the secret store. |
| Network | The service needs RFC access to SAP (gateway port `33<sysnr>` for application server logon, check). Callers need HTTPS only. |
| TLS / auth | Put the service behind a reverse proxy or API gateway with TLS. Add authentication (e.g. Spring Security with OAuth2 / mTLS) before exposing it. **This sample has no authentication.** |
| Scaling | More than one instance needs shared storage plus a database-backed transaction monitor (chapters 8 and 9). |

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `No qualifying bean of type RfcClient` with profile `jco` | Built without `-Pjco`, so the JCo classes are missing | `mvnw -Pjco package` |
| `Illegal JCo archive "sapjco3-3.1.10.jar"` | JCo jar renamed or bundled by Maven | Keep it `provided`; load the original `sapjco3.jar` via `-Dloader.path` |
| `UnsatisfiedLinkError … sapjco3` | Native library not found, or the wrong architecture | `-Djava.library.path=<dir>`; use the 64-bit library for a 64-bit JVM |
| Startup fails: `sap.jco.password must not be blank` | Env variable missing | Set `SAP_PASSWORD` (and the others) |
| 503 `sap-unavailable` with `JCo group …` in the log | Network, gateway, or logon failure | Check host, sysnr, client, user lock status and firewall; try `ping` via health |
| 503 with "Too many parallel SAP requests" | Worker pool and queue full | Raise `worker-threads` (≤ peak-limit) or slow down callers |
| "Function … not found or not RFC-enabled" | Missing `S_RFC` authorisation or a wrong name | SAP security: `S_RFC` for the function group; check the name in SE37 |
| 422 with an SAP message about the document type or field | DMS customizing / mandatory fields | Fix the data or the customizing (DC10, field selection) |
| Document visible only with a delay | Commit without wait | We send `WAIT='X'`; make sure nobody removed it |
| 504, then a duplicate document after a retry | The retry was sent without an Idempotency-Key | Always send the key; poll the transaction before retrying |
| 413 on upload | File bigger than the multipart limit | Raise `spring.servlet.multipart.max-file-size` / `max-request-size` |
| 415 for a valid DXF | File starts with a BOM or comments before `0/SECTION` | Check the first bytes; extend `FileTypeDetector` if your CAD tool writes such files |
