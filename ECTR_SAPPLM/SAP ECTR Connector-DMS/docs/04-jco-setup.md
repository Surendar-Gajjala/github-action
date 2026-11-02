# 4. Maven project structure and secure SAP JCo configuration

> **Verified vs illustrative.** The Maven build, the profiles and the Spring configuration are verified. The JCo classes in `src/jco/java` use the public JCo 3 API (`JCoDestinationManager`, `JCoContext`, `DestinationDataProvider`, …) but were **not compiled here**, because no `sapjco3.jar` was available. Compile with `-Pjco` and test against a sandbox system first. The JCo download location, release and licence terms come from SAP (check).

## Structure

```
pom.xml                      Spring Boot parent 3.3.5, Java 17; profile "jco"
mvnw, mvnw.cmd, .mvn/        Maven Wrapper (Maven 3.9.9)
src/main/java/...            always compiled (no JCo dependency)
src/jco/java/...             compiled only with -Pjco
src/main/resources/
  application.yml            defaults + mock SAP
  application-jco.yml        real SAP, values from environment variables
src/test/java/...            JUnit 5, AssertJ, MockMvc
examples/requests.http       sample calls
```

Dependencies: `spring-boot-starter-web`, `-validation`, `-actuator`, `-test`. There is **no JCo dependency in the default build**.

## Getting JCo into the build (profile `jco`)

1. Download SAP JCo 3 for your OS from the SAP Support Portal (an S-user is required). You get `sapjco3.jar` plus `sapjco3.dll` (Windows) or `libsapjco3.so` (Linux).
2. Install the jar into your local Maven repository, or into the company's Nexus/Artifactory:
   ```
   mvnw install:install-file -Dfile=C:/sapjco/sapjco3.jar -DgroupId=com.sap.conn.jco ^
        -DartifactId=sapjco3 -Dversion=3.1.10 -Dpackaging=jar
   ```
   Use the real version number, and set `<sapjco.version>` in `pom.xml` to match.
3. Build: `mvnw -Pjco package`

### Why the dependency is `provided`, and the ZIP layout

JCo checks the name of its own jar and refuses to start if it is renamed. A Maven-managed copy is called `sapjco3-3.1.10.jar`, so the profile does **not** bundle it into the fat jar. Instead, the profile switches the Boot jar to `layout=ZIP` (`PropertiesLauncher`), and you provide the original files at runtime:

```
java -Dloader.path=C:/sapjco -Djava.library.path=C:/sapjco ^
     -jar target/ectr-dms-connector-1.0.0-SNAPSHOT.jar --spring.profiles.active=jco
```

`C:/sapjco` contains the unrenamed `sapjco3.jar` and the native library. Check this behaviour against the JCo release notes for your version.

## Secure destination configuration

JCo can read `<name>.jcoDestination` property files, but those hold the password in clear text on disk. This project instead registers a **`DestinationDataProvider`** (`SpringDestinationDataProvider`), which feeds JCo from Spring configuration. The values come from **environment variables**:

```yaml
# application-jco.yml
sap:
  jco:
    destination-name: ECTR_DMS
    ashost: ${SAP_ASHOST}
    sysnr: ${SAP_SYSNR:00}
    client: ${SAP_CLIENT}
    user: ${SAP_USER}
    password: ${SAP_PASSWORD}
    language: ${SAP_LANG:EN}
    pool-capacity: 3
    peak-limit: 10
```

```powershell
$env:SAP_ASHOST="sapdev01"; $env:SAP_CLIENT="100"; $env:SAP_USER="RFC_ECTR_DMS"; $env:SAP_PASSWORD="…"
```

Rules:

- Never commit real values. In production, inject them from the platform's secret store: Kubernetes Secrets, Vault, Azure Key Vault, or similar.
- `JcoProperties.toString()` masks the password, so a stray `log.info(properties)` cannot leak it.
- `@Validated` + `@NotBlank` make the service **refuse to start** when a variable is missing, instead of failing on the first request.
- JCo allows **one** `DestinationDataProvider` per JVM. `JcoConfiguration` checks `Environment.isDestinationDataProviderRegistered()` before registering.
- Use a **technical (system/communication) user** with only the DMS authorisations it needs. Agree the user type, password policy and authorisation objects with SAP security.
- Prefer SNC (Secure Network Communication) for encrypted RFC in production. That needs additional JCo properties (`jco.client.snc_*`) and SAP-side setup (check).
- Keep `app.sap.worker-threads` ≤ `peak-limit`, so the pool cannot open more connections than JCo allows.

## Next

[Chapter 5](05-rest-api.md) covers the REST API.
