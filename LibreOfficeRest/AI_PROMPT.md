# AI prompt for recreating LibreOfficeRest

Recreate the project as a single Maven project under `LibreOfficeRest/` inside a non-Maven repo root called `libreoffice-addons`.

Keep this working state:

- No root `pom.xml`, no parent Maven, no Maven modules.
- Docker files and tools live under `LibreOfficeRest/docker` and `LibreOfficeRest/tools`.
- Root `.m2-docker/` is the Docker Maven cache.
- Java class name: `org.libreoffice.rest.LibreOfficeRestAddIn`.
- Calc/UNO functions are uppercase: `PING`, `HTTPGET`, `HTTPGETREFRESH`, `JSONPATH`, `JSONVALID`, etc.
- Each Calc function has hidden first parameter `com.sun.star.beans.XPropertySet xOptions` in IDL and Java.
- The extension uses `LibreOfficeRest.components` with loader `com.sun.star.loader.Java2`.
- The OXT manifest registers `LibreOfficeRest.components` as `application/vnd.sun.star.uno-components`.
- The shaded JAR manifest contains:
  - `RegistrationClassName: org.libreoffice.rest.LibreOfficeRestAddIn`
  - `UNO-Type-Path: LibreOfficeRest.jar`
- The real OXT build generates `LibreOfficeRest.rdb` and the UNO interface class with LibreOffice SDK `javamaker`.
- Do not create `src/main/java/org/libreoffice/rest/XLibreOfficeRest.java`.
- Keep the checked-in dev mirror at `src-generated/java/org/libreoffice/rest/XLibreOfficeRest.java` for IntelliJ/local unit tests only.
- Maven default profile uses `src-generated/java` and skips UNO generation for quick local tests.
- Docker build uses profile `uno-sdk-build`, runs SDK generation, tests, and packages the OXT.
- Do not add Python generators or extra helper generators.
- Do not fake-create the interface with a hardcoded heredoc.
- The Docker/Maven build may update `src-generated/java/org/libreoffice/rest/XLibreOfficeRest.java` by decompiling the real `javamaker` class with CFR.
- If the IDL changes, update the dev mirror with one Maven phase through Docker:

```bash
./LibreOfficeRest/tools/build-libreoffice-rest-docker.sh generate-sources
```

Useful build commands:

```bash
./LibreOfficeRest/tools/build-libreoffice-rest-docker.sh
./LibreOfficeRest/tools/build-libreoffice-rest-docker.sh clean verify -DskipITs=false
cd LibreOfficeRest && mvn test -DskipITs=true
```

Critical runtime lesson: autocomplete in Calc only proves `AddIn.xcu` is installed. `=PING()` must work. If it fails, debug Java2 component registration, JAR manifest, `LibreOfficeRest.components`, Java runtime, and `XPropertySet xOptions` signatures before touching JSON/HTTP code.
